package ee.taltech.ite4120.pantry.dto;

import org.helex.commons.model.QueryParams;

/**
 * Query parameters for the inventory report, {@code GET /api/pantry/products}.
 * Extends the platform {@link QueryParams} ({@code limit}, {@code offset}); the
 * ordering is fixed (food type, name, brand, id) so pagination is stable.
 */
public class ProductQueryParams extends QueryParams {

    /** Case-insensitive literal substring across product name, brand and food type. */
    private String search;
    /** Exact seeded category code, e.g. {@code DAIRY_EGGS}. */
    private String categoryCode;
    /** Exact food type after normalisation: {@code Milk} and {@code milk} are the same. */
    private String foodType;
    /** true: only products bought frozen; false: only the others; absent: both. */
    private Boolean frozen;
    /** true excludes products whose quantity is zero. Default false. */
    private Boolean inStockOnly;

    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getFoodType() { return foodType; }
    public void setFoodType(String foodType) { this.foodType = foodType; }
    public Boolean getFrozen() { return frozen; }
    public void setFrozen(Boolean frozen) { this.frozen = frozen; }
    public Boolean getInStockOnly() { return inStockOnly; }
    public void setInStockOnly(Boolean inStockOnly) { this.inStockOnly = inStockOnly; }
}
