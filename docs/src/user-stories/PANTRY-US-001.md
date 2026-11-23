# PANTRY-US-001 · Pantry user story v3

> **PANTRY-US-001** · state **Implemented (Stage A), awaiting verification** · traces to [PANTRY.01](../specifications/PANTRY.01-household-inventory.md)

Check and maintain household grocery stock

**As** a person buying and using groceries for my household, **I want** to see what food we already have, recognise related products across brands, and record purchases and consumption, **so that** I can make informed shopping decisions and maintain a useful view of our stock at home.

## Context

A shopper can remember that milk is on the shopping list while forgetting that there are already two cartons at home. When different household members buy groceries, it is easy to lose track of what is already at home. Different brands and pack sizes can make checking more difficult. The application should show the actual products and quantities available when the shopper searches for goods.

The household keeps one inventory. Categories provide broad organisation, for example Dairy & Eggs. A food type connects related products, for example Milk. Each specific product keeps its own name, brand, pack size and stock quantity. Thus two brands can appear together while retaining separate counts. Frozen identifies products bought frozen and is also available as a filter. The first version applies this flag to the whole product entry.

The core application supports manual entry and remains usable without an external product service. Open Food Facts is a proposed extension that can suggest details after a manually entered barcode. The user confirms the product's food type and details before saving. The lookup itself makes no stock change.

The first version counts complete packs or individual items. For example, two 500 g spaghetti packs have quantity 2. Four loose apples have quantity 4 and counting unit each. An opened pack remains one pack until finished. Use one removes one complete pack or one individually counted item. Partly used contents are approximate in this version. A zero-stock product remains searchable for future purchases.

This proposal assumes one household inventory shared through the existing course application's mock authentication. Team membership refers to the students developing the assignment. It does not create separate household inventories. Household accounts, receipt imports, camera recognition, expiry tracking and fractional remaining contents are future scope decisions.

## Acceptance criteria

### AC-01 · Browse and search household stock

The user can browse saved products and search by product name, brand or food type. Matching ignores letter case and leading or trailing spaces. Each result shows category, food type, product name, brand when recorded, pack size when applicable, Frozen status and quantity. Search results include zero-stock products marked Out of stock. The user can filter to products in stock, a category or frozen products.

### AC-02 · Create a product manually

The user can select an existing category, select or enter a food type, enter a product name and optional brand, specify its pack size when applicable, and save its initial stock quantity. Product creation and stock creation succeed together. A failed request leaves neither a partial product nor a stock record behind.

### AC-03 · Distinguish pack size from stock quantity

A 500 g pack with stock quantity 2 is displayed as 2 × 500 g. An individually counted product with quantity 4 is displayed as 4 items. Adding a 1 kg version of spaghetti creates a separate product from the 500 g version. Searches can show both. The app does not add carton counts and gram amounts together into a single total.

### AC-04 · Add purchased quantities

The user can add a positive whole-number quantity to a saved product. Given two cartons in stock, adding one results in three. The server adds to the current stored value and checks the version the user last saw. A stale request returns a conflict so the user can refresh before submitting again.

### AC-05 · Maintain remaining stock

The user can use one pack or item, or set the count to a corrected non-negative whole number. The count never becomes negative. When the count reaches zero, the product remains available and is marked Out of stock.

### AC-06 · Show related products across manufacturers

Before saving a product, choosing Milk as its food type lets the user check existing milk products. Related results identify the brands, sizes and current quantities. A new brand receives its own product record and quantity after confirmation. Related products are never silently merged. Related results only express membership in a user-assigned food type. They make no dietary or nutritional equivalence claim.

### AC-07 · Prevent unintended duplicate products

An existing barcode identifies its saved product. A second active product with the same barcode is rejected with a clear conflict message and a reference to the existing product. Entries without a barcode reject an exact normalised match against another barcode-free entry using name, brand, pack size, unit and Frozen flag. Other descriptive matches suggest a possible duplicate for the user to review. Different barcodes can identify separate products even when these descriptions match. The interface offers the existing product so the user can add stock there.

### AC-08 · Organise products consistently

The ten agreed broad categories are available at first launch. Food types are selected from existing values or entered when creating or editing a product. Formatting is normalised so Milk, milk and a space-padded Milk use the same food type. Bought frozen is an optional checkbox. Category and Frozen filters can be combined.

### AC-09 · Edit mistakes safely

The user can edit product details and separately correct stock. Invalid changes explain which field needs attention. A concurrent edit cannot silently overwrite a more recent change. Cancelling an unsaved form leaves stored data unchanged.

### AC-10 · Persist and report the saved inventory

Saved products and quantities survive a normal application restart. A stock report joins category, product and stock data and returns the same quantities as the inventory view. An empty inventory is shown with an Add product action. Counts come from the application's own database.

### AC-11 · Offer an external lookup as an extension

When the optional Open Food Facts feature is implemented, the user can enter a barcode and request suggestions. Returned values are editable. Missing products, incomplete data and service failures lead to a usable manual form. The user still chooses a category, food type and quantity. Confirming Save creates or updates local inventory through the app's API.

## Storyboard

| **Step** | **Situation**                         | **User action**                    | **Observable result**                                    |
|----------|---------------------------------------|------------------------------------|----------------------------------------------------------|
| 1        | At the shop, uncertain about milk     | Search milk                        | Existing milk products appear with brand, size and stock |
| 2        | Buying the same milk                  | Open Add stock and enter 1         | Confirmation shows the new quantity                      |
| 3        | Buying another brand                  | Start Add product and choose Milk  | Existing milk is visible before the new product is saved |
| 4        | Product has never been recorded       | Enter details and initial quantity | Product and stock appear together in inventory           |
| 5        | Cooking at home                       | Press Use one                      | Quantity decreases by one                                |
| 6        | Actual count differs                  | Enter corrected quantity           | Stored count matches the confirmed value                 |
| 7        | Looking for frozen alternatives       | Enable Frozen filter               | Frozen items appear within their existing categories     |
| 8        | Optional barcode lookup finds nothing | Continue manually                  | A complete manual product can still be saved             |

## Implemented defaults

These defaults were introduced while preparing the specification and are what Stage A implements. Changing one is a specification change.

- Count whole packs and individual items initially.
- Use a food type field on Product and derive its choices from saved products.
- Start with one shared inventory and the course template's mock authentication.
- Keep categories predefined in the first version.
- Implement manual inventory first and the external lookup as an optional second step (Stage B, not built).

## Document history

| **Date**         | **Change**                                          | **Evidence status**               |
|------------------|-----------------------------------------------------|-----------------------------------|
| 14 October 2026  | Initial shopping problem and first inventory sketch | Retrospective scenario for review |
| 28 October 2026  | Explored product lookup and grouping across brands  | Retrospective scenario for review |
| 7 November 2026  | Reworked the core scope and specification           | Retrospective scenario for review |
| 23 November 2026 | Reviewed wording, examples and prepared v3          | Retrospective scenario for review |
| 23 November 2026 | Stage A implemented against AC-01 to AC-10          | Current revision; tests not yet run |
