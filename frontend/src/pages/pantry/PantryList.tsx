import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Checkbox, Empty, Select, Space } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import {
  AppButtonPrimary,
  AppLink,
  AppTag,
  ResourceList,
  useAppNotification,
  type ResourceListColumn,
} from '@helex/ui';
import {
  ApiError,
  displayFoodType,
  packDescription,
  packLabel,
  pantryApi,
  type Category,
  type Product,
} from '../../pantryApi';
import { StockDialog, type StockDialogMode } from './StockDialog';

/** The API's page-size ceiling; a household inventory is expected to fit. */
const PAGE = 100;

interface Filters {
  categoryCode?: string;
  frozen: boolean;
  inStockOnly: boolean;
}
const NO_FILTERS: Filters = { frozen: false, inStockOnly: false };

/**
 * The inventory, `/pantry` (PANTRY.01 § UI): ResourceList over the joined report,
 * search across name/brand/food type, filters for category, Frozen and In stock, and
 * per-row stock actions. Zero-stock products stay visible, marked Out of stock. An API
 * failure is shown as an error with Retry, never as "no stock".
 */
export const PantryList = () => {
  const navigate = useNavigate();
  const notify = useAppNotification();
  const [products, setProducts] = useState<Product[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [categories, setCategories] = useState<Category[]>([]);
  /* What the filter card shows vs what the list is filtered by: they meet on Apply,
     the same explicit-apply pattern as the animals list. */
  const [draft, setDraft] = useState<Filters>(NO_FILTERS);
  const [applied, setApplied] = useState<Filters>(NO_FILTERS);
  const [selected, setSelected] = useState<Product | null>(null);
  const [dialog, setDialog] = useState<{ mode: StockDialogMode; product: Product } | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    pantryApi.categories().then(setCategories).catch(() => setCategories([]));
  }, []);

  const load = useCallback((text: string, f: Filters) => {
    setLoading(true);
    setFailed(null);
    pantryApi
      .search({
        search: text.trim() || undefined,
        categoryCode: f.categoryCode,
        frozen: f.frozen ? true : undefined, // unchecked omits the filter
        inStockOnly: f.inStockOnly || undefined,
        limit: PAGE,
      })
      .then((result) => {
        setProducts(result.data);
        setTotal(result.meta?.total ?? result.data.length);
      })
      .catch((e: Error) => setFailed(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const t = setTimeout(() => load(search, applied), 250);
    return () => clearTimeout(t);
  }, [search, applied, load, reloadKey]);

  /** Put the server's latest version of one product into the list. */
  const replace = (latest: Product) => {
    setProducts((list) => list.map((p) => (p.id === latest.id ? latest : p)));
    setSelected((s) => (s?.id === latest.id ? latest : s));
  };

  const consumeOne = async (p: Product) => {
    setPendingId(p.id);
    try {
      const saved = await pantryApi.correctStock(p.id, p.stock.quantity - 1, p.stock.stockVersion);
      replace(saved);
      notify.success('Used one', `${saved.name}: now ${packDescription(saved, saved.stock.quantity)}`);
    } catch (e) {
      const err = e as ApiError;
      if (err.conflict || err.uncertain) {
        notify.warning(
          err.conflict ? 'The stock changed meanwhile' : 'Could not confirm the save',
          'The current count is shown again; check it before using another one.',
        );
        await pantryApi.get(p.id).then(replace).catch(() => setReloadKey((k) => k + 1));
      } else {
        notify.error('Use one failed', err.message);
      }
    } finally {
      setPendingId(null);
    }
  };

  const filtered = search.trim() !== '' || applied.categoryCode !== undefined || applied.frozen || applied.inStockOnly;
  const stop = (e: { stopPropagation: () => void }) => e.stopPropagation();

  const columns: ResourceListColumn<Product>[] = [
    {
      key: 'name',
      title: 'Product',
      dataIndex: 'name',
      locked: true,
      render: (name: string, p: Product) => (
        <AppLink
          href={`/pantry/${p.id}`}
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            navigate(`/pantry/${p.id}`);
          }}
        >
          {name}
        </AppLink>
      ),
    },
    { key: 'brand', title: 'Brand', dataIndex: 'brand', render: (b?: string | null) => b ?? '—' },
    { key: 'foodType', title: 'Food type', dataIndex: 'foodType', render: (t: string) => displayFoodType(t) },
    { key: 'categoryName', title: 'Category', dataIndex: 'categoryName' },
    {
      key: 'stock',
      title: 'Quantity',
      dataIndex: 'stock',
      render: (_: unknown, p: Product) => (
        <Space size={4} wrap>
          <b>{packDescription(p, p.stock.quantity)}</b>
          {p.stock.quantity === 0 && <AppTag>Out of stock</AppTag>}
        </Space>
      ),
    },
    {
      key: 'isFrozen',
      title: 'Frozen',
      dataIndex: 'isFrozen',
      render: (frozen: boolean) => (frozen ? <AppTag>Frozen</AppTag> : '—'),
    },
    {
      key: 'actions',
      title: 'Stock',
      dataIndex: 'id',
      locked: true,
      render: (_: unknown, p: Product) => (
        <Space size={4} wrap onClick={stop}>
          <Button size="small" onClick={() => setDialog({ mode: 'add', product: p })}>
            Add stock
          </Button>
          <Button
            size="small"
            disabled={p.stock.quantity === 0 || pendingId === p.id}
            loading={pendingId === p.id}
            onClick={() => consumeOne(p)}
            aria-label={`Use one ${p.name}`}
          >
            Use one
          </Button>
          <Button size="small" onClick={() => setDialog({ mode: 'correct', product: p })}>
            Correct
          </Button>
        </Space>
      ),
    },
  ];

  const addProduct = (
    <AppButtonPrimary icon={<PlusOutlined />} onClick={() => navigate('/pantry/new')}>
      Add product
    </AppButtonPrimary>
  );

  return (
    <div style={{ display: 'grid', gap: 12 }}>
      {failed && (
        <Alert
          type="error"
          showIcon
          message="The inventory could not be loaded"
          description={`${failed}. This is not an empty pantry: the stock is unknown until it loads.`}
          action={<Button onClick={() => setReloadKey((k) => k + 1)}>Retry</Button>}
        />
      )}
      {!failed && !loading && products.length === 0 && (
        <Empty description={filtered ? 'No matching products' : 'Your inventory is empty'}>{addProduct}</Empty>
      )}
      {!failed && total > products.length && (
        <Alert
          type="info"
          showIcon
          message={`Showing the first ${products.length} of ${total} products. Search or filter to narrow the list.`}
        />
      )}
      <ResourceList<Product>
        title="Pantry"
        columns={columns}
        dataSource={failed ? [] : products}
        rowKey="id"
        loading={loading}
        search={{ value: search, onChange: setSearch, placeholder: 'Product, brand or food type…' }}
        filter={{
          content: (
            <div style={{ display: 'grid', gap: 8 }}>
              <Select
                style={{ minWidth: 240 }}
                placeholder="All categories"
                allowClear
                value={draft.categoryCode}
                onChange={(code?: string) => setDraft((d) => ({ ...d, categoryCode: code }))}
                options={categories.map((c) => ({ value: c.code, label: c.name }))}
                aria-label="Category"
              />
              <Checkbox checked={draft.frozen} onChange={(e) => setDraft((d) => ({ ...d, frozen: e.target.checked }))}>
                Frozen only
              </Checkbox>
              <Checkbox
                checked={draft.inStockOnly}
                onChange={(e) => setDraft((d) => ({ ...d, inStockOnly: e.target.checked }))}
              >
                In stock only
              </Checkbox>
            </div>
          ),
          onApply: () => setApplied(draft),
          onClear: () => {
            setDraft(NO_FILTERS);
            setApplied(NO_FILTERS);
          },
        }}
        actions={addProduct}
        detailView={{
          selectedRecord: selected,
          onSelectedRecordChange: setSelected,
          title: 'Product',
          render: (p) => (
            <div style={{ display: 'grid', gap: 8 }}>
              <div>
                <b>{p.name}</b>
                {p.brand ? ` · ${p.brand}` : ''}
              </div>
              <div>Food type: {displayFoodType(p.foodType)}</div>
              <div>Category: {p.categoryName}</div>
              <div>One pack: {packLabel(p)}</div>
              <div>
                At home: <b>{packDescription(p, p.stock.quantity)}</b>
                {p.stock.quantity === 0 && <> · <AppTag>Out of stock</AppTag></>}
              </div>
              {p.isFrozen && <AppTag>Frozen</AppTag>}
              {p.barcode && <div>Barcode: {p.barcode}</div>}
            </div>
          ),
        }}
      />
      {dialog && (
        <StockDialog
          mode={dialog.mode}
          product={dialog.product}
          onClose={() => setDialog(null)}
          onChanged={replace}
        />
      )}
    </div>
  );
};
