package ee.taltech.ite4120.pantry.api;

import ee.taltech.ite4120.pantry.model.ProductDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code POST /api/pantry/products}: a new product and its initial stock count, in one
 * request. Bean Validation checks only the message's outer shape (lengths); every
 * state-dependent and cross-field rule lives in PantryService, which answers with
 * field-specific 400s. Numbers are BigDecimal so 1.5 is refused instead of truncated.
 */
@Schema(name = "ProductCreateRequest")
public class ProductCreateRequest {

    @Schema(description = "Seeded category code", example = "DAIRY_EGGS", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 40)
    private String categoryCode;

    @Schema(description = "Food type; trimmed, whitespace collapsed and lowercased before saving",
            example = "Milk", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 200)
    private String foodType;

    @Schema(example = "Whole milk", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 400)
    private String name;

    @Schema(description = "Optional; blank becomes null", example = "Example Dairy A", nullable = true)
    @Size(max = 200)
    private String brand;

    @Schema(description = "Amount in one counted pack: positive for g, kg, ml, l, pcs (whole for pcs), "
            + "null for each", example = "1", nullable = true)
    private BigDecimal packSize;

    @Schema(allowableValues = {"g", "kg", "ml", "l", "pcs", "each"}, example = "l",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 10)
    private String unit;

    @Schema(description = "Optional, 8 to 14 digits, a string so leading zeroes survive",
            example = "0123456789012", nullable = true)
    @Size(max = 40)
    private String barcode;

    @Schema(description = "Bought frozen; default false", example = "false")
    private Boolean isFrozen;

    @Schema(description = "Whole packs or items now at home, 0 to 1000000", example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal initialQuantity;

    public ProductDraft toDraft() {
        ProductDraft d = new ProductDraft();
        d.setCategoryCode(categoryCode);
        d.setFoodType(foodType);
        d.setName(name);
        d.setBrand(brand);
        d.setPackSize(packSize);
        d.setUnit(unit);
        d.setBarcode(barcode);
        d.setIsFrozen(isFrozen);
        d.setInitialQuantity(initialQuantity);
        return d;
    }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getFoodType() { return foodType; }
    public void setFoodType(String foodType) { this.foodType = foodType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public BigDecimal getPackSize() { return packSize; }
    public void setPackSize(BigDecimal packSize) { this.packSize = packSize; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public Boolean getIsFrozen() { return isFrozen; }
    public void setIsFrozen(Boolean isFrozen) { this.isFrozen = isFrozen; }
    public BigDecimal getInitialQuantity() { return initialQuantity; }
    public void setInitialQuantity(BigDecimal initialQuantity) { this.initialQuantity = initialQuantity; }
}
