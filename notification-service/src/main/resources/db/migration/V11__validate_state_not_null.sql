ALTER TABLE processed_events
    VALIDATE CONSTRAINT ck_processed_events_sms_state_not_null;

ALTER TABLE processed_events
    VALIDATE CONSTRAINT ck_processed_events_email_state_not_null;

