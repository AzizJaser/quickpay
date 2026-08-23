ALTER TABLE processed_events
    ADD CONSTRAINT ck_processed_events_routing_key_not_null CHECK (routing_key is not null) NOT VALID;


