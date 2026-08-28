create table outbox_notification (
    event_id UUID primary key unique ,
    event_type varchar(30) NOT NULL ,
    sent_at TIMESTAMP DEFAULT NULL,
    payload JSONB NOT NULL,
    correlation_id varchar(70) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP not null
);

create index idx_outbox_unsent on outbox_notification (created_at) where sent_at is null;