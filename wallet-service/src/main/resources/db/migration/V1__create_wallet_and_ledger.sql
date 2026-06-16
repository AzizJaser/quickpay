CREATE TYPE wallet_status AS ENUM ('Pending', 'Active', 'Suspended', 'Closed');
-- CREATE TYPE entry_status AS ENUM ('Success', 'Failed', 'Pending', 'Reversed');
CREATE table wallet (
    walletNumber varchar(12) primary key,
    cif varchar(10) NOT NULL ,
    walletName VARCHAR(50) NOT NULL ,
    balance BIGINT check ( Balance >= 0) not null,
    status wallet_status default 'Pending' NOT NULL,
    CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP not null
);

create table ledger(
    entryId varchar(20) primary key,
    debitedWalletNumber varchar(12) not null ,
    creditedWalletNumber varchar(12) not null check ( debitedWalletNumber <> creditedWalletNumber),
    debitedAmount BIGINT check ( debitedAmount < 0 ) not null ,
    creditedAmount BIGINT check ( creditedAmount > 0 ) not null check ( debitedAmount + creditedAmount = 0 ),
    idempotency_key varchar(36) not null unique,
    CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP not null ,

        --Foreign keys
    CONSTRAINT FK_Debited_Wallet FOREIGN KEY (debitedWalletNumber)
        REFERENCES wallet(walletNumber),

    CONSTRAINT FK_Credited_Wallet FOREIGN KEY (creditedWalletNumber)
        REFERENCES wallet(walletNumber)
);