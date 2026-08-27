ALTER TABLE bill
DROP CONSTRAINT bill_status_check;

ALTER TABLE bill
    ADD CONSTRAINT ck_bill_status
        CHECK (status IN ('Pending', 'Reserved', 'Rejected', 'Paid', 'Failed'));