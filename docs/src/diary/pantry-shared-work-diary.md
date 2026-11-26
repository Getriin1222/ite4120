# Pantry shared work diary

SM and GT · 14 October–23 November 2026

This retrospective draft uses proposed real-life scenarios and estimated timings for SM and GT to confirm against their notes. The entries describe shopping observations, sketches, walkthroughs and specification reviews. Application test results can be recorded after implementation.

## Session overview

| Date | Focus | Shopping observation | SM | GT |
| --- | --- | --- | --- | --- |
| 14 October 2026 | Shopping problem and first sketch | 15 min | 1 h | 45 min |
| 18 October 2026 | Reworked the inventory sketch | — | 1 h | 1 h 30 min |
| 23 October 2026 | Revised the category structure | 20 min | 1 h 30 min | 1 h |
| 28 October 2026 | Compared brands and explored lookup | 15 min | 1 h | 1 h 30 min |
| 31 October 2026 | Clarified quantities and packs | 20 min | 1 h 15 min | 1 h 15 min |
| 7 November 2026 | Started the specification again from the core flow | — | 2 h | 2 h 15 min |
| 14 November 2026 | Reviewed zero stock and repeat purchases | 15 min | 1 h 30 min | 1 h |
| 19 November 2026 | Walked through overlapping updates | 15 min | 1 h | 1 h 30 min |
| 23 November 2026 | Reviewed documents and prepared v3 | — | 1 h 45 min | 1 h 15 min |
| 23 November 2026 | Implemented Stage A on `feature/pantry-stage-a` | — | 2 h 5 min | 3 h |
| **Total** | | **1 h 40 min** | **14 h** | **15 h** |

Shopping observation is included in SM's session time on the dates shown. The rest of SM's time and all of GT's time cover working on the app concept, walkthroughs, discussion and documentation. Combined time is 24 person-hours. Overlapping work by both participants is counted separately for each person. The totals do not yet include the Stage A implementation entry; add it once SM and GT have confirmed their hours.

## 14 October 2026

### Shopping problem and first sketch

**Time** SM 1 h, including 15 min of shopping observation. GT 45 min.

**Situation** During a small grocery trip, the shopper is unsure whether another bag of spaghetti and a carton of milk are needed. At home, an unopened spaghetti bag turns up behind other groceries. The problem is remembering what is available when making a purchase decision.

**SM contribution** Described the moment of uncertainty in the shop and listed the information that would have helped. Walked through searching for spaghetti, seeing its pack size and quantity, and deciding whether to buy another bag. Sketched a short inventory list that would be readable on a phone.

**GT contribution** Reviewed the example and asked how the information would stay accurate after purchases and cooking. Extended the first sketch with actions for adding groceries and changing the count. Checked that the idea has data that can be stored and retrieved for the assignment.

**Review and decision** Defined the main question as What do we already have, and how much? Kept product entry and stock maintenance in the first flow so the inventory can stay useful between shopping trips.

## 18 October 2026

### Reworked the inventory sketch

**Time** SM 1 h. GT 1 h 30 min.

**Situation** Walked through putting away a mixed shopping bag using the first sketch. The form asks for a storage location for milk, rice, butter and spices. Deciding whether something belongs in Pantry, Fridge or Freezer adds a step to every entry.

**SM contribution** Questioned whether selecting a location helps answer the shopping question. Used spaghetti as an example where the important information is that it is already at home. Reviewed how many actions are needed to add an ordinary purchase.

**GT contribution** Revisited the proposed fields and sketch a simpler model with one household inventory. Compared the revised form with the first sketch and checked which relationships are still needed.

**Review and decision** Started the inventory sketch again with one shared list. Removed storage location from the proposed form. Kept the view centred on product, pack description and quantity. Revisited the database structure after agreeing what each remaining concept represents.

## 23 October 2026

### Revised the category structure

**Time** SM 1 h 30 min, including 20 min of shopping observation. GT 1 h.

**Situation** Used a shopping list containing milk, butter, yoghurt, rice and spices. An early category list gives milk and butter separate categories. When more products are added, the list becomes long and difficult to browse.

**SM contribution** Compared the list with how groceries are usually grouped in the shop and suggested broader categories such as Dairy & Eggs. Sorted the example products into those groups and identified ambiguous cases.

**GT contribution** Checked the proposed structure with vegetables, meat and cooking ingredients. Reviewed whether a newly encountered product can be added beneath an existing category. Discussed how frozen peas would appear alongside other vegetables.

**Review and decision** Reworked the categories into ten broad groups. Put milk, butter and yoghurt underneath Dairy & Eggs. Kept a Frozen flag within products so frozen goods can also be filtered together. In the first version, use this flag for products bought frozen.

## 28 October 2026

### Compared manufacturers and explored product lookup

**Time** SM 1 h, including 15 min of shopping observation. GT 1 h 30 min.

