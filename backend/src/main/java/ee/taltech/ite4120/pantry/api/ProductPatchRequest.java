package ee.taltech.ite4120.pantry.api;

import ee.taltech.ite4120.pantry.model.ProductPatch;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * {@code PATCH /api/pantry/products/{id}}: edit catalogue fields. A setter runs only for
 * a property that is present in the JSON, so the setters record presence: absent keeps
 * the stored value, explicit null clears brand or barcode (and is refused for a required
 * field). Stock is not editable here.
 */
@Schema(name = "ProductPatchRequest")
public class ProductPatchRequest {

    @Schema(description = "The productVersion the user last saw", example = "3",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long expectedProductVersion;

    private final ProductPatch shadow = new ProductPatch(null);
    private String categoryCode;
    private String foodType;
    private String name;
    @Schema(nullable = true)
    private String brand;
    @Schema(nullable = true)
    private BigDecimal packSize;
    private String unit;
    @Schema(nullable = true)
    private String barcode;
    private Boolean isFrozen;

    public ProductPatch toPatch() {
        ProductPatch p = new ProductPatch(expectedProductVersion);
        if (shadow.has(ProductPatch.CATEGORY_CODE)) p.categoryCode(categoryCode);
        if (shadow.has(ProductPatch.FOOD_TYPE)) p.foodType(foodType);
        if (shadow.has(ProductPatch.NAME)) p.name(name);
        if (shadow.has(ProductPatch.BRAND)) p.brand(brand);
        if (shadow.has(ProductPatch.PACK_SIZE)) p.packSize(packSize);
        if (shadow.has(ProductPatch.UNIT)) p.unit(unit);
        if (shadow.has(ProductPatch.BARCODE)) p.barcode(barcode);
        if (shadow.has(ProductPatch.IS_FROZEN)) p.isFrozen(isFrozen);
        return p;
    }

    public Long getExpectedProductVersion() { return expectedProductVersion; }
    public void setExpectedProductVersion(Long v) { this.expectedProductVersion = v; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String v) { categoryCode = v; shadow.categoryCode(v); }
    public String getFoodType() { return foodType; }
    public void setFoodType(String v) { foodType = v; shadow.foodType(v); }
    public String getName() { return name; }
    public void setName(String v) { name = v; shadow.name(v); }
    public String getBrand() { return brand; }
    public void setBrand(String v) { brand = v; shadow.brand(v); }
    public BigDecimal getPackSize() { return packSize; }
    public void setPackSize(BigDecimal v) { packSize = v; shadow.packSize(v); }
    public String getUnit() { return unit; }
    public void setUnit(String v) { unit = v; shadow.unit(v); }
    public String getBarcode() { return barcode; }
    public void setBarcode(String v) { barcode = v; shadow.barcode(v); }
    public Boolean getIsFrozen() { return isFrozen; }
    public void setIsFrozen(Boolean v) { isFrozen = v; shadow.isFrozen(v); }
}
