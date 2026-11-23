--liquibase formatted sql

--changeset ite4120:pantry-02-product
--comment Product business table
/* One specific named, optionally branded grocery product. The pack size
   describes ONE counted pack; the current balance lives in pantry.stock.
   Values arrive normalised from PantryService; the checks below are the
   database's last line of validation for the same rules:
   - food_type trimmed, whitespace collapsed, lowercase, at most 80 characters;
   - name and brand trimmed; a blank brand is stored as null;
   - unit each has no pack size, every other unit a positive one, pcs a whole one;
   - barcode is TEXT of 8 to 14 digits, so leading zeroes survive. */
create table pantry.product (
    id                  bigint default nextval('core.seq_id') not null,
    category_code       text not null,
    food_type           text not null,
    name                text not null,
    brand               text,
    pack_size           numeric(12,3),
    unit                text not null,
    barcode             text,
    is_frozen           boolean default false not null,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint product_pk primary key (id),
    constraint product_category_fk foreign key (category_code) references pantry.category (code),
    constraint product_food_type_chk check (char_length(food_type) between 1 and 80
        and food_type = lower(regexp_replace(btrim(food_type), '\s+', ' ', 'g'))),
    constraint product_name_chk check (char_length(name) between 1 and 255 and name = btrim(name)),
    constraint product_brand_chk check (brand is null or (char_length(brand) between 1 and 100 and brand = btrim(brand))),
    constraint product_unit_chk check (unit in ('g', 'kg', 'ml', 'l', 'pcs', 'each')),
    constraint product_pack_size_chk check (
        (unit = 'each' and pack_size is null)
        or (unit <> 'each' and pack_size is not null and pack_size > 0 and (unit <> 'pcs' or pack_size = trunc(pack_size)))),
    constraint product_barcode_chk check (barcode is null or barcode ~ '^[0-9]{8,14}$')
);

select core.create_table_metadata('pantry.product');

/* A barcode identifies one active product. */
create unique index product_barcode_ukey
    on pantry.product (barcode) where (sys_status = 'A' and barcode is not null);

/* Barcode-free products: the normalised identity tuple is unique. Numeric
   500 and 500.0 compare equal; 1000 g and 1 kg stay distinct by design. */
create unique index product_identity_ukey
    on pantry.product (
        lower(regexp_replace(name, '\s+', ' ', 'g')),
        lower(regexp_replace(coalesce(brand, ''), '\s+', ' ', 'g')),
        coalesce(pack_size, 0),
        unit,
        is_frozen)
    where (sys_status = 'A' and barcode is null);

create index product_category_idx on pantry.product (category_code) where (sys_status = 'A');

create index product_food_type_idx on pantry.product (food_type) where (sys_status = 'A');
--
