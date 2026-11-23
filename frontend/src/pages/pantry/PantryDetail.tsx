import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AutoComplete, Button, Checkbox, Form, InputNumber, Modal, Space } from 'antd';
import { ShoppingOutlined } from '@ant-design/icons';
import { useDataController } from '@helex/core';
import {
  AppCard,
  AppTag,
  AppTimestamp,
  FieldItem,
  ResourceForm,
  useAppNotification,
  type ResourceFormSection,
} from '@helex/ui';
import {
  ApiError,
  displayFoodType,
  normaliseFoodType,
  packDescription,
  packLabel,
  pantryApi,
  UNITS,
  type Category,
  type Product,
  type ProductPatch,
} from '../../pantryApi';
import { StockDialog, type StockDialogMode } from './StockDialog';

/** The catalogue fields a product edit can change; stock is changed only by the stock actions. */
const EDITABLE = ['categoryCode', 'foodType', 'name', 'brand', 'unit', 'packSize', 'barcode', 'isFrozen'] as const;
type Editable = (typeof EDITABLE)[number];

const blankToNull = (v: unknown) => (typeof v === 'string' && v.trim() === '' ? null : v ?? null);

/**
 * One product, `/pantry/{id}`: ResourceForm + useDataController, the AnimalDetail
 * pattern. View shows the catalogue fields; Edit → Save sends only the changed fields
 * with the productVersion the user saw (PATCH). The stock card in the sidebar holds
 * Add stock, Use one and Correct quantity; the metadata card shows who changed what.
 * There is no delete in this version: a finished product stays at zero, Out of stock.
 */
