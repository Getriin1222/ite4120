package ee.taltech.ite4120.pantry.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One row of the joined inventory report: product + category + stock, flat, as
 * {@code PantryRepository} selects it. Mapped by PgBeanProcessor (snake_case column
 * aliases onto these camelCase properties); the repository turns it into a
 * {@link ProductView}. The stock columns are nullable here only so that a missing
 * companion stock row can be detected and reported as an integrity error.
 */
public class ProductRow {

    private Long id;
    private String categoryCode;
    private String categoryName;
    private String foodType;
    private String name;
    private String brand;
    private BigDecimal packSize;
    private String unit;
    private String barcode;
    private Boolean frozen;
    private Long productVersion;
    private OffsetDateTime sysCreatedAt;
    private String sysCreatedBy;
    private OffsetDateTime sysModifiedAt;
    private String sysModifiedBy;
    private Long stockId;
    private Long quantity;
    private Long stockVersion;
    private OffsetDateTime stockModifiedAt;
    private String stockModifiedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
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
    public Boolean getFrozen() { return frozen; }
    public void setFrozen(Boolean frozen) { this.frozen = frozen; }
    public Long getProductVersion() { return productVersion; }
    public void setProductVersion(Long productVersion) { this.productVersion = productVersion; }
    public OffsetDateTime getSysCreatedAt() { return sysCreatedAt; }
    public void setSysCreatedAt(OffsetDateTime sysCreatedAt) { this.sysCreatedAt = sysCreatedAt; }
    public String getSysCreatedBy() { return sysCreatedBy; }
    public void setSysCreatedBy(String sysCreatedBy) { this.sysCreatedBy = sysCreatedBy; }
    public OffsetDateTime getSysModifiedAt() { return sysModifiedAt; }
    public void setSysModifiedAt(OffsetDateTime sysModifiedAt) { this.sysModifiedAt = sysModifiedAt; }
    public String getSysModifiedBy() { return sysModifiedBy; }
    public void setSysModifiedBy(String sysModifiedBy) { this.sysModifiedBy = sysModifiedBy; }
    public Long getStockId() { return stockId; }
    public void setStockId(Long stockId) { this.stockId = stockId; }
    public Long getQuantity() { return quantity; }
    public void setQuantity(Long quantity) { this.quantity = quantity; }
    public Long getStockVersion() { return stockVersion; }
    public void setStockVersion(Long stockVersion) { this.stockVersion = stockVersion; }
    public OffsetDateTime getStockModifiedAt() { return stockModifiedAt; }
    public void setStockModifiedAt(OffsetDateTime stockModifiedAt) { this.stockModifiedAt = stockModifiedAt; }
    public String getStockModifiedBy() { return stockModifiedBy; }
    public void setStockModifiedBy(String stockModifiedBy) { this.stockModifiedBy = stockModifiedBy; }
}
