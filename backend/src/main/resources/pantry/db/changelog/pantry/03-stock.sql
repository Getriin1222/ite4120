--liquibase formatted sql

--changeset ite4120:pantry-03-stock
--comment Stock balance table, one row per product
/* The current count of complete packs or individually counted items. Created
   in the same transaction as its product and kept for the product's whole
   lifetime, so the product_id uniqueness is a plain constraint, not a partial
   index. sys_version is the optimistic version the API exposes as
   stockVersion. No purchase ledger: this is the balance only. */
create table pantry.stock (
    id                  bigint default nextval('core.seq_id') not null,
    product_id          bigint not null,
    quantity            int not null,
    sys_status          char(1),
    sys_version         int,
    sys_created_at      timestamptz,
    sys_created_by      text,
    sys_modified_at     timestamptz,
    sys_modified_by     text,
    constraint stock_pk primary key (id),
    constraint stock_product_fk foreign key (product_id) references pantry.product (id),
    constraint stock_product_ukey unique (product_id),
    constraint stock_quantity_chk check (quantity between 0 and 1000000)
);

select core.create_table_metadata('pantry.stock');
--
