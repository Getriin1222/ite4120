package ee.taltech.ite4120.pantry.service;

import org.helex.commons.exception.ConflictException;

/**
 * 409: the request was based on a product or stock version that is no longer
 * current. Someone else saved in between, or this very request was already applied
 * and is being resent. The client refreshes and decides again; nothing is overwritten.
 */
public class StaleVersionException extends ConflictException {

    public StaleVersionException(String what, long id) {
        super("version", "the " + what + " of product " + id
                + " was changed by another save; refresh and check the current values before saving again");
    }
}
