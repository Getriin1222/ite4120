package ee.taltech.ite4120.pantry.service;

import ee.taltech.ite4120.pantry.dto.ProductQueryParams;
import ee.taltech.ite4120.pantry.model.Category;
import ee.taltech.ite4120.pantry.model.ProductDraft;
import ee.taltech.ite4120.pantry.model.ProductPatch;
import ee.taltech.ite4120.pantry.model.ProductView;
import ee.taltech.ite4120.pantry.repository.PantryRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.NotFoundException;
import org.helex.commons.model.QueryResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Business rules of the household inventory, PANTRY.01. Each rule here is one of the
 * specification's business tests (PantryBusinessRulesIT): spec first, tests second,
 * implementation third.
 *
 * <p>Writes run in a {@link TransactionTemplate} rather than under
 * {@code @Transactional} for one reason: when the database's unique index rejects a
 * duplicate, the transaction is already rolled back, and only then can the service
 * look up the existing product to name it in the 409.
 */
@Service
public class PantryService {

    /** Upper bound of a stock balance and of one addition (database check: stock_quantity_chk). */
    public static final long MAX_QUANTITY = 1_000_000L;

    private static final Set<String> UNITS = Set.of("g", "kg", "ml", "l", "pcs", "each");
    private static final Pattern BARCODE = Pattern.compile("\\d{8,14}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** numeric(12,3): at most nine digits before the decimal point. */
    private static final BigDecimal MAX_PACK_SIZE = new BigDecimal("999999999.999");

    private final PantryRepository repository;
    private final TransactionTemplate tx;

    public PantryService(PantryRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // --- reads ---------------------------------------------------------------

    public List<Category> categories() {
        return repository.categories();
    }

    /** Distinct normalised food types of active products, sorted: the food-type picker's choices. */
    public List<String> foodTypes() {
        return repository.foodTypes();
    }

    public ProductView require(long id) {
        ProductView p = repository.load(id);
        if (p == null) {
            throw new NotFoundException("product " + id + " not found");
        }
        return p;
    }

    /** The inventory report with the platform's default paging. */
    public QueryResult<ProductView> search(ProductQueryParams p) {
        return search(p, null, null);
    }

    /**
     * The inventory report: products joined with category and stock, filtered and paged.
     * {@code limit} and {@code offset} are the values the caller sent, or null; only sent
     * values are range-checked, so the platform's own default page size always applies.
     */
    public QueryResult<ProductView> search(ProductQueryParams p, Integer limit, Integer offset) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (limit != null && (limit < 1 || limit > 100)) {
            errors.put("limit", "must be a whole number from 1 to 100");
        }
        if (offset != null && offset < 0) {
            errors.put("offset", "must be zero or more");
        }
        if (p.getSearch() != null && p.getSearch().length() > 200) {
            errors.put("search", "must be at most 200 characters");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        String search = p.getSearch() == null ? "" : p.getSearch().trim().toLowerCase(Locale.ROOT);
        p.setSearch(search.isEmpty() ? null : escapeLike(search));
        p.setCategoryCode(blankToNull(p.getCategoryCode()));
        p.setFoodType(p.getFoodType() == null ? null : blankToNull(normaliseFoodType(p.getFoodType())));
        return repository.search(p);
    }

    // --- create --------------------------------------------------------------

    /**
     * Creates the product and its initial stock in ONE transaction: either both rows
     * exist afterwards or neither does. Duplicates are refused with the existing id.
     */
    public ProductView create(ProductDraft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        Catalogue c = catalogue(draft.getCategoryCode(), draft.getFoodType(), draft.getName(), draft.getBrand(),
                draft.getPackSize(), draft.getUnit(), draft.getBarcode(),
                draft.getIsFrozen() == null ? Boolean.FALSE : draft.getIsFrozen(), errors);
        Long quantity = wholeNumber("initialQuantity", draft.getInitialQuantity(), 0, MAX_QUANTITY, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        refuseDuplicate(c, null);
        Long id;
        try {
            id = tx.execute(status -> {
                Long productId = repository.insertProduct(c.categoryCode, c.foodType, c.name, c.brand,
                        c.packSize, c.unit, c.barcode, c.frozen);
                repository.insertStock(productId, quantity);
                return productId;
            });
        } catch (DuplicateKeyException race) {
            throw duplicateAfterRollback(c, null);
        }
        return require(id);
    }

    // --- edit catalogue fields -----------------------------------------------

    /**
     * Applies a partial edit of the catalogue fields after checking the product version
     * the caller last saw. The stock balance is never touched by a product edit.
     */
    public ProductView update(long id, ProductPatch patch) {
        if (patch.getExpectedProductVersion() == null) {
            throw FieldValidationException.of("expectedProductVersion", "is required: send the productVersion you last saw");
        }
        try {
            tx.executeWithoutResult(status -> {
                Long version = repository.lockProductVersion(id);
                if (version == null) {
                    throw new NotFoundException("product " + id + " not found");
                }
                if (!version.equals(patch.getExpectedProductVersion())) {
                    throw new StaleVersionException("product", id);
                }
                ProductView current = repository.load(id);
                Catalogue c = merged(current, patch);
                refuseDuplicate(c, id);
                repository.updateProduct(id, c.categoryCode, c.foodType, c.name, c.brand, c.packSize, c.unit,
                        c.barcode, c.frozen);
            });
        } catch (DuplicateKeyException race) {
            throw duplicateAfterRollback(merged(require(id), patch), id);
        }
        return require(id);
    }

    private Catalogue merged(ProductView current, ProductPatch patch) {
        String unit = patch.has(ProductPatch.UNIT) ? patch.getUnit() : current.unit();
        BigDecimal packSize = patch.has(ProductPatch.PACK_SIZE) ? patch.getPackSize() : current.packSize();
        /* Switching to `each` clears the pack size in the same patch. Switching from `each`
           to a sized unit leaves it null, which the validation below refuses until the
           patch also carries a positive size. */
        if (patch.has(ProductPatch.UNIT) && "each".equals(normaliseUnit(unit)) && !patch.has(ProductPatch.PACK_SIZE)) {
            packSize = null;
        }
        Map<String, String> errors = new LinkedHashMap<>();
        Catalogue c = catalogue(
                patch.has(ProductPatch.CATEGORY_CODE) ? patch.getCategoryCode() : current.categoryCode(),
                patch.has(ProductPatch.FOOD_TYPE) ? patch.getFoodType() : current.foodType(),
                patch.has(ProductPatch.NAME) ? patch.getName() : current.name(),
                patch.has(ProductPatch.BRAND) ? patch.getBrand() : current.brand(),
                packSize,
                unit,
                patch.has(ProductPatch.BARCODE) ? patch.getBarcode() : current.barcode(),
                patch.has(ProductPatch.IS_FROZEN) ? patch.getIsFrozen() : Boolean.valueOf(current.isFrozen()),
                errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return c;
    }

    // --- stock ---------------------------------------------------------------

    /** Adds a purchase to the stored balance (an increment, not a replacement). */
    public ProductView addStock(long id, BigDecimal quantity, Long expectedStockVersion) {
        Map<String, String> errors = new LinkedHashMap<>();
        Long add = wholeNumber("quantity", quantity, 1, MAX_QUANTITY, errors);
        requireVersion(expectedStockVersion, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        changeStock(id, expectedStockVersion, current -> {
            if (current + add > MAX_QUANTITY) {
                throw FieldValidationException.of("quantity", "the resulting stock of " + (current + add)
                        + " would exceed the maximum of " + MAX_QUANTITY);
            }
            return current + add;
        });
        return require(id);
    }

    /** Replaces the balance with the confirmed remaining count. "Use one" is this with the displayed count minus one. */
    public ProductView correctStock(long id, BigDecimal quantity, Long expectedStockVersion) {
        Map<String, String> errors = new LinkedHashMap<>();
        Long target = wholeNumber("quantity", quantity, 0, MAX_QUANTITY, errors);
        requireVersion(expectedStockVersion, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        changeStock(id, expectedStockVersion, current -> target);
        return require(id);
    }

    private interface Balance {
        long next(long current);
    }

    /**
     * The one stock-write path: lock the row, compare the version the caller saw, compute,
     * write. Two requests carrying the same version are serialised by the row lock; the
     * second finds the version advanced by the trigger and gets 409.
     */
    private void changeStock(long id, Long expectedVersion, Balance balance) {
        tx.executeWithoutResult(status -> {
            long[] stock = repository.lockStock(id);
            if (stock == null) {
                if (repository.productExists(id)) {
                    throw new IllegalStateException("pantry.product " + id + " has no companion pantry.stock row");
                }
                throw new NotFoundException("product " + id + " not found");
            }
            if (stock[1] != expectedVersion) {
                throw new StaleVersionException("stock", id);
            }
            repository.updateQuantity(id, balance.next(stock[0]));
        });
    }

    // --- rules ---------------------------------------------------------------

    /** Catalogue fields after normalisation; every value here satisfies the table's checks. */
    private record Catalogue(String categoryCode, String foodType, String name, String brand,
            BigDecimal packSize, String unit, String barcode, boolean frozen) {
    }

    private Catalogue catalogue(String categoryCode, String foodType, String name, String brand,
            BigDecimal packSize, String unit, String barcode, Boolean frozen, Map<String, String> errors) {
        String category = blankToNull(categoryCode);
        if (category == null) {
            errors.put("categoryCode", "is required: select a category");
        } else if (!repository.categoryExists(category)) {
            errors.put("categoryCode", "unknown category " + category + "; select an available category");
        }

        String type = foodType == null ? null : normaliseFoodType(foodType);
        if (type == null || type.isEmpty()) {
            errors.put("foodType", "is required: select or enter a food type");
        } else if (type.length() > 80) {
            errors.put("foodType", "must be at most 80 characters");
        }

        String trimmedName = name == null ? null : name.trim();
        if (trimmedName == null || trimmedName.isEmpty()) {
            errors.put("name", "is required");
        } else if (trimmedName.length() > 255) {
            errors.put("name", "must be at most 255 characters");
        }

        String trimmedBrand = blankToNull(brand);
        if (trimmedBrand != null && trimmedBrand.length() > 100) {
            errors.put("brand", "must be at most 100 characters");
        }

        String u = normaliseUnit(unit);
        BigDecimal size = packSize;
        if (u == null) {
            errors.put("unit", "is required: one of g, kg, ml, l, pcs, each");
        } else if (!UNITS.contains(u)) {
            errors.put("unit", "must be one of g, kg, ml, l, pcs, each");
        } else if ("each".equals(u)) {
            if (size != null) {
                errors.put("packSize", "must be empty for individually counted products (unit each)");
            }
        } else if (size == null || size.signum() <= 0) {
            errors.put("packSize", "enter a positive size for unit " + u + ", or choose each");
        } else if (size.stripTrailingZeros().scale() > 3 || size.compareTo(MAX_PACK_SIZE) > 0) {
            errors.put("packSize", "use at most three decimals and at most 999999999.999");
        } else if ("pcs".equals(u) && size.stripTrailingZeros().scale() > 0) {
            errors.put("packSize", "pieces per pack must be a whole number");
        }

        String code = blankToNull(barcode);
        if (code != null && !BARCODE.matcher(code).matches()) {
            errors.put("barcode", "enter 8 to 14 digits");
        }

        if (frozen == null) {
            errors.put("isFrozen", "must be true or false");
        }
        return new Catalogue(category, type, trimmedName, trimmedBrand, size, u, code, Boolean.TRUE.equals(frozen));
    }

    /**
     * Friendly duplicate check before writing, so the 409 can name the existing product.
     * The unique indexes stay authoritative for concurrent saves.
     */
    private void refuseDuplicate(Catalogue c, Long excludeId) {
        if (c.barcode != null) {
            Long existing = repository.findIdByBarcode(c.barcode, excludeId);
            if (existing != null) {
                throw new DuplicateProductException(
                        "barcode " + c.barcode + " already identifies a saved product; add stock there instead", existing);
            }
            return;
        }
        Long existing = repository.findIdByIdentity(c.name, c.brand, c.packSize, c.unit, c.frozen, excludeId);
        if (existing != null) {
            throw new DuplicateProductException("the same product (name, brand, pack size, unit and Frozen) "
                    + "is already saved; add stock there instead", existing);
        }
    }

    /** A unique index fired: the transaction is rolled back, now find who won. */
    private ConflictException duplicateAfterRollback(Catalogue c, Long excludeId) {
        try {
            refuseDuplicate(c, excludeId);
        } catch (DuplicateProductException e) {
            return e;
        }
        return new ConflictException("product", "a matching product was saved at the same moment; refresh and try again");
    }

    private static Long wholeNumber(String field, BigDecimal value, long min, long max, Map<String, String> errors) {
        if (value == null) {
            errors.put(field, "is required: a whole number from " + min + " to " + max);
            return null;
        }
        if (value.stripTrailingZeros().scale() > 0) {
            errors.put(field, "must be a whole number from " + min + " to " + max);
            return null;
        }
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            errors.put(field, "must be from " + min + " to " + max);
            return null;
        }
        return value.longValueExact();
    }

    private static void requireVersion(Long expectedStockVersion, Map<String, String> errors) {
        if (expectedStockVersion == null) {
            errors.put("expectedStockVersion", "is required: send the stockVersion you last saw");
        }
    }

    /** Trim, collapse inner whitespace, lowercase: "  Whole   MILK " and "whole milk" are one food type. */
    static String normaliseFoodType(String value) {
        return WHITESPACE.matcher(value.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static String normaliseUnit(String unit) {
        String u = blankToNull(unit);
        return u == null ? null : u.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    /** '!' is the LIKE escape character the repository declares; escape it first. */
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
