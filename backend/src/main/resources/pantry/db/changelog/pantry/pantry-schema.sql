--liquibase formatted sql

--changeset ite4120:pantry-schema
--comment Create the pantry schema
/* The household grocery inventory component (PANTRY.01). One schema per
   component, the same boundary as the Java package ee.taltech.ite4120.pantry. */
create schema if not exists pantry;
--
