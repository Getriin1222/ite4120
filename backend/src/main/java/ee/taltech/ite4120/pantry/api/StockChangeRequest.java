package ee.taltech.ite4120.pantry.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Body of both stock operations. For a stock addition {@code quantity} is the number of
 * packs or items bought (1 to 1000000); for a correction it is the new absolute count
 * (0 to 1000000). {@code expectedStockVersion} is the stockVersion the user last saw.
 */
@Schema(name = "StockChangeRequest")
public class StockChangeRequest {

    @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long expectedStockVersion;

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Long getExpectedStockVersion() { return expectedStockVersion; }
    public void setExpectedStockVersion(Long expectedStockVersion) { this.expectedStockVersion = expectedStockVersion; }
}
