--liquibase formatted sql

--changeset ite4120:pantry-01-category
--comment Category reference table
/* Broad product families, the static reference list of PANTRY.01. Natural key,
   no core.seq_id, no sys columns: the same shape as animals.species. Category
   administration is future scope, so the first version only reads it. */
create table pantry.category (
    code                text not null,
    name                text not null,
    constraint category_pk primary key (code),
    constraint category_code_chk check (char_length(code) between 1 and 40),
    constraint category_name_chk check (char_length(name) between 1 and 100)
);
--

--changeset ite4120:pantry-01-category-seed
--comment Seed the ten agreed categories
/* Required reference data, not demo data: it is applied in every context.
   Convergent through ON CONFLICT DO NOTHING. */
insert into pantry.category (code, name) values
    ('DAIRY_EGGS',              'Dairy & Eggs'),
    ('FRUIT_VEGETABLES',        'Fruit & Vegetables'),
    ('MEAT_POULTRY',            'Meat & Poultry'),
    ('FISH_SEAFOOD',            'Fish & Seafood'),
    ('BREAD_BAKERY',            'Bread & Bakery'),
    ('PASTA_RICE_GRAINS',       'Pasta, Rice & Grains'),
    ('BEANS_PULSES',            'Beans & Pulses'),
    ('COOKING_BAKING',          'Cooking & Baking'),
    ('HERBS_SPICES_CONDIMENTS', 'Herbs, Spices & Condiments'),
    ('SNACKS_DRINKS',           'Snacks & Drinks')
on conflict do nothing;
--
