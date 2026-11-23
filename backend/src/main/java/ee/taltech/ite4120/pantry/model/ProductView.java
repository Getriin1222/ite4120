package ee.taltech.ite4120.pantry.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A product as the API returns it: catalogue fields, its category name and its
 * stock, joined. Database bigint ids travel as decimal STRINGS (a browser number
 * loses precision past 2^53); the versions stay integers. {@code productVersion} is
 * the product row's sys_version and {@code stock.stockVersion} the stock row's: the
 * two optimistic versions a client echoes back when it edits.
 */
public record ProductView(
        String id,
        String categoryCode,
        String categoryName,
        String foodType,
        String name,
        String brand,
        BigDecimal packSize,
        String unit,
        String barcode,
        boolean isFrozen,
        Long productVersion,
        StockView stock,
        OffsetDateTime sysCreatedAt,
        String sysCreatedBy,
        OffsetDateTime sysModifiedAt,
        String sysModifiedBy) {

    /** The companion stock balance: complete packs or individually counted items. */
    public record StockView(
            String id,
            long quantity,
            Long stockVersion,
            OffsetDateTime sysModifiedAt,
            String sysModifiedBy) {
    }
}
