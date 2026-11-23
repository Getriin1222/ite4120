import { useEffect, useState } from 'react';
import { InputNumber, Modal } from 'antd';
import { useAppNotification } from '@helex/ui';
import { ApiError, packDescription, packLabel, pantryApi, type Product } from '../../pantryApi';

export type StockDialogMode = 'add' | 'correct';

/**
 * Add stock (an increment) or Correct quantity (a replacement), PANTRY.01 § UI.
 * Shows the current count and pack description; Save is disabled while the request is
 * in flight. A 409 means someone changed the stock: the dialog refreshes the product,
 * shows the new count, and the user decides again. No answer at all means the save
 * may or may not have happened: the dialog refreshes before allowing another try and
 * never resends on its own.
 */
export const StockDialog = ({
  mode,
  product,
  onClose,
  onChanged,
}: {
  mode: StockDialogMode;
  product: Product;
  onClose: () => void;
  /** Called with the server's latest product after a save or a refresh. */
  onChanged: (latest: Product) => void;
}) => {
  const notify = useAppNotification();
  const [current, setCurrent] = useState<Product>(product);
  const [value, setValue] = useState<number | null>(mode === 'add' ? 1 : product.stock.quantity);
  const [pending, setPending] = useState(false);

  useEffect(() => setCurrent(product), [product]);

  const refresh = async () => {
    try {
      const latest = await pantryApi.get(current.id);
      setCurrent(latest);
      onChanged(latest);
      if (mode === 'correct') setValue(latest.stock.quantity);
    } catch (e) {
      notify.error('Could not refresh the product', (e as Error).message);
    }
  };

  const save = async () => {
    if (value == null) return;
    setPending(true);
    try {
      const saved =
        mode === 'add'
          ? await pantryApi.addStock(current.id, value, current.stock.stockVersion)
          : await pantryApi.correctStock(current.id, value, current.stock.stockVersion);
      onChanged(saved);
      notify.success(
        mode === 'add' ? 'Stock added' : 'Quantity corrected',
        `${saved.name}: now ${packDescription(saved, saved.stock.quantity)}`,
      );
      onClose();
    } catch (e) {
      const err = e as ApiError;
      if (err.conflict) {
        notify.warning('The stock changed meanwhile', 'The current count is shown again. Check it, then save if still needed.');
        await refresh();
      } else if (err.uncertain) {
        notify.warning(
          'Could not confirm the save',
          'The app did not get an answer. The current count is shown again; check it before saving another time.',
        );
        await refresh();
      } else {
        notify.error('Save failed', err.message);
      }
    } finally {
      setPending(false);
    }
  };

  const min = mode === 'add' ? 1 : 0;
  return (
    <Modal
      open
      title={mode === 'add' ? `Add stock · ${current.name}` : `Correct quantity · ${current.name}`}
      okText={mode === 'add' ? 'Add' : 'Save count'}
      onOk={save}
      onCancel={onClose}
      confirmLoading={pending}
      okButtonProps={{ disabled: pending || value == null }}
    >
      <p>
        Now at home: <b>{packDescription(current, current.stock.quantity)}</b>
        {current.brand ? ` · ${current.brand}` : ''} · one pack: {packLabel(current)}
      </p>
      <p>{mode === 'add' ? 'How many packs/items are you adding?' : 'How many packs/items remain?'}</p>
      <InputNumber
        aria-label={mode === 'add' ? 'Packs or items to add' : 'Packs or items remaining'}
        min={min}
        max={1000000}
        precision={0}
        step={1}
        value={value}
        onChange={(v) => setValue(typeof v === 'number' ? v : null)}
        style={{ width: 160 }}
        autoFocus
      />
      {value != null && (
        <p style={{ marginTop: 12, opacity: 0.75 }}>
          After saving:{' '}
          {packDescription(current, mode === 'add' ? current.stock.quantity + value : value)}
        </p>
      )}
    </Modal>
  );
};
