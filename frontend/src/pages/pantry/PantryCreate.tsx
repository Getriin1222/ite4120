import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, AutoComplete, Button, Checkbox, Form, Input, InputNumber, Select, Table } from 'antd';
import { AppButtonPrimary, AppButtonSecondary, AppCard, AppTag, useAppNotification } from '@helex/ui';
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
  type Unit,
} from '../../pantryApi';

interface CreateValues {
  categoryCode: string;
  foodType: string;
  name: string;
  brand?: string;
  unit: Unit;
  packSize?: number | null;
  barcode?: string;
  isFrozen?: boolean;
  initialQuantity: number;
}

const FORM_FIELDS: (keyof CreateValues)[] = [
  'categoryCode', 'foodType', 'name', 'brand', 'unit', 'packSize', 'barcode', 'isFrozen', 'initialQuantity',
];

/** Related products for the chosen food type: loading, loaded, or the check failed. */
type Related = { state: 'idle' } | { state: 'loading' } | { state: 'failed' } | { state: 'loaded'; items: Product[] };

const sameText = (a?: string | null, b?: string | null) =>
  (a ?? '').trim().replace(/\s+/g, ' ').toLowerCase() === (b ?? '').trim().replace(/\s+/g, ' ').toLowerCase();

/**
 * Add product, `/pantry/new` (PANTRY.01 § Add new product flow). Product details and
 * the initial count are saved together by one request. Choosing a food type shows the
 * products already saved under it, so a shopper sees the milk at home before adding a
 * new brand. The check is advisory: if it fails the form says so, and the server's
 * duplicate check stays authoritative.
 */
