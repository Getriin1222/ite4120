package ee.taltech.ite4120.pantry.api;

import ee.taltech.ite4120.pantry.dto.ProductQueryParams;
import ee.taltech.ite4120.pantry.model.Category;
import ee.taltech.ite4120.pantry.model.ProductView;
import ee.taltech.ite4120.pantry.service.PantryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.helex.commons.model.QueryResult;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The household inventory REST API, PANTRY.01 § API. HTTP only: every rule is in
 * PantryService, every error is a problem document (PantryErrorHandler). The product
 * list is the reporting operation: it joins category, product and stock.
 */
@RestController
@RequestMapping("/api/pantry")
@Tag(name = "Pantry", description = "Household grocery inventory: what we already have, and how much")
public class PantryController {

    private final PantryService service;

    public PantryController(PantryService service) {
        this.service = service;
    }

    @Operation(summary = "The ten seeded category options")
    @GetMapping("/categories")
    public List<Category> categories() {
        return service.categories();
    }

    @Operation(summary = "Distinct food types of saved products, sorted",
            description = "The choices for the food-type picker. Values are stored normalised: trimmed, "
                    + "whitespace collapsed, lowercase.")
    @GetMapping("/food-types")
    public List<String> foodTypes() {
        return service.foodTypes();
    }

    @Operation(summary = "Inventory report: search and filter products with their category and stock",
            description = "Joins category, product and stock. `search` is a case-insensitive literal substring "
                    + "across name, brand and food type (OR); every other filter is AND-ed. Zero-stock products "
                    + "are included unless `inStockOnly=true`. Fixed order: food type, name, brand, id. "
                    + "`limit` 1-100 (default 20), `offset` 0 or more.")
    @ApiResponse(responseCode = "200", description = "A page: `data` plus `meta.total` (matches before paging)")
    @ApiResponse(responseCode = "400", description = "Invalid query parameter")
    @GetMapping("/products")
    public QueryResult<ProductView> search(@ParameterObject @ModelAttribute ProductQueryParams params,
            @Parameter(hidden = true) @RequestParam(name = "limit", required = false) Integer limit,
            @Parameter(hidden = true) @RequestParam(name = "offset", required = false) Integer offset) {
        /* limit/offset are bound into params as well; they are passed again so the service checks
           the range only when the caller actually sent them, never the platform's own default. */
        return service.search(params, limit, offset);
    }

    @Operation(summary = "One product with its category and stock")
    @ApiResponse(responseCode = "200", description = "The product")
    @ApiResponse(responseCode = "404", description = "No active product with that id")
    @GetMapping("/products/{id}")
    public ProductView byId(@PathVariable long id) {
        return service.require(id);
    }

    @Operation(summary = "Create a product and its initial stock in one transaction",
            description = "Food type is normalised before saving. initialQuantity may be 0: a saved catalogue "
                    + "entry ready for the next purchase.")
    @ApiResponse(responseCode = "201", description = "Created; Location points at the new product")
    @ApiResponse(responseCode = "400", description = "Invalid fields; `errors` lists each field and its message")
    @ApiResponse(responseCode = "409", description = "Duplicate barcode or barcode-free identity; "
            + "`existingProductId` names the saved product")
    @PostMapping("/products")
    public ResponseEntity<ProductView> create(@Valid @RequestBody ProductCreateRequest body) {
        ProductView saved = service.create(body.toDraft());
        return ResponseEntity.created(URI.create("/api/pantry/products/" + saved.id())).body(saved);
    }

    @Operation(summary = "Edit catalogue fields (never the stock count)",
            description = "PATCH semantics: absent fields keep their value, explicit null clears brand or "
                    + "barcode. expectedProductVersion must equal the current productVersion.")
    @ApiResponse(responseCode = "200", description = "The updated product")
    @ApiResponse(responseCode = "400", description = "Invalid fields")
    @ApiResponse(responseCode = "404", description = "No active product with that id")
    @ApiResponse(responseCode = "409", description = "Stale productVersion, or the edit would create a duplicate")
    @PatchMapping("/products/{id}")
    public ProductView update(@PathVariable long id, @Valid @RequestBody ProductPatchRequest body) {
        return service.update(id, body.toPatch());
    }

    @Operation(summary = "Add a purchase to the stored quantity",
            description = "An increment of 1 to 1000000 whole packs or items; the result must stay at most "
                    + "1000000. Applied only if expectedStockVersion is still current, so a resent request "
                    + "is refused rather than counted twice.")
    @ApiResponse(responseCode = "200", description = "The product with its new stock")
    @ApiResponse(responseCode = "400", description = "Quantity not a whole number in range")
    @ApiResponse(responseCode = "404", description = "No active product with that id")
    @ApiResponse(responseCode = "409", description = "Stale stockVersion: refresh before saving again")
    @PostMapping("/products/{id}/stock-additions")
    public ProductView addStock(@PathVariable long id, @Valid @RequestBody StockChangeRequest body) {
        return service.addStock(id, body.getQuantity(), body.getExpectedStockVersion());
    }

    @Operation(summary = "Set the confirmed remaining count",
            description = "An absolute count, 0 to 1000000. Use one is this operation with the displayed "
                    + "count minus one and the displayed stockVersion.")
    @ApiResponse(responseCode = "200", description = "The product with its corrected stock")
    @ApiResponse(responseCode = "400", description = "Quantity not a whole number in range")
    @ApiResponse(responseCode = "404", description = "No active product with that id")
    @ApiResponse(responseCode = "409", description = "Stale stockVersion: refresh before saving again")
    @PatchMapping("/products/{id}/stock")
    public ProductView correctStock(@PathVariable long id, @Valid @RequestBody StockChangeRequest body) {
        return service.correctStock(id, body.getQuantity(), body.getExpectedStockVersion());
    }
}
