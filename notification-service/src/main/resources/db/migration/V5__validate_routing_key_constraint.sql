ALTER TABLE processed_events
    VALIDATE CONSTRAINT ck_processed_events_routing_key_not_null;