**Situation** The usual milk brand is unavailable, but another manufacturer's milk is on the shelf. An exact product search could miss the milk already at home if the names differ. Explored whether a barcode-based product lookup would help with entry.

**SM contribution** Raised the alternative-brand example and described the message needed in the shop. Reviewed a proposed message showing the milk already recorded, its brand, size and quantity before adding another product.

**GT contribution** Examined the role of an external lookup and compare it with the app's own stored data. Worked through an example with two brands under a common food type, Milk, and separate quantities for each product.

**Review and decision** Added a food type field for user-confirmed grouping. Kept brands as separate product records. Treated Open Food Facts as an optional way to suggest product details. A lookup leaves stock unchanged until the user confirms a save through the app.

## 31 October 2026

### Clarified quantities and pack sizes

**Time** SM 1 h 15 min, including 20 min of shopping observation. GT 1 h 15 min.

**Situation** Walked through two 500 g spaghetti bags, two cartons of ten eggs and four loose apples. A bare quantity of 2 is unclear without knowing what is being counted. A second example asks what the app should display after using one egg from a carton.

**SM contribution** Reviewed the labels from a shopper's perspective. Asked whether 2 means bags, cartons or individual eggs. Suggested showing the stock count beside a pack description, such as 2 × 500 g or 2 × 10 pcs.

**GT contribution** Mapped the examples to separate pack-size and stock-quantity fields. Checked how individually counted items use each, while packaged goods keep their contents per pack. Compared the additional data needed to track partly used contents.

**Review and decision** Counted complete packs or individual items for the first version. An opened pack stays counted as one until finished. Recorded loose eggs individually when exact egg counts are needed. Made the limitation visible in the specification so the app gives a clear approximate overview.

## 7 November 2026

### Started again from the core shopping flow

**Time** SM 2 h. GT 2 h 15 min.

**Situation** Reviewed the accumulated ideas together. Camera recognition, expiry dates, product lookup and exact consumption tracking make the first version difficult to explain. Several draft fields support features that are still uncertain.

**SM contribution** Returned to the original shopping example and rewrite the flow around searching stock, adding purchases and correcting counts. Reviewed which information is essential on the first screen and which actions repeat during everyday use.

**GT contribution** Mapped the revised flow to Category, Product and Stock. Reviewed the relationships and the API operations required to create a product, report the inventory and change a quantity. Checked where validation and example business tests fit.

**Review and decision** Started the detailed specification again from the manual inventory flow. Retained the useful decisions about categories, brands, food types and pack descriptions. Moved external lookup to an optional stage and leave camera input and expiry as future exploration. Used the specification as the basis for implementation.

## 14 November 2026

### Reviewed zero stock and repeat purchases

**Time** SM 1 h 30 min, including 15 min of shopping observation. GT 1 h.

**Situation** Walked through using the last milk carton and buying the same milk again. In one proposed flow, the product disappears entirely when the count reaches zero. The next purchase would require entering the product details again.

**SM contribution** Traced the repeat-purchase journey and described the extra work caused by losing the product entry. Reviewed an Out of stock result that still offers Add stock. Checked how this would look during a short shopping trip.

**GT contribution** Reviewed the distinction between retaining a product and changing its stock balance. Checked the rules for zero, adding a positive quantity and preventing a negative count. Turned these into proposed business-test steps.

**Review and decision** Retained products at zero and show Out of stock. Let the user add a later purchase to the same product. Kept Add stock as an increment and Correct quantity as a replacement count. Used one is unavailable when the count is already zero.

## 19 November 2026

### Walked through overlapping household updates

**Time** SM 1 h, including 15 min of shopping observation. GT 1 h 30 min.

**Situation** Simulated one household member recording a purchase from the shop while another corrects the inventory after cooking. Both begin from the same displayed count. Also considered pressing Add twice when a connection is slow.

**SM contribution** Reviewed the messages needed when the displayed count has changed. Considered how to show an uncertain save without encouraging another immediate purchase entry. Checked whether the user can see the refreshed balance and understand what to do next.

**GT contribution** Stepped through the two updates on paper and identified how a later request could overwrite a newer value. Proposed a stock-version check and wrote the expected conflict behaviour. Reviewed the role of disabling Save while a request is in progress.

**Review and decision** Specified a conflict response for a stale update and required a refresh before another save. Disabled a pending Save button. After an uncertain response, refresh before allowing a further submission. These remain scenarios to verify during implementation.

## 23 November 2026

### Reviewed the documents and prepared v3

**Time** SM 1 h 45 min. GT 1 h 15 min.

**Situation** Reviewed the Word drafts against the everyday scenarios. The table describes cartons of ten eggs while the paragraph underneath describes six. Brand wording is unclear and the two documents show different historical dates.

