ALTER TABLE processed_events
    ALTER COLUMN sms_state SET NOT NULL;

ALTER TABLE processed_events
    ALTER COLUMN email_state SET NOT NULL;
