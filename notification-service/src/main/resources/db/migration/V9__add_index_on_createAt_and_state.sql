CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pe_sms_pending_state
    ON processed_events (created_at) WHERE sms_state = 'PENDING';


CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pe_email_pending_state
    ON processed_events (created_at) WHERE email_state = 'PENDING';


DROP INDEX CONCURRENTLY IF EXISTS idx_pe_sms_pending;


Drop INDEX CONCURRENTLY IF EXISTS idx_pe_email_pending;
