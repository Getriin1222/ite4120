package ee.taltech.ite4120.pantry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The HTTP contract of PANTRY.01 § API: what the service-level business tests cannot
 * see. 201 + Location on create, ids as JSON strings, every error as
 * application/problem+json with field errors or existingProductId where the
 * specification asks for them, and 409 for a stale stock version.
 *
 * <p>Plain JDK HttpClient against the real server on a random port, so the security
 * chain, the JSON binding and the error handlers all take part.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PantryApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Autowired
    Environment env;

    @Test
    void createAnswers201WithLocationAndStringIds() throws Exception {
        HttpResponse<String> r = post("/api/pantry/products", """
                {"categoryCode":"DAIRY_EGGS","foodType":"Milk","name":"Whole milk","brand":"Example Dairy A",
                 "packSize":1,"unit":"l","barcode":null,"isFrozen":false,"initialQuantity":2}""");

        assertThat(r.statusCode()).isEqualTo(201);
        Map<String, Object> body = json(r);
        assertThat(body.get("id")).isInstanceOf(String.class);
        assertThat(r.headers().firstValue("Location")).hasValue("/api/pantry/products/" + body.get("id"));
        assertThat(body.get("foodType")).isEqualTo("milk");
        // The interface reads `isFrozen`; pin the property name (a boolean record component
        // must not be renamed to `frozen` by the JSON mapping).
        assertThat(body).containsEntry("isFrozen", false).doesNotContainKey("frozen");
        assertThat(body.get("categoryName")).isEqualTo("Dairy & Eggs");
        @SuppressWarnings("unchecked")
        Map<String, Object> stock = (Map<String, Object>) body.get("stock");
        assertThat(stock.get("id")).isInstanceOf(String.class);
        assertThat(((Number) stock.get("quantity")).intValue()).isEqualTo(2);
        assertThat(body.get("productVersion")).isInstanceOf(Number.class);
        assertThat(stock.get("stockVersion")).isInstanceOf(Number.class);

        HttpResponse<String> report = get("/api/pantry/products?foodType=MILK");
        assertThat(report.statusCode()).isEqualTo(200);
        assertThat(report.body()).contains("\"Example Dairy A\"");
    }

    @Test
    void fractionalQuantityIsAProblemWithFieldErrors() throws Exception {
        HttpResponse<String> r = post("/api/pantry/products", """
                {"categoryCode":"PASTA_RICE_GRAINS","foodType":"pasta","name":"Spaghetti",
                 "packSize":500,"unit":"g","initialQuantity":1.5}""");

        assertThat(r.statusCode()).isEqualTo(400);
        assertProblem(r, 400);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) json(r).get("errors");
        assertThat(errors).extracting(e -> e.get("field")).contains("initialQuantity");
    }

    @Test
    void malformedJsonAndUnknownCategoryAre400Problems() throws Exception {
        HttpResponse<String> broken = post("/api/pantry/products", "{\"name\":");
        assertProblem(broken, 400);

        HttpResponse<String> unknown = post("/api/pantry/products", """
                {"categoryCode":"NOPE","foodType":"x","name":"x","unit":"each","initialQuantity":0}""");
        assertProblem(unknown, 400);
        assertThat(String.valueOf(json(unknown).get("detail"))).contains("category");
    }

    @Test
    void duplicateBarcodeIs409WithExistingProductId() throws Exception {
        String body = """
                {"categoryCode":"SNACKS_DRINKS","foodType":"juice","name":"Apple juice %s",
                 "packSize":1,"unit":"l","barcode":"0400000000017","initialQuantity":1}""";
        HttpResponse<String> first = post("/api/pantry/products", body.formatted("A"));
        assertThat(first.statusCode()).isEqualTo(201);

        HttpResponse<String> second = post("/api/pantry/products", body.formatted("B"));
        assertProblem(second, 409);
        assertThat(json(second).get("existingProductId")).isEqualTo(json(first).get("id"));
    }

    @Test
    void staleStockVersionIs409AndRefreshAllowsTheRetry() throws Exception {
        Map<String, Object> created = json(post("/api/pantry/products", """
                {"categoryCode":"BREAD_BAKERY","foodType":"bread","name":"Rye bread",
                 "unit":"each","initialQuantity":1}"""));
        String id = (String) created.get("id");
        @SuppressWarnings("unchecked")
        long v = ((Number) ((Map<String, Object>) created.get("stock")).get("stockVersion")).longValue();

        HttpResponse<String> added = post("/api/pantry/products/" + id + "/stock-additions",
                "{\"quantity\":1,\"expectedStockVersion\":" + v + "}");
        assertThat(added.statusCode()).isEqualTo(200);

        HttpResponse<String> resent = post("/api/pantry/products/" + id + "/stock-additions",
                "{\"quantity\":1,\"expectedStockVersion\":" + v + "}");
        assertProblem(resent, 409);

        HttpResponse<String> corrected = send(HttpRequest.newBuilder(uri("/api/pantry/products/" + id + "/stock"))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        "{\"quantity\":0,\"expectedStockVersion\":" + v + "}")));
        assertProblem(corrected, 409);
    }

    @Test
    void notFoundBadIdAndBadQueryAreProblems() throws Exception {
        assertProblem(get("/api/pantry/products/999999999"), 404);
        assertProblem(get("/api/pantry/products/abc"), 400);
        assertProblem(get("/api/pantry/products?limit=101"), 400);
        assertProblem(get("/api/pantry/products?frozen=maybe"), 400);
    }

    @Test
    void emptySearchIs200WithNoData() throws Exception {
        HttpResponse<String> r = get("/api/pantry/products?search=" + "zz-no-such-grocery-zz");
        assertThat(r.statusCode()).isEqualTo(200);
        Map<String, Object> body = json(r);
        assertThat((List<?>) body.get("data")).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) body.get("meta");
        assertThat(((Number) meta.get("total")).intValue()).isZero();
    }

    @Test
    void categoriesAreSeededAndTheApiNeedsAToken() throws Exception {
        HttpResponse<String> categories = get("/api/pantry/categories");
        assertThat(categories.statusCode()).isEqualTo(200);
        assertThat(JsonParserFactory.getJsonParser().parseList(categories.body())).hasSize(10);

        HttpResponse<String> anonymous = HTTP.send(HttpRequest.newBuilder(uri("/api/pantry/products")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    // --- helpers ---------------------------------------------------------------

    private URI uri(String path) {
        return URI.create("http://localhost:" + env.getProperty("local.server.port") + path);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> send(HttpRequest.Builder b) throws IOException, InterruptedException {
        return HTTP.send(b.header("Authorization", "Bearer pantry-test")
                .header("Content-Type", "application/json")
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> json(HttpResponse<String> r) {
        return JsonParserFactory.getJsonParser().parseMap(r.body());
    }

    private static void assertProblem(HttpResponse<String> r, int status) {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(status);
        assertThat(r.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        Map<String, Object> problem = json(r);
        assertThat(((Number) problem.get("status")).intValue()).isEqualTo(status);
        assertThat(problem).containsKeys("title", "detail");
        assertThat(String.valueOf(problem.get("detail"))).doesNotContainIgnoringCase("sql")
                .doesNotContain("Exception");
    }
}
