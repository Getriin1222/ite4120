package ee.taltech.ite4120.pantry.model;

import java.math.BigDecimal;

/**
 * A product to create, as the caller sent it: raw, not yet normalised or validated.
 * PantryService owns every rule (trimming, the unit/size pair, the quantity range),
 * so the same rules apply whether the draft came from the API or from a test.
 * Quantities are BigDecimal on purpose: a fractional 1.5 must be refused, not
 * silently truncated to 1 by the JSON binding.
 */
public class ProductDraft {

    private String categoryCode;
    private String foodType;
    private String name;
    private String brand;
    private BigDecimal packSize;
    private String unit;
    private String barcode;
    private Boolean isFrozen;
    private BigDecimal initialQuantity;

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
