package ee.taltech.ite4120.pantry.service;

import org.helex.commons.exception.ConflictException;

/**
 * 409: the product being saved is already in the catalogue, by barcode or by the
 * normalised identity of a barcode-free entry. Carries the existing product's id so
 * the interface can offer "open the existing product and add stock there".
 */
public class DuplicateProductException extends ConflictException {

    private final String existingProductId;

    public DuplicateProductException(String message, Long existingProductId) {
        super("product", message);
        this.existingProductId = String.valueOf(existingProductId);
    }

    public String getExistingProductId() { return existingProductId; }
}