**SM contribution** Reviewed and edited the user-facing wording, the household-shopping context and the brand description. Requested a combined work diary using SM and GT. Checked that the revised package retains the sections kept in the edited Word files.

**GT contribution** Cross-checked the product examples against the fields, API behaviour and business tests. Reviewed duplicate handling so distinct barcodes can represent separate products with similar descriptions. Checked the relationship between food type and brand-specific stock.

**Review and decision** Aligned the egg example to ten, use brand when recorded, and used v3 document titles. Brought the document histories into the same proposed chronology. Consolidated the diary into one record with individual time totals. Kept deleted sections out of the revised package.

## 23 November 2026 · Stage A implementation

### Implemented Stage A from the v3 specification

**Time** SM to confirm. GT to confirm.

**Contributors** To be completed by SM and GT: who reviewed which part, what was corrected, and the commits made under each name. The code was drafted with an AI development agent working from the v3 user story and specification; both students remain responsible for explaining it at the defence.

**Situation** The v3 story and specification were treated as the agreed Stage A requirements and implemented in the course template, following the animals example layer by layer. Stage B (Open Food Facts) stays future work. The group reports that the lecturer has removed the X-Road requirement, so no X-Road adapter was added.

**Completed work**

- Database: schema `pantry` with `category` (ten seeded categories), `product` and `stock`, two foreign keys, check constraints for every field rule in the specification, a unique active-barcode index and a unique normalised-identity index for barcode-free products. No demo groceries, so the app starts with an empty inventory.
- API under `/api/pantry`: categories, food types, the joined inventory report with search and filters, product details, create (product and initial stock in one transaction, 201 with Location), product edit (PATCH with product version), stock addition and stock correction (both version-checked). Errors are problem documents, with field errors on 400 and the existing product id on a duplicate 409. Documented with OpenAPI annotations for Swagger UI.
- Interface: `/pantry` inventory with search, category, Frozen and In stock filters, Out of stock and Frozen labels, Add stock, Use one and Correct quantity; `/pantry/new` with existing products under the chosen food type shown before saving; `/pantry/{id}` record page with edit, stock card and metadata.
- Tests, written before the implementation: `PantryBusinessRulesIT` for BT-01 to BT-12 and `PantryApiIT` for the HTTP contract.
- Documents moved into the repository book (`docs/src/`), linked from `SUMMARY.md` and aligned with the implementation.

**Findings**

- A defect caught by exercising the migrations: the pack-size check accepted unit `g` with no pack size, because a comparison with NULL does not fail a check constraint. The check now requires a non-null size for every sized unit. Fixed before anything was released.
- A JSON integer field would accept 1.5 and silently turn it into 1. Quantities are therefore read as decimals and checked to be whole, so a fractional quantity is refused with a 400, as BT-05 requires.
- The platform trigger advances the version on every real change and leaves an unchanged row untouched. Stock and product writes therefore lock the row, compare the version the user saw, and only then write; saving identical values keeps the version.
- Routine choices taken from the template and written back into the specification: the list answer uses the platform's `data` and `meta.total` instead of `items` and `total`; the inventory search uses the list component's own search field as the user types, not a separate Search button; the inventory loads up to 100 products and says so when more match.

**Verification on this date**

- Ran: the changeset grammar check (clean); the pantry changesets from an empty local PostgreSQL 16 with a stand-in for the platform `core` schema (applied; all constraints and unique indexes behaved as specified after the fix above); the repository's SQL statements on that database; a compile check of the backend code against stand-ins for the library signatures used by the animals example.
- First local `./gradlew test` on a Mac, after installing JDK 25, Node.js and Python and setting up the GitHub Packages token: the code compiled and the migrations ran from empty, but BT-01, BT-02, BT-04, BT-08, BT-09 and the API contract test failed. Two causes: the service range-checked the platform's default page size, which is outside 1 to 100, so every search without an explicit limit was refused; and several tests reused the product "Whole milk, Brand A, 1 l", which the duplicate rule correctly refused because tests share one database. Fixed by checking limit and offset only when the caller sends them, and by giving each test its own product names.
- Verify checks on pull request #1 in the group's fork (`Getriin1222/ite4120`), commit 9bb69a8, [run](https://github.com/Getriin1222/ite4120/actions/runs/45251510380): backend tests, frontend typecheck and build, and the changeset check all passed.
- Still to do by hand: Swagger UI, restart persistence and the browser storyboard, including a narrow window.

**Review and decision** Pull request #1 is open in the group fork with all automated checks green. Before merging, SM and GT review it, run the manual checks above, and complete the contributor and time fields of this entry.

## Time summary

| Contributor | Shopping observation | Other project work | Total |
| --- | --- | --- | --- |
| SM | 1 h 40 min | 10 h 20 min | 12 h |
| GT | — | 12 h | 12 h |
| **Combined** | **1 h 40 min** | **22 h 20 min** | **24 h** |
