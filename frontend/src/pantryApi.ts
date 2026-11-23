// The pantry API client: thin fetch wrappers over /api/pantry, same shape as api.ts.
// Ids and barcodes are strings end to end; versions are numbers the server returns
// and the client only echoes back (never assumes a starting value).

import type { QueryResult } from './api';

export interface Category {
  code: string;
  name: string;
}

export interface StockView {
  id: string;
  quantity: number;
  stockVersion: number;
  sysModifiedAt?: string;
  sysModifiedBy?: string;
}

export interface Product {
  id: string;
  categoryCode: string;
  categoryName: string;
  foodType: string;
  name: string;
  brand?: string | null;
  packSize?: number | null;
  unit: Unit;
  barcode?: string | null;
  isFrozen: boolean;
  productVersion: number;
  stock: StockView;
  sysCreatedAt?: string;
  sysCreatedBy?: string;
  sysModifiedAt?: string;
  sysModifiedBy?: string;
}

export type Unit = 'g' | 'kg' | 'ml' | 'l' | 'pcs' | 'each';
export const UNITS: Unit[] = ['g', 'kg', 'ml', 'l', 'pcs', 'each'];

export interface ProductCreate {
  categoryCode: string;
  foodType: string;
  name: string;
  brand?: string | null;
  packSize?: number | null;
  unit: Unit;
  barcode?: string | null;
  isFrozen: boolean;
  initialQuantity: number;
}

/** PATCH body: only the fields that changed, plus the version the user last saw. */
export type ProductPatch = Partial<Omit<ProductCreate, 'initialQuantity'>> & { expectedProductVersion: number };

export interface ProductQuery {
  search?: string;
  categoryCode?: string;
  foodType?: string;
  frozen?: boolean;
  inStockOnly?: boolean;
  limit?: number;
  offset?: number;
}

export interface FieldProblem {
  field: string;
  message: string;
}

/**
 * A failed call. `status` is the HTTP status, or 0 when no answer arrived (timeout or
 * lost connection): for a write that means the app cannot tell whether it was saved.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly errors: FieldProblem[];
  readonly existingProductId?: string;

  constructor(status: number, message: string, problem?: Record<string, unknown> | null) {
    super(message);
    this.status = status;
    this.errors = Array.isArray(problem?.errors) ? (problem!.errors as FieldProblem[]) : [];
    this.existingProductId =
      typeof problem?.existingProductId === 'string' ? (problem.existingProductId as string) : undefined;
  }

  /** No answer: the save may or may not have happened. */
  get uncertain(): boolean {
    return this.status === 0;
  }

  get conflict(): boolean {
    return this.status === 409;
  }
}

const TIMEOUT_MS = 15000;

function authHeaders(): Record<string, string> {
  // MockAuthProvider persists the signed-in mock user here (same as api.ts).
  const user = localStorage.getItem('helex_mock_user') || 'superadmin';
  return { Authorization: `Bearer ${user}` };
}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      signal: controller.signal,
      headers: {
        ...authHeaders(),
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(init.headers as Record<string, string> | undefined),
      },
    });
  } catch {
    throw new ApiError(0, 'No answer from the server: the app could not confirm the result.');
  } finally {
    clearTimeout(timer);
  }
  if (!response.ok) {
    // The backend answers application/problem+json; surface its human text.
    const problem = (await response.json().catch(() => null)) as Record<string, unknown> | null;
    const detail = typeof problem?.detail === 'string' ? problem.detail : `HTTP ${response.status}`;
    throw new ApiError(response.status, detail, problem);
  }
  return response.json() as Promise<T>;
}

function queryString(q: ProductQuery): string {
  const params = new URLSearchParams();
  Object.entries(q).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') params.set(k, String(v));
  });
  const s = params.toString();
  return s ? `?${s}` : '';
}

export const pantryApi = {
  categories: (): Promise<Category[]> => call<Category[]>('/api/pantry/categories'),

  foodTypes: (): Promise<string[]> => call<string[]>('/api/pantry/food-types'),

  search: (q: ProductQuery): Promise<QueryResult<Product>> =>
    call<QueryResult<Product>>(`/api/pantry/products${queryString(q)}`),

  get: (id: string): Promise<Product> => call<Product>(`/api/pantry/products/${encodeURIComponent(id)}`),

  create: (body: ProductCreate): Promise<Product> =>
    call<Product>('/api/pantry/products', { method: 'POST', body: JSON.stringify(body) }),

  update: (id: string, patch: ProductPatch): Promise<Product> =>
    call<Product>(`/api/pantry/products/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    }),

  /** Increment: a purchase of `quantity` packs or items. */
  addStock: (id: string, quantity: number, expectedStockVersion: number): Promise<Product> =>
    call<Product>(`/api/pantry/products/${encodeURIComponent(id)}/stock-additions`, {
      method: 'POST',
      body: JSON.stringify({ quantity, expectedStockVersion }),
    }),

  /** Replacement: the confirmed remaining count. Use one = displayed count minus one. */
  correctStock: (id: string, quantity: number, expectedStockVersion: number): Promise<Product> =>
    call<Product>(`/api/pantry/products/${encodeURIComponent(id)}/stock`, {
      method: 'PATCH',
      body: JSON.stringify({ quantity, expectedStockVersion }),
    }),
};

/** "2 × 500 g", "2 × 10 pcs", "4 items". Never a total across packs and grams. */
export function packDescription(p: Pick<Product, 'packSize' | 'unit'>, quantity: number): string {
  if (p.unit === 'each' || p.packSize == null) {
    return `${quantity} ${quantity === 1 ? 'item' : 'items'}`;
  }
  return `${quantity} × ${p.packSize} ${p.unit}`;
}

/** One pack's description, without a count: "500 g", "10 pcs", "individual item". */
export function packLabel(p: Pick<Product, 'packSize' | 'unit'>): string {
  return p.unit === 'each' || p.packSize == null ? 'individual item' : `${p.packSize} ${p.unit}`;
}

/** Stored food types are lowercase; show them with a capital: "milk" → "Milk". */
export function displayFoodType(foodType?: string | null): string {
  if (!foodType) return '';
  return foodType.charAt(0).toUpperCase() + foodType.slice(1);
}

/** The server's food-type normalisation, mirrored for matching in the browser. */
export function normaliseFoodType(value: string): string {
  return value.trim().replace(/\s+/g, ' ').toLowerCase();
}
