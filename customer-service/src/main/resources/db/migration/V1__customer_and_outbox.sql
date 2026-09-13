CREATE TABLE customers (
    cif  varchar(10) PRIMARY KEY,
    customer_name varchar(100) NOT NULL,
    phone_number varchar(20) NOT NULL UNIQUE,
    email varchar(254) NOT NULL UNIQUE,  -- need to change the notification column
    password_hash varchar(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMP,
    status varchar(30) CHECK (status IN ('PENDING','ACTIVE','CLOSED','BLOCKED')) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE customer_outbox (
    event_id UUID PRIMARY KEY,
    event_type varchar(30) NOT NULL,
    sent_at TIMESTAMP DEFAULT NULL,
    payload JSONB NOT NULL,
    correlation_id varchar(70),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

create index idx_outbox_unsent on customer_outbox (created_at) WHERE sent_at IS NULL;