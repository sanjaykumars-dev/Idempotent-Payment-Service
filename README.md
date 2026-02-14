# Idempotent Payment API
A crash-safe and concurrency-safe payment API built using Spring Boot + PostgreSQL.
This project demonstrates how to correctly implement idempotency for financial transactions using database-level guarantees and atomic transactions.

# Problem
When clients retry requests (due to network issues, crashes, or double-clicks), financial APIs must ensure:
- Money is debited only once
- Transaction is recorded once
- Same response is returned for the same idempotency key

# Key Concepts Implemented
- Database-level uniqueness constraint
 > UNIQUE (user_id, idempotency_key)
- Pessimistic row locking
> SELECT ... FOR UPDATE
- Atomic transaction boundary
> Debit + idempotency update commit together
- JSONB response replay
- Deadlock prevention via ordered locking

# Core Table
```SQL
CREATE TABLE idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    response_body JSONB,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_idempotency_key UNIQUE (user_id, idempotency_key)
);
```

# API
> ### POST  /api/v1/transaction/debit
### Header : 
```CODE
Idempotent-Key : <key>
```
### Body :
```JSON
{
  "userId": 1,
  "senderAccountId": 1,
  "receiverAccountId": 2,
  "amount": 300
}
```

# Guarantees

- Same key + same payload → same response
- Same key + different payload → rejected
- Concurrent same key → only one debit
- Crash before commit → safe rollback

# Run 
- Create PostgreSQL database
- Configure application.yml

Run:
```CODE
mvn spring-boot:run
```

# What This Demonstrates
This project focuses on:
- ACID guarantees
- Concurrency control
- Crash consistency
- Defensive backend design

Idempotency is not just checking a key — it is enforcing correctness at the database level.
