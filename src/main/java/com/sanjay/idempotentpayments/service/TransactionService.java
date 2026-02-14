package com.sanjay.idempotentpayments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjay.idempotentpayments.entity.Account;
import com.sanjay.idempotentpayments.entity.IdempotencyRecord;
import com.sanjay.idempotentpayments.entity.Transaction;
import com.sanjay.idempotentpayments.enums.IdempotencyStatus;
import com.sanjay.idempotentpayments.repository.AccountRepository;
import com.sanjay.idempotentpayments.repository.IdempotencyRepository;
import com.sanjay.idempotentpayments.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRepository idempotencyRepository;

    @Transactional
    public String processDebitWithIdempotency(IdempotencyRecord record, Long senderId, Long receiverId, BigDecimal amount, ObjectMapper objectMapper ) {

        Long firstId = Math.min(senderId, receiverId);
        Long secondId = Math.max(senderId, receiverId);

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Account sender = senderId.equals(firstId) ? first : second;
        Account receiver = receiverId.equals(firstId) ? first : second;

        if (sender.getBalance().compareTo(amount) < 0) {
            record.setStatus(IdempotencyStatus.FAILED);
            idempotencyRepository.save(record);
            throw new RuntimeException("Insufficient Balance");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        Transaction transaction = Transaction.builder()
                .senderAccountId(senderId)
                .receiverAccountId(receiverId)
                .amount(amount)
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

        transactionRepository.save(transaction);

        try {
            String responseJson = objectMapper.writeValueAsString(transaction);

            record.setStatus(IdempotencyStatus.SUCCESS);
            record.setResponseBody(responseJson);

            idempotencyRepository.save(record);

            return responseJson;

        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}