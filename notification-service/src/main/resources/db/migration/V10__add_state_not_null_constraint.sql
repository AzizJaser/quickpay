ALTER TABLE processed_events
    ADD CONSTRAINT ck_processed_events_sms_state_not_null CHECK (sms_state is not null) NOT VALID;


ALTER TABLE processed_events
    ADD CONSTRAINT ck_processed_events_email_state_not_null CHECK (email_state is not null) NOT VALID;


