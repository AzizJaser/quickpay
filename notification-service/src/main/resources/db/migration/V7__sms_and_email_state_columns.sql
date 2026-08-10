

ALTER TABLE processed_events ADD COLUMN sms_state varchar(10) CHECK ( sms_state IN ('PENDING', 'SENT', 'FAILED'));

ALTER TABLE processed_events ADD COLUMN email_state varchar(10) CHECK ( email_state IN ('PENDING', 'SENT', 'FAILED'));
