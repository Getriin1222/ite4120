# PANTRY.01 · Pantry specification v3

> **PANTRY.01** · state **Implemented (Stage A); automated checks pass, manual checks pending** · traces from [PANTRY-US-001](../user-stories/PANTRY-US-001.md)
> · source `backend/src/main/java/ee/taltech/ite4120/pantry/`, `backend/src/main/resources/pantry/db/changelog/pantry/`, `frontend/src/pages/pantry/`, `frontend/src/pantryApi.ts`
> · tests `backend/src/test/java/ee/taltech/ite4120/pantry/` (passing, see [Verification status](#verification-status))

## Description

The module maintains a household's groceries in the application's PostgreSQL database. Its primary use is checking stock while shopping and recording purchases and consumption. The module provides a searchable inventory, manual product creation, stock additions, corrections and related-product information across brands.

This is a specification for implementation in the lecturer's Spring Boot and React template. It follows the template's Category/reference-list, business-record and related-record patterns. It has three application tables, foreign keys, database migrations, a documented REST API, a component-library interface and business tests.

### Delivery stages

| **Stage** | **Deliverable**                                                                  | **Status**         |
|-----------|----------------------------------------------------------------------------------|--------------------|
| A         | Manual inventory, three tables, REST API, interface and automated business tests | Implemented        |
| B         | Open Food Facts lookup through an adapter and confirmed local product entry      | Optional extension |
| C         | Camera/barcode capture, receipts, expiry or household accounts                   | Future exploration |

The written 2026 assessment criteria list an X-Road service call (group deliverable item 4). The group reports that the lecturer has since removed that requirement; keep the lecturer's confirmation with the submission. This component therefore has no X-Road adapter. Stage B, if built, is an ordinary REST integration and is not an X-Road call.

### Implemented defaults

| **Decision**         | **Proposed default**                                                           | **Reason**                              |
|----------------------|--------------------------------------------------------------------------------|-----------------------------------------|
| Household scope      | One shared inventory within the course prototype                               | Matches the current use case            |
| Quantity             | Integer packs or individual items                                              | Makes stock actions predictable         |
| Pack size            | Positive size plus g, kg, ml, l or pcs; each for individually counted products | Covers the proposed groceries           |
| Frozen               | Boolean on Product for products bought frozen                                  | Supports the agreed filter              |
| Food type            | Normalised text field on Product                                               | Groups alternatives within three tables |
| Product removal      | Correct stock to zero and retain the catalogue entry                           | Allows easy repeat purchases            |
| Categories           | Ten seeded choices                                                             | Keeps the first interface focused       |
| Simultaneous changes | Version check and database transaction                                         | Avoids silent lost updates              |

### Domain definitions

| **Term**         | **Meaning**                                                           | **Example**             |
|------------------|-----------------------------------------------------------------------|-------------------------|
| Category         | Broad product family                                                  | Dairy & Eggs            |
| Food type        | A user-confirmed label connecting related products                    | milk                    |
| Product          | One specific named and optionally branded packaged or individual item | Brand A whole milk, 1 l |
| Pack size        | Amount contained in one counted pack                                  | 1 l                     |
| Stock quantity   | Count of packs or individual items currently recorded                 | 2 cartons               |
| Stock addition   | A purchase that increases the count                                   | Add 1 carton            |
| Stock correction | Replace the count after consumption or checking                       | Set quantity to 0       |
| Available        | Stock quantity greater than zero                                      | In stock                |
| Out of stock     | A saved product with quantity zero                                    | Still searchable        |
| Related product  | Another product sharing the same stored food type                     | Brand B milk            |

Food type is selected by the household. It does not imply that all milk products have the same ingredients or suitability. The user can choose more specific types such as whole milk, lactose-free milk or oat drink. Automated semantic matching is future work. Both brands appear together only after they have been assigned the same type. The Frozen flag identifies a product bought frozen. Recording some packs of the same product as fresh and others as frozen would require a later stock-state design.

## Data model

### Relationships

- One Category has many Products.
- Each Product belongs to exactly one Category.
- Each Product has exactly one Stock record after a successful create transaction.
- Each Stock record refers to exactly one Product.
- Products with the same food type remain separate records.

The category-to-product and product-to-stock links are foreign keys. Stock has a unique product ID. Product and its initial Stock record are created in one database transaction. No product-delete operation is included in this version. A stock join should therefore always find its companion stock row. A missing row is an integrity error to diagnose, not an assumption that quantity is zero.

### Table `pantry.category`

| **Field** | **Type**          | **Rules**                                     |
|-----------|-------------------|-----------------------------------------------|
| `code`    | text, primary key | Stable seeded code, maximum 40 characters     |
| `name`    | text              | Required display name, maximum 100 characters |

This is a static reference table following the course species-table pattern. The first version offers read access to categories. Category administration is future scope.

| **Code**                | **Name**                   | **Examples**                 |
|-------------------------|----------------------------|------------------------------|
| DAIRY_EGGS              | Dairy & Eggs               | Milk, butter, yoghurt, eggs  |
| FRUIT_VEGETABLES        | Fruit & Vegetables         | Apples, carrots, peas        |
| MEAT_POULTRY            | Meat & Poultry             | Chicken, mince, ham          |
| FISH_SEAFOOD            | Fish & Seafood             | Salmon, prawns, tuna         |
| BREAD_BAKERY            | Bread & Bakery             | Bread, rolls, wraps          |
| PASTA_RICE_GRAINS       | Pasta, Rice & Grains       | Spaghetti, rice, oats        |
| BEANS_PULSES            | Beans & Pulses             | Chickpeas, beans, lentils    |
| COOKING_BAKING          | Cooking & Baking           | Flour, sugar, oil            |
| HERBS_SPICES_CONDIMENTS | Herbs, Spices & Condiments | Cinnamon, mustard, soy sauce |
| SNACKS_DRINKS           | Snacks & Drinks            | Crisps, chocolate, juice     |

### Table `pantry.product`

| **Field**         | **Type**                | **Rules**                                                                              |
|-------------------|-------------------------|----------------------------------------------------------------------------------------|
| `id`              | bigint, primary key     | Generated from `core.seq_id`                                                           |
| `category_code`   | text                    | Required foreign key to category.code                                                  |
| `food_type`       | text                    | Required, maximum 80 characters; trim, collapse whitespace and lowercase before saving |
| `name`            | text                    | Required, 1–255 characters after trimming                                              |
| `brand`           | text, nullable          | Maximum 100 characters; trim; blank becomes null                                       |
| `pack_size`       | numeric(12,3), nullable | Greater than zero for g, kg, ml, l or pcs; integer for pcs; null for each              |
| `unit`            | text                    | Required; one of g, kg, ml, l, pcs, each                                               |
| `barcode`         | text, nullable          | Optional string, 8–14 digits; trim; preserve leading zeroes                            |
| `is_frozen`       | boolean                 | Required, default false; describes products bought frozen                              |
| `sys_status`      | char(1)                 | Platform-managed lifecycle value                                                       |
| `sys_version`     | integer                 | Platform-managed optimistic version                                                    |
| `sys_created_at`  | timestamptz             | Platform-managed                                                                       |
| `sys_created_by`  | text                    | Platform-managed                                                                       |
| `sys_modified_at` | timestamptz             | Platform-managed                                                                       |
| `sys_modified_by` | text                    | Platform-managed                                                                       |

Use the course's metadata trigger through `core.create_table_metadata`. Follow the actual repository's current conventions if its platform schema evolves. Application code does not fill sys metadata values.

Barcode validation here catches format errors. Check-digit validation and conversion between UPC/EAN representations are future enhancements. Local duplicate checks compare the stored digit string exactly. A manually supplied alternate zero-padded representation might need the user to resolve it. An external lookup's returned code is retained as the lookup suggestion, reviewed before saving.

### Table `pantry.stock`

| **Field**         | **Type**            | **Rules**                                         |
|-------------------|---------------------|---------------------------------------------------|
| `id`              | bigint, primary key | Generated from `core.seq_id`                      |
| `product_id`      | bigint              | Required foreign key to product.id; unique        |
| `quantity`        | integer             | Required; 0–1,000,000 inclusive                   |
| `sys_status`      | char(1)             | Platform-managed lifecycle value                  |
| `sys_version`     | integer             | Platform-managed version; exposed as stockVersion |
| `sys_created_at`  | timestamptz         | Platform-managed                                  |
| `sys_created_by`  | text                | Platform-managed                                  |
| `sys_modified_at` | timestamptz         | Platform-managed                                  |
| `sys_modified_by` | text                | Platform-managed                                  |

Quantity represents complete packs or individual items, depending on the product. For pcs, consuming one means consuming one complete pack. The pack size is descriptive. The stock record holds the current balance. The metadata fields identify the latest change and its author. An opened pack remains counted as one until finished. For example, a partly used carton of ten eggs remains one carton. Users who need exact remaining egg counts can record loose eggs as individually counted stock. This model provides no historical purchase ledger or past-date balances.

### Pack-size examples

| **Product**    | **Size** | **Unit** | **Quantity** | **Display** |
|----------------|----------|----------|--------------|-------------|
| Spaghetti      | 500      | g        | 2            | 2 × 500 g   |
| Milk           | 1        | l        | 2            | 2 × 1 l     |
| Carton of eggs | 10       | pcs      | 2            | 2 × 10 pcs  |
| Loose eggs     | null     | each     | 6            | 6 items     |
| Loose apples   | null     | each     | 4            | 4 items     |

For packaged items whose contents are counted, use pcs for pieces per pack. Eggs with pack_size 10, unit pcs and quantity 2 means two cartons of ten eggs. For loose individual eggs, use unit each, null pack_size and quantity 6. This distinction is implemented as specified.

### Uniqueness and normalisation

1.  An active non-null barcode is unique across products.
2.  The normalised identity tuple is unique among active products that have no barcode. The tuple contains lowercase whitespace-normalised name, normalised brand with null treated as empty, exact numeric pack size with null treated as zero for indexing, unit and is_frozen.
3.  Category and food type can be corrected without changing the duplicate identity.
4.  Numeric 500 and 500.0 are equal. 1000 g and 1 kg remain distinct tuples in this first version. The interface shows related products for user review.
5.  Use a unique active-barcode index (`product_barcode_ukey`) and a unique normalised-identity index restricted to active barcode-free products (`product_identity_ukey`), as well as service validation. Concurrent requests must not bypass these constraints: when an index rejects a save, the transaction is already rolled back, and the service then looks up the existing product so the 409 can name it.
6.  Two entries with different barcodes may have identical descriptive fields. Show a possible-duplicate suggestion for review and allow the user to save them separately. Descriptive matches involving a barcode-bearing product are advisory. The barcode-free identity constraint applies only when both records have no barcode.
7.  A failed stock or product save rolls back the whole operation and returns a structured error.

### Migration requirements

- Add a pantry schema and numbered Liquibase changesets for category, product, stock and category seeds.
- Reuse the existing core schema, ID sequence, metadata triggers and database roles.
- Add the pantry changelog to the master changelog.
- Place application indexes on active rows as required by AGENTS.md. Keep the stock-to-product unique constraint unconditional because the first version keeps one companion stock row for the product's lifetime.
- Include field lengths, allowed units, pack-size pairing and quantity bounds as database constraints where appropriate. Implemented as check constraints: `product_food_type_chk` (normalised, at most 80), `product_name_chk`, `product_brand_chk`, `product_unit_chk`, `product_pack_size_chk` (a sized unit needs a non-null positive size, whole for pcs; each has none), `product_barcode_chk` and `stock_quantity_chk` (0 to 1,000,000).
- Keep released changesets unchanged and append future changesets.
- Follow the reference formatted-SQL grammar and run scripts/check-changesets.
- Verify a clean database can be built entirely from migrations. A normal restart preserves user data.
- Demo groceries belong in test fixtures or a deliberate development seed, separate from required reference seeds. Production-like startup begins with categories and no groceries. Implemented: the pantry changelog has no demo changeset, so every start, including the template's `demo` context, begins with the ten categories and an empty inventory.

Changesets: `pantry-schema.sql`, `01-category.sql` (table and seed as two changesets), `02-product.sql`, `03-stock.sql`, listed in `pantry/db/changelog/pantry/changelog.xml` and included once from the master changelog.

## API

### Conventions

Base path is `/api/pantry`. Follow the existing controller, service and repository layers. Use the template's mock authentication and bearer-token handling. JSON uses camelCase. Database bigint IDs are represented as decimal strings in JSON to avoid browser integer precision loss. sys versions remain JSON integers. Opaque ID values and barcodes are strings throughout the frontend.

Dates in metadata use ISO 8601 timestamps. Successful create responses return 201 and Location. Updates return 200 with the current resource. All errors use application/problem+json through the existing handler. Document request bodies, response schemas, parameters and failures in OpenAPI and verify them in Swagger UI.

### Operations

| **Method and path**                              | **Purpose**                                         | **Success**            | **Main failures**                     |
|--------------------------------------------------|-----------------------------------------------------|------------------------|---------------------------------------|
| GET `/api/pantry/categories`                     | Seeded category options                             | 200 array              | Existing auth errors                  |
| GET `/api/pantry/food-types`                     | Sorted distinct food types from active products     | 200 array of strings   | Existing auth errors                  |
| GET `/api/pantry/products`                       | Search/report joined product, category and stock    | 200 `QueryResult` page | 400 invalid query                     |
| GET `/api/pantry/products/{id}`                  | Product details with category and stock             | 200 resource           | 400 non-numeric id, 404 missing       |
| POST `/api/pantry/products`                      | Create product and initial Stock in one transaction | 201 resource, Location | 400 invalid fields, 409 duplicate     |
| PATCH `/api/pantry/products/{id}`                | Edit catalogue fields                               | 200 resource           | 400, 404, 409 stale version/duplicate |
| POST `/api/pantry/products/{id}/stock-additions` | Add a purchase to stored quantity                   | 200 resource           | 400, 404, 409 stale stock version     |
| PATCH `/api/pantry/products/{id}/stock`          | Set the confirmed remaining count                   | 200 resource           | 400, 404, 409 stale stock version     |

The products list is the required reporting operation. It joins all three tables. A separate dashboard endpoint is unnecessary for the stated use case.

### Search contract

| **Query parameter** | **Default** | **Rules**                                                                                            |
|---------------------|-------------|------------------------------------------------------------------------------------------------------|
| `search`            | Empty       | Maximum 200 characters; trimmed; case-insensitive literal substring across name, brand and food_type |
| `categoryCode`      | Omitted     | Exact seeded category code                                                                           |
| `foodType`          | Omitted     | Exact normalised food type for related-product matching                                              |
| `frozen`            | Omitted     | true, false or omitted                                                                               |
| `inStockOnly`       | false       | true excludes zero counts                                                                            |
| `limit`             | 20          | Integer 1–100                                                                                        |
| `offset`            | 0           | Integer at least zero                                                                                |

Search conditions within name/brand/food_type are OR conditions. Other filters are AND conditions. An unknown but syntactically valid category or food type yields an empty page. Escape SQL wildcard characters so a typed percent or underscore is literal. Use parameterised queries. Order by food type, name, brand and ID for stable pagination; the order is fixed and the platform `sort` parameter is not used. The answer is the platform list contract, `{ "data": [...], "meta": { "total": n, ... } }`, as for animals. `meta.total` is the number of matching products before limit/offset. An empty result is 200 with `data` \[\] and `meta.total` 0.

### Product response example

    {
      "id": "101",
      "categoryCode": "DAIRY_EGGS",
      "categoryName": "Dairy & Eggs",
      "foodType": "milk",
      "name": "Whole milk",
      "brand": "Example Dairy A",
      "packSize": 1,
      "unit": "l",
      "barcode": "0123456789012",
      "isFrozen": false,
      "productVersion": 1,
      "stock": {
        "id": "102",
        "quantity": 2,
        "stockVersion": 1,
        "sysModifiedAt": "2026-11-23T18:40:00Z",
        "sysModifiedBy": "superadmin"
      },
      "sysCreatedAt": "2026-11-23T18:40:00Z",
      "sysCreatedBy": "superadmin",
      "sysModifiedAt": "2026-11-23T18:40:00Z",
      "sysModifiedBy": "superadmin"
    }

Example names and barcodes are synthetic format examples. They are not claims that Open Food Facts recognises those products. Metadata can be included using the template's existing resource convention. Frontend logic reads versions returned by the server and never assumes they start at a particular value.

### Create request

    {
      "categoryCode": "DAIRY_EGGS",
      "foodType": "Milk",
      "name": "Whole milk",
      "brand": "Example Dairy A",
      "packSize": 1,
      "unit": "l",
      "barcode": null,
      "isFrozen": false,
      "initialQuantity": 2
    }

Required create fields are categoryCode, foodType, name, unit and initialQuantity. isFrozen defaults to false. brand and barcode default to null. packSize must be supplied for sized units. For each it must be null or omitted. initialQuantity may be zero. A created product with zero stock is useful as a saved catalogue entry.

The response is 201 with Location `/api/pantry/products/{id}` and the complete new resource. Initial stock is created even when its count is zero. No independently callable Stock create endpoint is needed.

### Product edit request

    {
      "expectedProductVersion": 3,
      "brand": "Corrected brand",
      "foodType": "whole milk"
    }

Fields absent from the patch retain their values. Explicit null clears brand or barcode. Explicit null for a required field is invalid. Switching to each clears packSize within the same patch. Switching from each to a sized unit requires a positive packSize in that patch. Editing pack size while quantity is positive asks the user to confirm that existing packs are correctly described. Saving changes no quantity. Versions are checked within the database transaction.

### Stock addition request

    {
      "quantity": 1,
      "expectedStockVersion": 4
    }

quantity is an integer 1–1,000,000. The resulting total must also remain within bounds. The server atomically increments the balance only if expectedStockVersion matches. The response includes the latest joined resource.

### Stock correction request

    {
      "quantity": 0,
      "expectedStockVersion": 5
    }

quantity is the new absolute count, 0–1,000,000. Use one uses this operation with the displayed count minus one and the displayed stock version. At zero the action is disabled. A concurrent update returns 409 and prompts a refresh.

Quantities in both stock requests and `initialQuantity` are accepted as JSON numbers and checked by the service to be whole: 1.5 is a 400, never silently truncated to 1.

### Concurrent saves and uncertain responses

Use transactional version-checked updates, following the template's supported metadata mechanism. Implemented as: in one transaction, lock the row (`select ... for update`), compare its `sys_version` with the expected version (409 if different), then write. Two requests based on the same stock version cannot both mutate the row: the second waits for the lock and then sees the advanced version. A successful change advances the trigger-managed version. A duplicate request carrying the previous version therefore receives 409. Two simultaneous requests to create the same identity are resolved by the unique constraint. The platform trigger leaves a row that did not change untouched, so saving identical values is a no-op that keeps the version.

Disable Save while its request is in flight. After timeout (15 seconds in the client) or connection loss, refresh the product before allowing another submission. Show that the app could not confirm whether the save succeeded. Do not automatically resend with a new version. This preserves consistency without adding a request ledger table.

### Validation and errors

| **Condition**                              | **Status** | **User-facing result**                                        |
|--------------------------------------------|------------|---------------------------------------------------------------|
| Required name or food type missing         | 400        | Field-specific explanation                                    |
| Unknown category on create/edit            | 400        | Select an available category                                  |
| Invalid unit/size pair                     | 400        | Enter a positive size, use an integer for pcs, or choose each |
| Negative, fractional or excessive quantity | 400        | Explain the allowed quantity range                            |
| Barcode contains letters or wrong length   | 400        | Enter 8–14 digits                                             |
| Duplicate barcode or barcode-free identity | 409        | Explain the duplicate and provide existingProductId           |
| Stale product or stock version             | 409        | Data changed; refresh before saving                           |
| Product ID not found                       | 404        | Product could not be found                                    |
| Malformed JSON, non-numeric id or wrongly typed query value | 400 | Explain which value is wrong                         |
| Unexpected database/service error          | 500        | Generic message and server-side diagnostic logging            |

Reuse the repository's error handler and common exceptions. A problem response has the handler's standard type, title, status, detail and instance fields. Add structured field errors or existingProductId using its supported extension mechanism. Implemented in `PantryErrorHandler`, scoped to the pantry controllers: it delegates the commons exceptions to `ApiErrorHandler` and adds `errors: [{ "field", "message" }]` to a field-validation 400 and `existingProductId` to a duplicate 409. Example:

    {
      "type": "about:blank", "title": "Conflict with current state", "status": 409,
      "detail": "barcode 0123456789012 already identifies a saved product; add stock there instead",
      "instance": "/api/pantry/products", "existingProductId": "101"
    }
 Never expose SQL, credentials or stack traces. HTTP status and problem.status agree. Map database uniqueness conflicts to 409 and preserve rollback.

## UI

### Screens and components

| **Screen**          | **Element**                | **Existing component**                | **Behaviour**                                                 |
|---------------------|----------------------------|---------------------------------------|---------------------------------------------------------------|
| `/pantry` inventory | Resource table             | `ResourceList`                        | Joined data, stable pagination and clear empty state          |
| Inventory           | Search                     | `ResourceList` search field           | Searches own API as the user types (250 ms pause), as animals |
| Inventory           | Category selector          | antd `Select` in the filter card      | All categories plus seeded choices; applied with Apply        |
| Inventory           | Frozen filter              | antd `Checkbox` in the filter card    | Checked sends frozen=true; unchecked omits filter             |
| Inventory           | In stock filter            | antd `Checkbox` in the filter card    | Default unchecked, preserves zero-stock visibility            |
| Inventory           | Frozen/Out of stock labels | `AppTag`                              | Text labels visible without colour interpretation             |
| Inventory           | Add product                | `AppButtonPrimary`                    | Opens `/pantry/new`                                           |
| Inventory           | Add stock                  | antd `Button`, `Modal`, `InputNumber` | Positive integer input, current count and confirmation        |
| Inventory           | Use one                    | antd `Button`                         | Disabled at zero or while pending                             |
| Inventory           | Correct quantity           | antd `Modal`, `InputNumber`           | Shows current count and requested replacement                 |
| Inventory           | Detail panel               | `ResourceList.detailView`             | Pack, food type, category, count and barcode of the row       |
| Inventory           | Empty and error states     | antd `Empty`, `Alert`                 | Empty inventory / no matches with Add product; failure with Retry |
| `/pantry/new`       | Create form                | antd `Form` in `AppCard` (the `AnimalCreate` pattern) | Product details and initial quantity together |
| Form                | Name and brand             | antd `Input`                          | Required name, optional brand                                 |
| Form                | Category                   | antd `Select`                         | Required seeded choice                                        |
| Form                | Food type                  | antd `AutoComplete`                   | Existing types plus one explicit new text value               |
| Form                | Pack size                  | antd `InputNumber`                    | Positive, up to three decimals; hidden/cleared for each       |
| Form                | Unit                       | antd `Select`                         | g, kg, ml, l, pcs, each                                       |
| Form                | Barcode                    | antd `Input`                          | Text preserves leading zeroes                                 |
| Form                | Bought frozen              | antd `Checkbox`                       | Optional, default false                                       |
| Form                | Initial quantity           | antd `InputNumber`                    | Integer, default 0; explicit editable count                   |
| Form                | Existing alternatives      | antd `Table`                          | Results by exact food type, sizes and counts                  |
| `/pantry/{id}`      | View/edit                  | `ResourceForm` + `useDataController`  | Product fields, edit and cancel; no delete                    |
| Detail              | Stock card                 | `AppCard`, `FieldItem`, antd `Button` | Count with pack description; Add stock, Use one, Correct quantity |
| Detail              | Metadata sidebar           | `AppCard`, `FieldItem`                | Read-only created/modified/by/version if template supports it |
| All saves           | Feedback                   | Existing notification API             | Validation near fields, conflicts visible, refresh action     |

Use the installed component exports and worked example to confirm wiring. The inventory requests up to 100 products (the API maximum) and lets `ResourceList` page them; when more match, it says so and asks the user to search or filter. Food types are stored in lowercase and displayed with a capital first letter. Keep labels and accessible names on controls. Prefer the existing layout and styles. A responsive table may show secondary fields in the row detail panel on a narrow screen. Product name, quantity and actions must remain accessible.

### Inventory information

Required columns are product name, brand, food type, category, pack description, quantity and Frozen status. The product name links to its detail page. Unit and count should be legible together, for example 2 × 500 g. Products with each show their descriptive name and quantity in items. Products with pcs show quantity × pieces per pack. Avoid inventing totals across incompatible units.

The initial empty state reads Your inventory is empty and provides Add product. A search with no matches reads No matching products and retains Add product. An API failure shows a retry option and must not be displayed as no stock.

### Add new product flow

1.  Open Add product from inventory.
2.  Choose a category and enter/select a food type.
3.  Request related products by exact normalised foodType; display all matching brands and their stock.
4.  Enter product name, optional brand, unit/size, optional barcode and Frozen flag.
5.  Enter initial quantity explicitly.
6.  If a related product is the exact intended item, offer Open existing product to add its stock instead.
7.  Save sends one create request. Success navigates to the created detail page.
8.  Validation keeps entered values. A duplicate conflict offers the existing product. Cancel returns without saving.

A related-product lookup is advisory. Its failure shows that existing products could not be checked. Server duplicate validation remains authoritative. The UI must not claim the user has no related stock when a check failed.

### Add purchase and use groceries

Add stock asks How many packs/items are you adding? and shows the product's pack description. It sends an increment request. Correct quantity asks How many packs/items remain? and sends an absolute value. Use one is a shortcut for decrementing one whole pack/item with version checking. Unsaved form fields never mutate stored stock.

### Different manufacturer scenario

The inventory has Whole milk, Example Dairy A, 1 l, quantity 2, food type milk. A user enters Whole milk, Example Dairy B, 1 l, food type milk. The form shows the A product and its count with the message You already have products under Milk. After confirmation, B is saved with its own quantity 1. Searching milk displays A with 2 and B with 1. Adding stock to B changes only B. Food type does not cause records to merge.

## Optional external product lookup

### Boundary and contract

Open Food Facts provides suggestions for product details through GET `/api/v3/product/{code}`. It is a source of packaged-product information. The application's own database remains authoritative for the household's chosen catalogue records, food types and quantities.

Put the external HTTP call behind a PantryProductLookup interface and mapper, following the template's external-adapter structure. External response records stay separate from local product records. The core inventory endpoints remain usable when this adapter is absent or unavailable.

The course's general external-registry convention discourages copying registry-owned facts into local tables. This design proposes editable user-confirmed catalogue values based on external suggestions. Confirm this interpretation with the lecturer before implementing Stage B. Avoid creating a separate mirror of the external catalogue.

### Proposed extension endpoint

GET `/api/pantry/product-lookup/{barcode}` returns a lookup result with barcode, suggestedName, suggestedBrand, suggestedPackSize, suggestedUnit, sourceUrl and any missing-field notes. Suggestions are nullable. It has no stock side effects. A suggested food type or category requires user confirmation against local values.

| **Outcome**         | **Response**                      | **Interface behaviour**            |
|---------------------|-----------------------------------|------------------------------------|
| Product found       | 200 suggestions                   | Prefill editable fields            |
| Product absent      | 404 problem                       | Continue with manual entry         |
| Invalid barcode     | 400 problem                       | Explain format                     |
| External failure    | 502 problem                       | Manual entry remains available     |
| External timeout    | 504 problem                       | Manual entry remains available     |
| Upstream rate limit | 503 problem, Retry-After if known | Offer manual entry and later retry |

The adapter's failure policy is fail open for core inventory work. Set a bounded request timeout, for example five seconds. Avoid automatic retry storms. Handle HTTP errors and the external body's result/errors fields. Request only needed fields, identify the application with the documented User-Agent and respect current API limits. Show source attribution and review the service's reuse terms before implementation.

The optional barcode can be entered manually. Camera capture is a future input method for that same value. Ordinary barcode data identifies the packaged product. It does not establish the expiry date of a particular pack. Expiry tracking would require a separate batch/date design.

## Business tests

Executable form: `PantryBusinessRulesIT` (BT-01 to BT-12, against the service and a real PostgreSQL through Testcontainers) and `PantryApiIT` (the HTTP contract: 201 and Location, string ids, problem documents with `errors` and `existingProductId`, 400/404/409). They were written before the implementation. The first local run failed for two reasons, both fixed: a page-size check that rejected the platform's default limit, and two tests that reused the same product identity, which the duplicate rule correctly refused. All of them now pass in the repository's Verify checks; see [Verification status](#verification-status). Implement the core tests before business implementation as required by the course workflow. BT-01 and BT-02 are the first minimum pair. The rest cover the specific grouping, stock consistency and fallback behaviour introduced by this design.

### BT-01 · Create product and initial stock together \[AC-02, AC-10\]

Given seeded category DAIRY_EGGS and no cream product, when the user creates Cream, food type cream, 200 ml, initialQuantity 1, then the API returns 201 with Location, one Product and one Stock exist, and the joined report shows the product with quantity 1. A failed create transaction must leave no partial record.

### BT-02 · Report related products across brands \[AC-01, AC-06\]

Given Brand A milk 1 l has quantity 2 and Brand B milk 1 l has quantity 1, both assigned food type milk, when the user searches MILK, then both products appear with their own brands and quantities. Searching with an exact foodType filter returns the same group. No stock is merged or modified by the query.

### BT-03 · Add stock to the correct product \[AC-04, AC-06\]

Given A milk has quantity 2 and B milk has quantity 1, when 3 are added to B with its current stock version, then A remains 2, B becomes 4 and B's stock version changes. The saved values survive reloading the resource.

### BT-04 · Retain zero-stock products \[AC-05\]

Given one carton of milk, when Use one succeeds, then quantity is zero, default search still returns the product marked Out of stock, inStockOnly=true excludes it, and Use one is disabled (in the API a correction below zero is refused; in the interface the button is disabled, checked manually). Adding one carton later restores its count to 1.

### BT-05 · Reject invalid quantities \[AC-03, AC-05\]

Given a product with quantity 2, when the caller adds zero, adds a fractional quantity, sets a negative count or requests a total above 1,000,000, then each request returns 400 with an explanation and quantity remains 2. Verify both service validation and the database bounds where meaningful.

### BT-06 · Prevent duplicate catalogue entries \[AC-07\]

Given a saved product with a barcode (the test uses 4006381333931), when another product uses that same string, then the API returns 409 and the existing product ID. Also verify a normalised identity duplicate between two barcode-free entries returns 409. Verify distinct barcodes can be saved despite matching descriptions. A rejected create leaves no additional stock record.

### BT-07 · Preserve leading barcode zeroes \[AC-07\]

Given barcode 0123456789012, when the product is saved and retrieved, then the value remains exactly that string. A barcode with letters is rejected with 400.

### BT-08 · Combine category and Frozen filtering \[AC-08\]

Given frozen peas under Fruit & Vegetables, fresh peas under the same category and frozen salmon under Fish & Seafood, when category FRUIT_VEGETABLES and frozen=true are selected, then only frozen peas appear.

### BT-09 · Normalise food types \[AC-06, AC-08\]

Given an existing product with food type milk, when a second brand is saved using space-padded MiLk, then both share stored food type milk and the distinct food-type list contains one milk value.

### BT-10 · Prevent lost or duplicate stock changes \[AC-04, AC-09\]

Given quantity 2 at version V, when two stock updates both use V, then exactly one mutation succeeds and the other receives 409. The final quantity reflects one successful change. Resending a successful addition with its original version also receives 409. Run the version checks against a real test database.

### BT-11 · Validate pack-size pairs \[AC-03\]

Given a new product request, when unit g has no size, size is negative, unit pcs has a fractional packSize, or unit each has a numeric packSize, then create returns 400. A valid 500 g request and an each request with null size both succeed.

### BT-12 · Reject stale product edits \[AC-09\]

Given two users read the same product version, when the first changes its brand and the second submits an older version, then the second receives 409 and the first change remains. Product edits leave stock quantity unchanged.

### BT-13 · Lookup failure preserves manual workflow \[AC-11, Stage B\]

Given the product lookup adapter times out or returns no match, when a barcode is looked up, then the endpoint returns the documented failure and makes no local database change. The user can enter required fields manually and create a product successfully.

### BT-14 · Lookup suggestions need confirmation \[AC-11, Stage B\]

Given a lookup returns name and brand but no usable size, when the user opens the suggestions, then the product is unsaved and missing fields remain editable. Save succeeds only after the required size/unit and food type are confirmed.

## Implementation and verification plan

1.  Review this proposed specification with both students. Record confirmed choices and revise the document before implementation. *(Done: v3, 23 November 2026.)*
2.  In a branch, add the story/spec links to docs/src/SUMMARY.md using the repository's established format. *(Done on `feature/pantry-stage-a`.)*
3.  Implement the chosen business tests against the template's Testcontainers/database setup. Establish the failing behaviour first.
4.  Add the schema, tables, indexes, triggers, grants and seeds through migrations.
5.  Build the repository and service rules, then controllers and OpenAPI descriptions.
6.  Verify create, report, stock changes and failures in Swagger UI, including 201 plus Location and structured errors.
7.  Build the inventory and forms with existing @helex/ui and antd components.
8.  Manually execute the storyboard in the browser, including a narrow screen, zero stock, stale update and a duplicate product.
9.  Run backend ./gradlew test, frontend npx tsc -b and the existing build command, plus scripts/check-changesets from the correct directories.
10. Confirm normal restart persistence and migrations from an empty disposable test database. Never reset an inventory containing user data as a routine troubleshooting step.
11. Have the teammate review the PR. Record each person's work, hours and corrections in the shared SM and GT diary.
12. Implement Stage B only after the core flow and its checks pass, if the group chooses that scope.

## Assessment evidence checklist

| **Requirement**                         | **Evidence to produce**                                                                                     |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------|
| User story and matching specification   | These two reviewed Markdown documents, updated with implementation                                          |
| Interoperability (X-Road)               | Requirement removed by the lecturer, as reported by the group; attach the confirmation                     |
| At least three tables and a foreign key | Category, Product, Stock migrations and schema diagram from actual schema                                   |
| Create and reporting operations         | Product create and joined products list, shown in Swagger                                                   |
| Validation and consistent errors        | Specified 400/404/409 cases and problem responses                                                           |
| UI using course library                 | Inventory and form flows wired to own API                                                                   |
| At least two passing business tests     | Test source, named scenarios and actual passing run                                                         |
| Individual contribution                 | Commits by each student, individual contributions and hours in the shared diary, and explanation at defence |
| Peer reviews                            | Written justified reviews of two other groups, prepared separately                                          |
| English defence                         | Demonstrate stock search, create, purchase update and design reasoning                                      |

## Decisions and history

| **Date**         | **Decision**                                              | **Status**                        |
|------------------|-----------------------------------------------------------|-----------------------------------|
| 14 October 2026  | Initial shopping problem and first inventory sketch       | Retrospective scenario for review |
| 28 October 2026  | Explored Open Food Facts and grouping brands by food type | Retrospective scenario for review |
| 7 November 2026  | Reworked the core fields, operations and whole-pack rules | Retrospective scenario for review |
| 23 November 2026 | Reviewed wording, duplicate rules and examples for v3     | Retrospective scenario for review |
| 23 November 2026 | Stage A implemented; specification aligned with the code  | Current revision; automated checks pass |

## Verification status

What was checked when Stage A was implemented, and what is still open. Update this table with actual results as they come in; the dated diary entry holds the details.

| Check | Result on 23 November 2026 |
| --- | --- |
| `scripts/check-changesets` | Clean, 10 changelogs |
| Pantry changesets from empty, local PostgreSQL 16 with a stand-in for the platform `core` schema | Applied; every check constraint and unique index accepted the valid and rejected the invalid examples; one defect found and fixed (a sized unit with no pack size passed the check) |
| Repository SQL (search, literal %/_ escape, identity match, lock and version advance) on that database | As specified |
| Backend type-check against stand-ins for the library signatures the animals code uses | Clean |
| `./gradlew test`, first local run on a Mac (JDK 25, Docker) | Compiled; migrations applied from empty; failures in BT-01, BT-02, BT-04, BT-08, BT-09 and `PantryApiIT`, all traced to the two causes under Business tests and fixed |
| Verify checks on pull request #1 of the group fork, commit 9bb69a8 ([run](https://github.com/Getriin1222/ite4120/actions/runs/45251510380)) | **Passed**: `./gradlew test` (all tests, including BT-01 to BT-12, `PantryApiIT` and the existing animals tests, migrations from empty with the real `core` schema), `npx tsc -b` + `vite build`, `scripts/check-changesets` |
Swagger UI, restart persistence, the storyboard in the browser incl. a narrow screen (26 November 2026, local Mac) | **Passed**: Swagger UI lists all eight Pantry endpoints and `GET /api/pantry/categories` returned 200 with the ten categories; a product and its stock were still there after a backend restart; create, Add stock, Use one, Correct quantity and the existing-products hint worked in the browser; the inventory stays usable in the narrow device view |
