package com.sanjay.idempotentpayments.repository;

import com.sanjay.idempotentpayments.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction,Long> {

}
