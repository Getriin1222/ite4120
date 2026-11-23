package ee.taltech.ite4120.pantry.repository;

import ee.taltech.ite4120.pantry.dto.ProductQueryParams;
import ee.taltech.ite4120.pantry.model.Category;
import ee.taltech.ite4120.pantry.model.ProductRow;
import ee.taltech.ite4120.pantry.model.ProductView;
import java.math.BigDecimal;
import java.util.List;
import org.helex.commons.db.bean.PgBeanProcessor;
import org.helex.commons.db.repo.BaseRepository;
import org.helex.commons.db.sql.SqlBuilder;
import org.helex.commons.model.QueryResult;
import org.springframework.stereotype.Repository;

/**
 * Data access for the household inventory: the same BaseRepository / PgBeanProcessor /
 * SqlBuilder idiom as AnimalRepository. SQL and row mapping only; every rule lives in
 * PantryService, and every value arriving here has already been normalised there.
 */
@Repository
public class PantryRepository extends BaseRepository {

    /**
     * The inventory report: product + category + stock. Stock is a LEFT join only so a
     * missing companion row can be noticed (see {@link #toView}); with no product-delete
     * operation every active product has exactly one.
     */
    private static final String SELECT = """
            select p.id, p.category_code, c.name as category_name, p.food_type, p.name, p.brand,
                   p.pack_size, p.unit, p.barcode, p.is_frozen as frozen,
                   p.sys_version::bigint as product_version,
                   p.sys_created_at, p.sys_created_by, p.sys_modified_at, p.sys_modified_by,
                   s.id as stock_id, s.quantity::bigint as quantity, s.sys_version::bigint as stock_version,
                   s.sys_modified_at as stock_modified_at, s.sys_modified_by as stock_modified_by
              from pantry.product p
              join pantry.category c on c.code = p.category_code
              left join pantry.stock s on s.product_id = p.id
            """;

    /** Fixed order: food type, name, brand, id. Stable across pages. */
    private static final String ORDER = " order by p.food_type, p.name, p.brand nulls first, p.id";

    /** The normalised identity expression, written exactly as product_identity_ukey indexes it. */
    private static final String IDENTITY_MATCH = """
             p.sys_status = 'A' and p.barcode is null
               and lower(regexp_replace(p.name, '\\s+', ' ', 'g')) = lower(regexp_replace(?::text, '\\s+', ' ', 'g'))
               and lower(regexp_replace(coalesce(p.brand, ''), '\\s+', ' ', 'g'))
                   = lower(regexp_replace(coalesce(?::text, ''), '\\s+', ' ', 'g'))
               and coalesce(p.pack_size, 0) = coalesce(?::numeric, 0)
               and p.unit = ?
               and p.is_frozen = ?
            """;

    private final PgBeanProcessor rowBp = new PgBeanProcessor(ProductRow.class);
    private final PgBeanProcessor categoryBp = new PgBeanProcessor(Category.class);

    // --- reads ---------------------------------------------------------------

    public List<Category> categories() {
        return getBeans("select c.* from pantry.category c order by c.name", categoryBp);
    }

    public boolean categoryExists(String code) {
        Integer n = queryForObject("select count(1) from pantry.category where code = ?", Integer.class, code);
        return n != null && n > 0;
    }

    public List<String> foodTypes() {
        return jdbcTemplate.queryForList(
                "select distinct food_type from pantry.product where sys_status = 'A' order by food_type", String.class);
    }

    public ProductView load(long id) {
        ProductRow row = getBean(SELECT + " where p.id = ? and p.sys_status = 'A'", rowBp, id);
        return row == null ? null : toView(row);
    }

    public boolean productExists(long id) {
        Integer n = queryForObject("select count(1) from pantry.product where id = ? and sys_status = 'A'",
                Integer.class, id);
        return n != null && n > 0;
    }

    public QueryResult<ProductView> search(ProductQueryParams p) {
        return query(p, this::count, this::list);
    }

    private Integer count(ProductQueryParams p) {
        SqlBuilder sb = new SqlBuilder("""
                select count(1)
                  from pantry.product p
                  left join pantry.stock s on s.product_id = p.id
                 where p.sys_status = 'A'""");
        sb.append(filter(p));
        return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }

    private List<ProductView> list(ProductQueryParams p) {
        SqlBuilder sb = new SqlBuilder(SELECT + " where p.sys_status = 'A'");
        sb.append(filter(p));
        sb.append(new SqlBuilder(ORDER));
        sb.append(limit(p));
        List<ProductRow> rows = getBeans(sb.getSql(), rowBp, sb.getParams());
        return rows.stream().map(PantryRepository::toView).toList();
    }

