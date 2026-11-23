package ee.taltech.ite4120.pantry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.taltech.ite4120.pantry.dto.ProductQueryParams;
import ee.taltech.ite4120.pantry.model.ProductDraft;
import ee.taltech.ite4120.pantry.model.ProductPatch;
import ee.taltech.ite4120.pantry.model.ProductView;
import ee.taltech.ite4120.pantry.service.DuplicateProductException;
import ee.taltech.ite4120.pantry.service.PantryService;
import ee.taltech.ite4120.pantry.service.StaleVersionException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.helex.commons.exception.ApiClientException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * THE PANTRY BUSINESS TESTS: the executable form of
 * docs/src/specifications/PANTRY.01-household-inventory.md § Business tests,
 * BT-01 to BT-12 (Stage A). Written before the implementation; every test names
 * the BT it quotes.
 *
 * <p>Runs against a real PostgreSQL via Testcontainers: the whole changelog
 * (core + animals + pantry) is applied to an empty database first, so these tests
 * also prove the pantry migrations run clean from empty. The container is shared
 * by the class, so each test works in its own food type (e.g. {@code bt02 milk})
 * and never depends on rows written by another test.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PantryBusinessRulesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    PantryService service;

    @Autowired
    JdbcTemplate jdbc;

    /** BT-01 · Create product and initial stock together [AC-02, AC-10]. */
    @Test
    void bt01_productAndInitialStockAreCreatedTogether() {
        // Given seeded category DAIRY_EGGS and no cream product
        assertThat(search(foodType("bt01 cream"))).isEmpty();

        // When the user creates Cream, food type cream, 200 ml, initialQuantity 1
        ProductView created = service.create(draft("DAIRY_EGGS", "bt01 cream", "Cream", null, "200", "ml", 1));

        // Then one Product and one Stock exist, and the joined report shows quantity 1
        assertThat(created.id()).isNotBlank();
        assertThat(created.stock().quantity()).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(1) from pantry.stock where product_id = ?", Integer.class, Long.valueOf(created.id())))
                .isEqualTo(1);
        List<ProductView> report = search(foodType("bt01 cream"));
        assertThat(report).singleElement().satisfies(p -> {
            assertThat(p.categoryName()).isEqualTo("Dairy & Eggs");
            assertThat(p.name()).isEqualTo("Cream");
            assertThat(p.stock().quantity()).isEqualTo(1L);
        });

        // A failed create leaves no partial record: the invalid request is refused and
        // every product still has exactly one companion stock row.
        assertThatThrownBy(() -> service.create(draft("DAIRY_EGGS", "bt01 cream", "Cream", "Brand X", "200", "ml", -1)))
                .isInstanceOf(ApiClientException.class);
        assertThat(search(foodType("bt01 cream"))).hasSize(1);
        assertCompanionStockInvariant();
    }

    /** BT-02 · Report related products across brands [AC-01, AC-06]. */
    @Test
    void bt02_relatedProductsAcrossBrandsAreReportedSeparately() {
        // Given Brand A milk 1 l has quantity 2 and Brand B milk 1 l has quantity 1, both food type milk
        service.create(draft("DAIRY_EGGS", "bt02 milk", "Whole milk bt02", "Brand A", "1", "l", 2));
        service.create(draft("DAIRY_EGGS", "bt02 milk", "Whole milk bt02", "Brand B", "1", "l", 1));

        // When the user searches MILK (case-insensitive, padded)
        ProductQueryParams byText = new ProductQueryParams();
        byText.setSearch("  BT02 MILK ");
        List<ProductView> found = search(byText);

        // Then both products appear with their own brands and quantities
        assertThat(found).extracting(ProductView::brand, p -> p.stock().quantity())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Brand A", 2L),
                        org.assertj.core.groups.Tuple.tuple("Brand B", 1L));

        // And the exact foodType filter returns the same group, nothing merged or modified
        assertThat(search(foodType("bt02 milk"))).extracting(ProductView::id)
                .containsExactlyElementsOf(found.stream().map(ProductView::id).toList());
        assertThat(search(foodType("bt02 milk"))).extracting(p -> p.stock().quantity()).containsExactly(2L, 1L);
    }

    /** BT-03 · Add stock to the correct product [AC-04, AC-06]. */
    @Test
    void bt03_addingStockChangesOnlyTheChosenProduct() {
        // Given A milk has quantity 2 and B milk has quantity 1
        ProductView a = service.create(draft("DAIRY_EGGS", "bt03 milk", "Whole milk bt03", "Brand A", "1", "l", 2));
        ProductView b = service.create(draft("DAIRY_EGGS", "bt03 milk", "Whole milk bt03", "Brand B", "1", "l", 1));

        // When 3 are added to B with its current stock version
        ProductView after = service.addStock(Long.valueOf(b.id()), new BigDecimal("3"), b.stock().stockVersion());

        // Then A remains 2, B becomes 4 and B's stock version changes; the values survive reloading
        assertThat(after.stock().quantity()).isEqualTo(4L);
        assertThat(after.stock().stockVersion()).isNotEqualTo(b.stock().stockVersion());
        assertThat(service.require(Long.valueOf(b.id())).stock().quantity()).isEqualTo(4L);
        assertThat(service.require(Long.valueOf(a.id())).stock().quantity()).isEqualTo(2L);
    }

    /** BT-04 · Retain zero-stock products [AC-05]. */
    @Test
    void bt04_zeroStockProductsAreRetained() {
        // Given one carton of milk
        ProductView milk = service.create(draft("DAIRY_EGGS", "bt04 milk", "Whole milk bt04", "Brand A", "1", "l", 1));

        // When Use one succeeds (a correction to the displayed count minus one)
        ProductView used = service.correctStock(Long.valueOf(milk.id()), BigDecimal.ZERO, milk.stock().stockVersion());

        // Then quantity is zero and default search still returns the product
        assertThat(used.stock().quantity()).isZero();
        assertThat(search(foodType("bt04 milk"))).singleElement()
                .satisfies(p -> assertThat(p.stock().quantity()).isZero());

        // And inStockOnly=true excludes it
        ProductQueryParams inStock = foodType("bt04 milk");
        inStock.setInStockOnly(true);
        assertThat(search(inStock)).isEmpty();

        // And Use one at zero is impossible: the count never becomes negative
        assertThatThrownBy(() -> service.correctStock(Long.valueOf(milk.id()), new BigDecimal("-1"),
                used.stock().stockVersion()))
                .isInstanceOf(ApiClientException.class);

        // And adding one carton later restores its count to 1
        ProductView restocked = service.addStock(Long.valueOf(milk.id()), BigDecimal.ONE, used.stock().stockVersion());
        assertThat(restocked.stock().quantity()).isEqualTo(1L);
    }

    /** BT-05 · Reject invalid quantities [AC-03, AC-05]. */
    @Test
    void bt05_invalidQuantitiesAreRejectedAndStockIsUnchanged() {
        // Given a product with quantity 2
        ProductView p = service.create(draft("PASTA_RICE_GRAINS", "bt05 spaghetti", "Spaghetti", null, "500", "g", 2));
        long id = Long.valueOf(p.id());
        Long v = p.stock().stockVersion();

        // When the caller adds zero, adds a fraction, sets a negative count, or exceeds 1,000,000
        assertThatThrownBy(() -> service.addStock(id, BigDecimal.ZERO, v)).isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> service.addStock(id, new BigDecimal("1.5"), v)).isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> service.correctStock(id, new BigDecimal("-1"), v)).isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> service.addStock(id, new BigDecimal("999999"), v))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("1000000");
        assertThatThrownBy(() -> service.correctStock(id, new BigDecimal("1000001"), v))
                .isInstanceOf(ApiClientException.class);

        // Then each is refused and the quantity remains 2
        assertThat(service.require(id).stock().quantity()).isEqualTo(2L);

        // And the database bounds hold even if the service were bypassed
        assertThatThrownBy(() -> jdbc.update("update pantry.stock set quantity = -1 where product_id = ?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update pantry.stock set quantity = 1000001 where product_id = ?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** BT-06 · Prevent duplicate catalogue entries [AC-07]. */
    @Test
    void bt06_duplicateCatalogueEntriesAreRejected() {
        int stockRowsBefore = count("pantry.stock");

        // Given a saved product with barcode 4006381333931
        ProductDraft first = draft("DAIRY_EGGS", "bt06 milk", "Whole milk", "Brand A", "1", "l", 1);
        first.setBarcode("4006381333931");
        ProductView saved = service.create(first);

        // When another product uses that same string, then 409 with the existing product id
        ProductDraft sameBarcode = draft("DAIRY_EGGS", "bt06 milk", "Other milk", "Brand Z", "1", "l", 1);
        sameBarcode.setBarcode("4006381333931");
        assertThatThrownBy(() -> service.create(sameBarcode))
                .isInstanceOfSatisfying(DuplicateProductException.class,
                        e -> assertThat(e.getExistingProductId()).isEqualTo(saved.id()));

        // And a normalised identity duplicate between two barcode-free entries is 409 too
        ProductView plain = service.create(draft("DAIRY_EGGS", "bt06 cream", "Sour cream", "Brand A", "200", "ml", 1));
        ProductDraft samePlain = draft("DAIRY_EGGS", "bt06 cream", "  SOUR   cream ", " brand a ", "200.0", "ml", 3);
        assertThatThrownBy(() -> service.create(samePlain))
                .isInstanceOfSatisfying(DuplicateProductException.class,
                        e -> assertThat(e.getExistingProductId()).isEqualTo(plain.id()));

        // And distinct barcodes may be saved despite matching descriptions
        ProductDraft otherBarcode = draft("DAIRY_EGGS", "bt06 milk", "Whole milk", "Brand A", "1", "l", 1);
        otherBarcode.setBarcode("96385074");
        assertThat(service.create(otherBarcode).id()).isNotEqualTo(saved.id());

        // A rejected create leaves no additional stock record
        assertThat(count("pantry.stock")).isEqualTo(stockRowsBefore + 3);
        assertCompanionStockInvariant();
    }

    /** BT-07 · Preserve leading barcode zeroes [AC-07]. */
    @Test
    void bt07_barcodeKeepsItsLeadingZeroes() {
        // Given barcode 0123456789012, when saved and retrieved, then it is exactly that string
        ProductDraft d = draft("SNACKS_DRINKS", "bt07 juice", "Apple juice", "Brand A", "1", "l", 1);
        d.setBarcode(" 0123456789012 ");
        ProductView saved = service.create(d);
        assertThat(service.require(Long.valueOf(saved.id())).barcode()).isEqualTo("0123456789012");

        // And a barcode with letters is rejected
        ProductDraft letters = draft("SNACKS_DRINKS", "bt07 juice", "Orange juice", "Brand A", "1", "l", 1);
        letters.setBarcode("01234ABC");
        assertThatThrownBy(() -> service.create(letters))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("barcode");
    }

    /** BT-08 · Combine category and Frozen filtering [AC-08]. */
    @Test
    void bt08_categoryAndFrozenFiltersCombine() {
        // Given frozen peas and fresh peas under Fruit & Vegetables, frozen salmon under Fish & Seafood
        ProductDraft frozenPeas = draft("FRUIT_VEGETABLES", "bt08 peas", "Garden peas", "Brand A", "400", "g", 1);
        frozenPeas.setIsFrozen(true);
        service.create(frozenPeas);
        service.create(draft("FRUIT_VEGETABLES", "bt08 peas", "Garden peas", "Brand A", "400", "g", 1));
        ProductDraft salmon = draft("FISH_SEAFOOD", "bt08 salmon", "Salmon fillet", "Brand A", "250", "g", 1);
        salmon.setIsFrozen(true);
        service.create(salmon);

        // When category FRUIT_VEGETABLES and frozen=true are selected
        ProductQueryParams q = new ProductQueryParams();
        q.setSearch("bt08");
        q.setCategoryCode("FRUIT_VEGETABLES");
        q.setFrozen(true);

        // Then only frozen peas appear
        assertThat(search(q)).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("Garden peas");
            assertThat(p.isFrozen()).isTrue();
        });
    }

    /** BT-09 · Normalise food types [AC-06, AC-08]. */
    @Test
    void bt09_foodTypesAreNormalised() {
        // Given an existing product with food type "bt09 milk"
        service.create(draft("DAIRY_EGGS", "bt09 milk", "Whole milk bt09", "Brand A", "1", "l", 1));

        // When a second brand is saved using space-padded mixed case
        ProductView second = service.create(draft("DAIRY_EGGS", "  BT09   MiLk ", "Whole milk bt09", "Brand B", "1", "l", 1));

        // Then both share the stored food type and the distinct list contains it once
        assertThat(second.foodType()).isEqualTo("bt09 milk");
        assertThat(service.foodTypes()).filteredOn(t -> t.startsWith("bt09")).containsExactly("bt09 milk");
    }

    /** BT-10 · Prevent lost or duplicate stock changes [AC-04, AC-09]. */
    @Test
    void bt10_onlyOneUpdatePerStockVersionSucceeds() throws Exception {
        // Given quantity 2 at version V
        ProductView p = service.create(draft("DAIRY_EGGS", "bt10 eggs", "Eggs", "Brand A", "10", "pcs", 2));
        long id = Long.valueOf(p.id());
        Long v = p.stock().stockVersion();

        // When two stock updates both use V, at the same moment
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ProductView>> updates = List.of(
                () -> { start.await(); return service.addStock(id, BigDecimal.ONE, v); },
                () -> { start.await(); return service.correctStock(id, BigDecimal.ONE, v); });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<ProductView>> results = new ArrayList<>();
        try {
            for (Callable<ProductView> u : updates) {
                results.add(pool.submit(u));
            }
            start.countDown();
            int succeeded = 0;
            int conflicted = 0;
            for (Future<ProductView> f : results) {
                try {
                    f.get();
                    succeeded++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(StaleVersionException.class);
                    conflicted++;
                }
            }
            // Then exactly one mutation succeeds and the other receives a conflict
            assertThat(succeeded).isEqualTo(1);
            assertThat(conflicted).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // The final quantity reflects one successful change: 3 (added one) or 1 (corrected to one)
        ProductView now = service.require(id);
        assertThat(now.stock().quantity()).isIn(1L, 3L);

        // Resending a successful addition with its original version also conflicts
        Long current = now.stock().stockVersion();
        service.addStock(id, BigDecimal.ONE, current);
        assertThatThrownBy(() -> service.addStock(id, BigDecimal.ONE, current))
                .isInstanceOf(StaleVersionException.class);
        assertThat(service.require(id).stock().quantity()).isEqualTo(now.stock().quantity() + 1);
    }

    /** BT-11 · Validate pack-size pairs [AC-03]. */
    @Test
    void bt11_packSizeAndUnitMustPair() {
        // Given new product requests with invalid unit/size pairs, then create is refused
        assertThatThrownBy(() -> service.create(draft("PASTA_RICE_GRAINS", "bt11 pasta", "Penne", null, null, "g", 1)))
                .isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> service.create(draft("PASTA_RICE_GRAINS", "bt11 pasta", "Penne", null, "-500", "g", 1)))
                .isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> service.create(draft("DAIRY_EGGS", "bt11 eggs", "Eggs", null, "6.5", "pcs", 1)))
                .isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> service.create(draft("FRUIT_VEGETABLES", "bt11 apples", "Apples", null, "1", "each", 4)))
                .isInstanceOf(ApiClientException.class);

        // And a valid 500 g request and an `each` request with no size both succeed
        ProductView pasta = service.create(draft("PASTA_RICE_GRAINS", "bt11 pasta", "Penne", null, "500", "g", 1));
        ProductView apples = service.create(draft("FRUIT_VEGETABLES", "bt11 apples", "Apples", null, null, "each", 4));
        assertThat(pasta.packSize()).isEqualByComparingTo("500");
        assertThat(apples.packSize()).isNull();
        assertThat(apples.stock().quantity()).isEqualTo(4L);
    }

    /** BT-12 · Reject stale product edits [AC-09]. */
    @Test
    void bt12_staleProductEditsAreRejected() {
        // Given two users read the same product version
        ProductView read = service.create(draft("DAIRY_EGGS", "bt12 butter", "Butter", "Brand A", "200", "g", 3));
        long id = Long.valueOf(read.id());

        // When the first changes its brand
        ProductView first = service.update(id, new ProductPatch(read.productVersion()).brand("Brand B"));
        assertThat(first.brand()).isEqualTo("Brand B");

        // And the second submits an older version, then the second receives a conflict
        assertThatThrownBy(() -> service.update(id, new ProductPatch(read.productVersion()).brand("Brand C")))
                .isInstanceOf(StaleVersionException.class);

        // The first change remains, and product edits leave the stock quantity unchanged
        ProductView now = service.require(id);
        assertThat(now.brand()).isEqualTo("Brand B");
        assertThat(now.stock().quantity()).isEqualTo(3L);
        assertThat(now.stock().stockVersion()).isEqualTo(read.stock().stockVersion());
    }

    // --- helpers ---------------------------------------------------------------

    private List<ProductView> search(ProductQueryParams q) {
        return service.search(q).getData();
    }

    private static ProductQueryParams foodType(String foodType) {
        ProductQueryParams q = new ProductQueryParams();
        q.setFoodType(foodType);
        return q;
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(1) from " + table, Integer.class);
    }

    /** Every product has exactly one stock row and no stock row is orphaned. */
    private void assertCompanionStockInvariant() {
        assertThat(jdbc.queryForObject("""
                select count(1) from pantry.product p
                 where (select count(1) from pantry.stock s where s.product_id = p.id) <> 1""", Integer.class))
                .isZero();
    }

    static ProductDraft draft(String category, String foodType, String name, String brand,
            String packSize, String unit, int initialQuantity) {
        ProductDraft d = new ProductDraft();
        d.setCategoryCode(category);
        d.setFoodType(foodType);
        d.setName(name);
        d.setBrand(brand);
        d.setPackSize(packSize == null ? null : new BigDecimal(packSize));
        d.setUnit(unit);
        d.setInitialQuantity(BigDecimal.valueOf(initialQuantity));
        return d;
    }
}
