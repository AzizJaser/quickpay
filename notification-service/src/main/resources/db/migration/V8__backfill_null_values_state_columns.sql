UPDATE processed_events
SET sms_state = CASE
    WHEN sms_status is TRUE THEN 'SENT'
    WHEN sms_status is FALSE AND attempts >= 5 THEN 'FAILED'
    ELSE 'PENDING'
END
WHERE sms_state IS NULL;


UPDATE processed_events
SET email_state = CASE
    WHEN email_status is TRUE THEN 'SENT'
    WHEN email_status is FALSE AND attempts >= 5 THEN 'FAILED'
    ELSE 'PENDING'
END
WHERE email_state IS NULL;
