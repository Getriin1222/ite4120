package ee.taltech.ite4120.pantry.model;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * A partial edit of a product's catalogue fields (PATCH semantics). A field that is
 * absent keeps its stored value; a field that is present, even as null, is applied:
 * explicit null clears brand or barcode and is refused for a required field.
 * {@link #has(String)} tells the two cases apart, which a plain bean cannot.
 * Stock is never part of a product edit.
 */
public class ProductPatch {

    public static final String CATEGORY_CODE = "categoryCode";
    public static final String FOOD_TYPE = "foodType";
    public static final String NAME = "name";
    public static final String BRAND = "brand";
    public static final String PACK_SIZE = "packSize";
    public static final String UNIT = "unit";
    public static final String BARCODE = "barcode";
    public static final String IS_FROZEN = "isFrozen";

    private final Long expectedProductVersion;
    private final Set<String> present = new HashSet<>();
    private String categoryCode;
    private String foodType;
    private String name;
    private String brand;
    private BigDecimal packSize;
    private String unit;
    private String barcode;
    private Boolean isFrozen;

    public ProductPatch(Long expectedProductVersion) {
        this.expectedProductVersion = expectedProductVersion;
    }

    public boolean has(String field) { return present.contains(field); }
    public boolean isEmpty() { return present.isEmpty(); }
    public Long getExpectedProductVersion() { return expectedProductVersion; }

    public ProductPatch categoryCode(String v) { categoryCode = v; present.add(CATEGORY_CODE); return this; }
    public ProductPatch foodType(String v) { foodType = v; present.add(FOOD_TYPE); return this; }
    public ProductPatch name(String v) { name = v; present.add(NAME); return this; }
    public ProductPatch brand(String v) { brand = v; present.add(BRAND); return this; }
    public ProductPatch packSize(BigDecimal v) { packSize = v; present.add(PACK_SIZE); return this; }
    public ProductPatch unit(String v) { unit = v; present.add(UNIT); return this; }
    public ProductPatch barcode(String v) { barcode = v; present.add(BARCODE); return this; }
    public ProductPatch isFrozen(Boolean v) { isFrozen = v; present.add(IS_FROZEN); return this; }

    public String getCategoryCode() { return categoryCode; }
    public String getFoodType() { return foodType; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public BigDecimal getPackSize() { return packSize; }
    public String getUnit() { return unit; }
    public String getBarcode() { return barcode; }
    public Boolean getIsFrozen() { return isFrozen; }
}
