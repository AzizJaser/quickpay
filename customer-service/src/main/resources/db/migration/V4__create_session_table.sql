CREATE TABLE sessions (
    session_id VARCHAR(40) PRIMARY KEY,
    token_hash VARCHAR(200) NOT NULL UNIQUE,
    cif VARCHAR(10) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ,
    expires_at TIMESTAMP NOT NULL,


    CONSTRAINT FK_customer_session FOREIGN KEY (cif)
    REFERENCES customers(cif)
);