    /**
     * The search contract. {@code search} arrives lowercased with SQL wildcards already
     * escaped by '!' (see PantryService), so a typed % or _ matches literally. Text
     * conditions are OR-ed; every other filter is AND-ed.
     */
    private SqlBuilder filter(ProductQueryParams p) {
        SqlBuilder sb = new SqlBuilder();
        if (p.getSearch() != null && !p.getSearch().isEmpty()) {
            String like = "%" + p.getSearch() + "%";
            sb.and("(lower(p.name) like ? escape '!' or lower(coalesce(p.brand, '')) like ? escape '!'"
                    + " or p.food_type like ? escape '!')", like, like, like);
        }
        if (p.getCategoryCode() != null) {
            sb.and("p.category_code = ?", p.getCategoryCode());
        }
        if (p.getFoodType() != null) {
            sb.and("p.food_type = ?", p.getFoodType());
        }
        if (p.getFrozen() != null) {
            sb.and("p.is_frozen = ?", p.getFrozen());
        }
        if (Boolean.TRUE.equals(p.getInStockOnly())) {
            sb.and("s.quantity > 0");
        }
        return sb;
    }

    /** The id of the active product holding this barcode, other than {@code excludeId}. */
    public Long findIdByBarcode(String barcode, Long excludeId) {
        List<Long> ids = jdbcTemplate.queryForList("""
                select p.id from pantry.product p
                 where p.sys_status = 'A' and p.barcode = ? and p.id <> coalesce(?::bigint, -1)
                 order by p.id limit 1""", Long.class, barcode, excludeId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** The id of the active barcode-free product with the same normalised identity, other than {@code excludeId}. */
    public Long findIdByIdentity(String name, String brand, BigDecimal packSize, String unit, boolean frozen,
            Long excludeId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "select p.id from pantry.product p where" + IDENTITY_MATCH
                        + " and p.id <> coalesce(?::bigint, -1) order by p.id limit 1",
                Long.class, name, brand, packSize, unit, frozen, excludeId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // --- writes --------------------------------------------------------------

    public Long insertProduct(String categoryCode, String foodType, String name, String brand,
            BigDecimal packSize, String unit, String barcode, boolean frozen) {
        return queryForObject("""
                insert into pantry.product (category_code, food_type, name, brand, pack_size, unit, barcode, is_frozen)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id""", Long.class,
                categoryCode, foodType, name, brand, packSize, unit, barcode, frozen);
    }

    public Long insertStock(long productId, long quantity) {
        return queryForObject("insert into pantry.stock (product_id, quantity) values (?, ?) returning id",
                Long.class, productId, quantity);
    }

    /**
     * Locks the active product row for the rest of the transaction and returns its current
     * sys_version, or null when there is no such active product. Two edits of the same
     * product therefore run one after the other, and the second sees the first's version.
     */
    public Long lockProductVersion(long id) {
        List<Long> v = jdbcTemplate.queryForList(
                "select sys_version::bigint from pantry.product where id = ? and sys_status = 'A' for update",
                Long.class, id);
        return v.isEmpty() ? null : v.get(0);
    }

    /** Same for the product's stock row: [quantity, sys_version], or null when absent. */
    public long[] lockStock(long productId) {
        List<long[]> rows = jdbcTemplate.query("""
                select s.quantity, s.sys_version
                  from pantry.stock s
                  join pantry.product p on p.id = s.product_id and p.sys_status = 'A'
                 where s.product_id = ?
                   for update of s""",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, productId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Writes the catalogue fields. Called with the row locked and its version checked.
     * The platform trigger leaves an unchanged row untouched (and its version as it was),
     * so zero affected rows is a no-op, not a failure.
     */
    public void updateProduct(long id, String categoryCode, String foodType, String name, String brand,
            BigDecimal packSize, String unit, String barcode, boolean frozen) {
        jdbcTemplate.update("""
                update pantry.product
                   set category_code = ?, food_type = ?, name = ?, brand = ?, pack_size = ?, unit = ?,
                       barcode = ?, is_frozen = ?
                 where id = ? and sys_status = 'A'""",
                categoryCode, foodType, name, brand, packSize, unit, barcode, frozen, id);
    }

    /** Sets the balance. Called with the stock row locked and its version checked. */
    public void updateQuantity(long productId, long quantity) {
        jdbcTemplate.update("update pantry.stock set quantity = ? where product_id = ?", quantity, productId);
    }

    // --- mapping -------------------------------------------------------------

    private static ProductView toView(ProductRow r) {
        if (r.getStockId() == null) {
            /* Product and stock are created in one transaction and never deleted, so this
               is an integrity fault to diagnose, never "quantity zero". */
            throw new IllegalStateException("pantry.product " + r.getId() + " has no companion pantry.stock row");
        }
        return new ProductView(
                String.valueOf(r.getId()),
                r.getCategoryCode(),
                r.getCategoryName(),
                r.getFoodType(),
                r.getName(),
                r.getBrand(),
                plain(r.getPackSize()),
                r.getUnit(),
                r.getBarcode(),
                Boolean.TRUE.equals(r.getFrozen()),
                r.getProductVersion(),
                new ProductView.StockView(
                        String.valueOf(r.getStockId()),
                        r.getQuantity(),
                        r.getStockVersion(),
                        r.getStockModifiedAt(),
                        r.getStockModifiedBy()),
                r.getSysCreatedAt(),
                r.getSysCreatedBy(),
                r.getSysModifiedAt(),
                r.getSysModifiedBy());
    }

    /** numeric(12,3) reads back as 500.000; the API shows 500 (and never 5E+2). */
    private static BigDecimal plain(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }
}
