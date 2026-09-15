ALTER TABLE customers ADD COLUMN national_id varchar(10) NOT NULL;
ALTER TABLE customers ADD CONSTRAINT ck_national_id_format CHECK (national_id ~ '^[12][0-9]{9}$');
ALTER TABLE customers ADD CONSTRAINT ck_cif_format CHECK (cif ~ '^0[0-9]{9}$');
ALTER TABLE customers DROP CONSTRAINT  customers_email_key;
ALTER TABLE customers DROP CONSTRAINT  customers_phone_number_key;

create unique index idx_national_id_live_record on customers (national_id) where status <> 'CLOSED';
create unique index idx_email_live_record on customers (email) where status <> 'CLOSED';
create unique index idx_phone_number_live_record on customers (phone_number) where status <> 'CLOSED';