export const PantryDetail = () => {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const notify = useAppNotification();
  const [form] = Form.useForm<Product>();
  const dc = useDataController<Product>({} as Product);
  const [modal, modalContext] = Modal.useModal();
  const [mode, setMode] = useState<'view' | 'edit'>('view');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [categories, setCategories] = useState<Category[]>([]);
  const [foodTypes, setFoodTypes] = useState<string[]>([]);
  const [dialog, setDialog] = useState<StockDialogMode | null>(null);
  const [usingOne, setUsingOne] = useState(false);
  const unit = Form.useWatch('unit', form);

  /** Show the server's latest product everywhere on the page. */
  const show = (p: Product) => {
    dc.load(p);
    form.setFieldsValue(p);
  };

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([pantryApi.get(id), pantryApi.categories(), pantryApi.foodTypes().catch(() => [])])
      .then(([product, cats, types]) => {
        if (cancelled) return;
        show(product);
        setCategories(cats);
        setFoodTypes(types);
      })
      .catch((e) => {
        notify.error('Could not load the product', e.message);
        navigate('/pantry');
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // dc, form and navigate are stable handles; the record is keyed by the route parameter.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, notify]);

  /* Switching to `each` clears the pack size within the same edit. */
  useEffect(() => {
    if (mode === 'edit' && unit === 'each') form.setFieldValue('packSize', null);
  }, [unit, mode, form]);

  const refresh = async () => {
    try {
      show(await pantryApi.get(id));
    } catch (e) {
      notify.error('Could not refresh the product', (e as Error).message);
    }
  };

  const confirmPackSizeChange = (count: number) =>
    new Promise<boolean>((resolve) => {
      modal.confirm({
        title: 'Change the pack description?',
        content: `${count} ${count === 1 ? 'pack is' : 'packs are'} at home. The count stays ${count}; only what one pack contains changes. Continue?`,
        okText: 'Save',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });

  const save = async (values: Product) => {
    const original = dc.current;
    const patch: ProductPatch = { expectedProductVersion: original.productVersion };
    const changed: Editable[] = [];
    EDITABLE.forEach((key) => {
      const next = key === 'unit' || key === 'isFrozen' ? values[key] : blankToNull(values[key]);
      const before = blankToNull(original[key]);
      const same =
        key === 'foodType'
          ? normaliseFoodType(String(next ?? '')) === String(before ?? '')
          : String(next ?? '') === String(before ?? '');
      if (!same) {
        (patch as Record<string, unknown>)[key] = next;
        changed.push(key);
      }
    });
    if (changed.length === 0) {
      setMode('view');
      return;
    }
    const sizeChanged = changed.includes('packSize') || changed.includes('unit');
    if (sizeChanged && original.stock.quantity > 0 && !(await confirmPackSizeChange(original.stock.quantity))) {
      return;
    }
    setSaving(true);
    try {
      const updated = await pantryApi.update(id, patch);
      show(updated);
      setMode('view');
      notify.success('Saved', updated.name);
    } catch (e) {
      const err = e as ApiError;
      if (err.status === 409 && err.existingProductId) {
        notify.error('That would duplicate another product', err.message);
      } else if (err.conflict) {
        notify.warning('The product changed meanwhile', 'The current values are shown again; repeat your edit if still needed.');
        await refresh();
        setMode('view');
      } else if (err.uncertain) {
        notify.warning('Could not confirm the save', 'The current values are shown again; check them before saving again.');
        await refresh();
        setMode('view');
      } else if (err.errors.length) {
        form.setFields(
          err.errors
            .filter((f) => (EDITABLE as readonly string[]).includes(f.field))
            .map((f) => ({ name: f.field as Editable, errors: [f.message] })),
        );
        notify.error('Check the highlighted fields', err.message);
      } else {
        notify.error('Save failed', err.message);
      }
    } finally {
      setSaving(false);
    }
  };

  const consumeOne = async () => {
    const p = dc.current;
    setUsingOne(true);
    try {
      const saved = await pantryApi.correctStock(p.id, p.stock.quantity - 1, p.stock.stockVersion);
      show(saved);
      notify.success('Used one', `Now ${packDescription(saved, saved.stock.quantity)}`);
    } catch (e) {
      const err = e as ApiError;
      if (err.conflict || err.uncertain) {
        notify.warning(
          err.conflict ? 'The stock changed meanwhile' : 'Could not confirm the save',
          'The current count is shown again; check it before using another one.',
        );
        await refresh();
      } else {
        notify.error('Use one failed', err.message);
      }
    } finally {
      setUsingOne(false);
    }
  };

  const sections: ResourceFormSection<Product>[] = [
    {
      key: 'product',
      title: 'Product',
      fields: [
        { name: 'name', label: 'Product name', type: 'text', required: true, rules: [{ required: true, whitespace: true, max: 255 }] },
        {
          name: 'brand',
          label: 'Brand',
          type: 'text',
          rules: [{ max: 100 }],
          viewRender: (value) => (value ? String(value) : '—'),
        },
        {
          name: 'foodType',
          label: 'Food type',
          type: 'custom',
          required: true,
          rules: [{ required: true, whitespace: true, message: 'Select or enter a food type' }, { max: 80 }],
          tooltip: 'Groups related products across brands; stored in lowercase',
          render: ({ value, onChange }) => (
            <AutoComplete
              value={value == null ? '' : String(value)}
              onChange={(v: string) => onChange?.(v)}
              options={foodTypes.map((t) => ({ value: t, label: displayFoodType(t) }))}
              style={{ width: '100%' }}
            />
          ),
          viewRender: (value) => displayFoodType(value == null ? '' : String(value)),
        },
        {
          name: 'categoryCode',
          label: 'Category',
          type: 'select',
          required: true,
          options: categories.map((c) => ({ value: c.code, label: c.name })),
          viewRender: (code) => categories.find((c) => c.code === code)?.name ?? String(code ?? ''),
        },
      ],
    },
    {
      key: 'pack',
      title: 'Pack',
      fields: [
        {
          name: 'unit',
          label: 'Unit',
          type: 'select',
          required: true,
          options: UNITS.map((u) => ({ value: u, label: u })),
        },
        {
          name: 'packSize',
          label: 'Pack size',
          type: 'custom',
          tooltip: 'What one counted pack contains; empty for unit each',
          render: ({ value, onChange }) => (
            <InputNumber
              style={{ width: '100%' }}
              value={typeof value === 'number' ? value : value == null ? null : Number(value)}
              onChange={(v) => onChange?.(v ?? null)}
              min={0.001}
              max={999999999.999}
              precision={unit === 'pcs' ? 0 : 3}
              disabled={unit === 'each'}
            />
          ),
          viewRender: (value) => (value == null ? '—' : String(value)),
        },
        {
          name: 'barcode',
          label: 'Barcode',
          type: 'text',
          rules: [{ pattern: /^\s*\d{8,14}\s*$/, message: 'Enter 8 to 14 digits' }],
          viewRender: (value) => (value ? String(value) : '—'),
        },
        {
          name: 'isFrozen',
          label: 'Bought frozen',
          type: 'custom',
          render: ({ value, onChange }) => (
            <Checkbox checked={!!value} onChange={(e) => onChange?.(e.target.checked)}>
              Bought frozen
            </Checkbox>
          ),
          viewRender: (value) => (value ? <AppTag>Frozen</AppTag> : 'No'),
        },
      ],
    },
  ];

  const p = dc.current;
  const quantity = p.stock?.quantity ?? 0;
  const busy = usingOne || saving;

  return (
    <>
      {modalContext}
      <ResourceForm<Product>
        mode={mode}
        entityLabel="Product"
        icon={<ShoppingOutlined />}
        name={p.name}
        subtitle={p.brand ? `${p.brand} · ${packLabel(p)}` : p.unit ? packLabel(p) : undefined}
        chips={
          p.stock ? (
            <>
              <AppTag>{displayFoodType(p.foodType)}</AppTag>
              {quantity === 0 && <AppTag>Out of stock</AppTag>}
              {p.isFrozen && <AppTag>Frozen</AppTag>}
            </>
          ) : undefined
        }
        loading={loading}
        saving={saving}
        form={form}
        dataController={dc}
        initialValues={dc.current}
        sections={sections}
        onBack={() => navigate('/pantry')}
        backTooltip="Back to the inventory"
        onEdit={() => setMode('edit')}
        onCancel={() => {
          dc.reset();
          form.setFieldsValue(dc.original ?? dc.current);
          setMode('view');
        }}
        onSave={save}
        sidebar={
          <div style={{ display: 'grid', gap: 12 }}>
            <AppCard title="Stock" size="small">
              <FieldItem label="At home">
                <b>{p.stock ? packDescription(p, quantity) : '—'}</b>
              </FieldItem>
              <FieldItem label="One pack">{p.unit ? packLabel(p) : '—'}</FieldItem>
              <Space wrap style={{ marginTop: 8 }}>
                <Button onClick={() => setDialog('add')} disabled={!p.stock || busy}>
                  Add stock
                </Button>
                <Button onClick={consumeOne} loading={usingOne} disabled={!p.stock || quantity === 0 || busy}>
                  Use one
                </Button>
                <Button onClick={() => setDialog('correct')} disabled={!p.stock || busy}>
                  Correct quantity
                </Button>
              </Space>
            </AppCard>
            <AppCard title="Metadata" size="small">
              <FieldItem label="Created at">
                <AppTimestamp value={p.sysCreatedAt} fallback="—" />
              </FieldItem>
              <FieldItem label="Created by">{p.sysCreatedBy ?? '—'}</FieldItem>
              <FieldItem label="Modified at">
                <AppTimestamp value={p.sysModifiedAt} fallback="—" />
              </FieldItem>
              <FieldItem label="Modified by">{p.sysModifiedBy ?? '—'}</FieldItem>
              <FieldItem label="Product version">{p.productVersion ?? '—'}</FieldItem>
              <FieldItem label="Stock changed at">
                <AppTimestamp value={p.stock?.sysModifiedAt} fallback="—" />
              </FieldItem>
              <FieldItem label="Stock changed by">{p.stock?.sysModifiedBy ?? '—'}</FieldItem>
              <FieldItem label="Stock version">{p.stock?.stockVersion ?? '—'}</FieldItem>
            </AppCard>
          </div>
        }
      />
      {dialog && p.stock && (
        <StockDialog mode={dialog} product={p} onClose={() => setDialog(null)} onChanged={show} />
      )}
    </>
  );
};
