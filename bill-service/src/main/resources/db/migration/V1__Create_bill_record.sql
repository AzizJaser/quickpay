-- Pending -> record created but didn't handle the amount captured yet.
-- Reserved -> money is in temp account.
-- Rejected -> rejected either from biller or wallet due to insufficient balance.
-- Paid -> bill paid

CREATE table bill (
     payment_id varchar(36) primary key,
     bill_reference varchar(36) NOT NULL,
     wallet_number varchar(12) NOT NULL ,
     amount BIGINT check ( amount > 0) NOT NULL,
     status varchar(15) default 'Pending' NOT NULL CHECK (status IN ('Pending', 'Reserved', 'Rejected', 'Paid')),
     entry_id varchar(36),
     idempotency_key varchar(36) not null unique,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP not null
);