export const PantryCreate = () => {
  const navigate = useNavigate();
  const notify = useAppNotification();
  const [form] = Form.useForm<CreateValues>();
  const [categories, setCategories] = useState<Category[]>([]);
  const [foodTypes, setFoodTypes] = useState<string[]>([]);
  const [related, setRelated] = useState<Related>({ state: 'idle' });
  const [saving, setSaving] = useState(false);
  const [duplicateOf, setDuplicateOf] = useState<{ id: string; message: string } | null>(null);
  const [uncertain, setUncertain] = useState(false);

  const unit = Form.useWatch('unit', form);
  const foodType = Form.useWatch('foodType', form);
  const name = Form.useWatch('name', form);
  const brand = Form.useWatch('brand', form);

  useEffect(() => {
    pantryApi.categories().then(setCategories).catch((e) => notify.error('Could not load categories', e.message));
    pantryApi.foodTypes().then(setFoodTypes).catch(() => setFoodTypes([]));
  }, [notify]);

  /* Related products by exact normalised food type, looked up as the user settles on one. */
  const normalisedType = foodType ? normaliseFoodType(foodType) : '';
  useEffect(() => {
    if (!normalisedType) {
      setRelated({ state: 'idle' });
      return;
    }
    let cancelled = false;
    setRelated({ state: 'loading' });
    const t = setTimeout(() => {
      pantryApi
        .search({ foodType: normalisedType, limit: 100 })
        .then((r) => !cancelled && setRelated({ state: 'loaded', items: r.data }))
        .catch(() => !cancelled && setRelated({ state: 'failed' }));
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [normalisedType]);

  /* Switching to `each` clears the pack size: individually counted items have none. */
  useEffect(() => {
    if (unit === 'each') form.setFieldValue('packSize', null);
  }, [unit, form]);

  const foodTypeOptions = useMemo(
    () =>
      foodTypes
        .filter((t) => !foodType || t.includes(normaliseFoodType(foodType)))
        .map((t) => ({ value: t, label: displayFoodType(t) })),
    [foodTypes, foodType],
  );

  const submit = async (values: CreateValues) => {
    setSaving(true);
    setDuplicateOf(null);
    setUncertain(false);
    try {
      const created = await pantryApi.create({
        categoryCode: values.categoryCode,
        foodType: values.foodType,
        name: values.name,
        brand: values.brand?.trim() ? values.brand : null,
        unit: values.unit,
        packSize: values.unit === 'each' ? null : values.packSize ?? null,
        barcode: values.barcode?.trim() ? values.barcode.trim() : null,
        isFrozen: !!values.isFrozen,
        initialQuantity: values.initialQuantity,
      });
      notify.success('Product saved', `${created.name}: ${packDescription(created, created.stock.quantity)}`);
      navigate(`/pantry/${created.id}`);
    } catch (e) {
      const err = e as ApiError;
      if (err.status === 409 && err.existingProductId) {
        setDuplicateOf({ id: err.existingProductId, message: err.message });
      } else if (err.uncertain) {
        /* Do not resend: show what is saved under this food type, then let the user decide. */
        setUncertain(true);
        setRelated({ state: 'loading' });
        pantryApi
          .search({ foodType: normalisedType, limit: 100 })
          .then((r) => setRelated({ state: 'loaded', items: r.data }))
          .catch(() => setRelated({ state: 'failed' }));
      } else if (err.errors.length) {
        form.setFields(
          err.errors
            .filter((f) => FORM_FIELDS.includes(f.field as keyof CreateValues))
            .map((f) => ({ name: f.field as keyof CreateValues, errors: [f.message] })),
        );
        notify.error('Check the highlighted fields', err.message);
      } else {
        notify.error('Saving failed', err.message);
      }
    } finally {
      setSaving(false);
    }
  };

  const possibleDuplicate = (p: Product) => sameText(p.name, name) && sameText(p.brand, brand);

  return (
    <AppCard title="Add product" style={{ maxWidth: 760, margin: '24px auto' }}>
      <Form<CreateValues>
        form={form}
        layout="vertical"
        onFinish={submit}
        requiredMark
        initialValues={{ initialQuantity: 0, isFrozen: false }}
      >
        <Form.Item name="categoryCode" label="Category" rules={[{ required: true, message: 'Select a category' }]}>
          <Select
            placeholder="Choose a category"
            options={categories.map((c) => ({ value: c.code, label: c.name }))}
          />
        </Form.Item>
        <Form.Item
          name="foodType"
          label="Food type"
          rules={[{ required: true, whitespace: true, message: 'Select or enter a food type' }, { max: 80 }]}
          extra="Groups related products across brands, e.g. Milk. Choose an existing one or type a new one."
        >
          <AutoComplete options={foodTypeOptions} placeholder="Milk" />
        </Form.Item>

        {related.state === 'failed' && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="Existing products could not be checked"
            description="You may already have this at home. Saving still checks for exact duplicates."
          />
        )}
        {related.state === 'loaded' && related.items.length === 0 && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={`No saved products under ${displayFoodType(normalisedType)} yet.`}
          />
        )}
        {related.state === 'loaded' && related.items.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 8 }}
              message={`You already have products under ${displayFoodType(normalisedType)}`}
              description="If one of these is the item you bought, open it and add stock there instead."
            />
            <Table<Product>
              size="small"
              rowKey="id"
              pagination={false}
              dataSource={related.items}
              columns={[
                {
                  title: 'Product',
                  dataIndex: 'name',
                  render: (n: string, p) => (
                    <>
                      {n} {possibleDuplicate(p) && <AppTag>Possible duplicate</AppTag>}
                    </>
                  ),
                },
                { title: 'Brand', dataIndex: 'brand', render: (b?: string | null) => b ?? '—' },
                { title: 'Pack', key: 'pack', render: (_: unknown, p) => packLabel(p) },
                {
                  title: 'At home',
                  key: 'qty',
                  render: (_: unknown, p) => (
                    <>
                      {packDescription(p, p.stock.quantity)} {p.stock.quantity === 0 && <AppTag>Out of stock</AppTag>}
                    </>
                  ),
                },
                { title: 'Frozen', dataIndex: 'isFrozen', render: (f: boolean) => (f ? 'Yes' : '—') },
                {
                  title: '',
                  key: 'open',
                  render: (_: unknown, p) => (
                    <Button size="small" onClick={() => navigate(`/pantry/${p.id}`)}>
                      Open existing product
                    </Button>
                  ),
                },
              ]}
            />
          </div>
        )}

        <Form.Item name="name" label="Product name" rules={[{ required: true, whitespace: true }, { max: 255 }]}>
          <Input placeholder="Whole milk" />
        </Form.Item>
        <Form.Item name="brand" label="Brand" rules={[{ max: 100 }]} extra="Optional">
          <Input placeholder="Example Dairy A" />
        </Form.Item>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item name="unit" label="Unit" rules={[{ required: true, message: 'Choose a unit' }]}
            extra="each: individually counted, like loose apples">
            <Select options={UNITS.map((u) => ({ value: u, label: u }))} placeholder="g, kg, ml, l, pcs or each" />
          </Form.Item>
          {unit !== 'each' && (
            <Form.Item
              name="packSize"
              label="Pack size"
              rules={[{ required: true, message: 'Enter the amount in one pack' }]}
              extra={unit === 'pcs' ? 'Pieces per pack, e.g. 10 eggs' : 'Amount in one pack, e.g. 500 (g)'}
            >
              <InputNumber
                style={{ width: '100%' }}
                min={0.001}
                max={999999999.999}
                precision={unit === 'pcs' ? 0 : 3}
                placeholder="500"
              />
            </Form.Item>
          )}
        </div>
        <Form.Item
          name="barcode"
          label="Barcode"
          rules={[{ pattern: /^\s*\d{8,14}\s*$/, message: 'Enter 8 to 14 digits' }]}
          extra="Optional. Kept as text, so leading zeroes stay."
        >
          <Input inputMode="numeric" placeholder="0123456789012" />
        </Form.Item>
        <Form.Item name="isFrozen" valuePropName="checked">
          <Checkbox>Bought frozen</Checkbox>
        </Form.Item>
        <Form.Item
          name="initialQuantity"
          label="Initial quantity"
          rules={[{ required: true, message: 'Enter how many packs or items are at home (0 is allowed)' }]}
          extra="Whole packs or items now at home. An opened pack still counts as one."
        >
          <InputNumber min={0} max={1000000} precision={0} style={{ width: 160 }} />
        </Form.Item>

        {duplicateOf && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="This product is already saved"
            description={duplicateOf.message}
            action={<Button onClick={() => navigate(`/pantry/${duplicateOf.id}`)}>Open existing product</Button>}
          />
        )}
        {uncertain && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="Could not confirm whether the product was saved"
            description="The app got no answer. Check the products listed above before saving again."
          />
        )}
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <AppButtonSecondary onClick={() => navigate('/pantry')}>Cancel</AppButtonSecondary>
          <AppButtonPrimary htmlType="submit" loading={saving}>
            Save
          </AppButtonPrimary>
        </div>
      </Form>
    </AppCard>
  );
};
