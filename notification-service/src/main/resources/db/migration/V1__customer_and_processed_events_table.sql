create table customers(
    cif varchar(10) NOT NULL PRIMARY KEY,
    phone_number varchar(20) NOT NULL,
    email varchar(50) NOT NULL,
    customer_name varchar(100) NOT NULL
);

create table processed_events(
    message_id varchar(50) NOT NULL PRIMARY KEY,
    sms_status boolean NOT NULL DEFAULT FALSE,
    sms_sent_at TIMESTAMP DEFAULT NULL,
    email_sent_at TIMESTAMP DEFAULT NULL,
    email_status boolean NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload JSONB not null,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pe_sms_pending   ON processed_events (created_at) WHERE sms_status = false;
CREATE INDEX idx_pe_email_pending ON processed_events (created_at) WHERE email_status = false;