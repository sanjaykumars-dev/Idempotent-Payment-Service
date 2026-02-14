package com.sanjay.idempotentpayments.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjay.idempotentpayments.entity.IdempotencyRecord;
import com.sanjay.idempotentpayments.enums.IdempotencyStatus;
import com.sanjay.idempotentpayments.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final TransactionService transactionService;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private static final int RETRY_ATTEMPT = 3;

    public String handleDebit(Long userId, String idempotencyKey, Long senderId, Long receiverId, BigDecimal amount) {

        String requestHash = generateHash(senderId, receiverId, amount);

        IdempotencyRecord record = null;
        boolean inserted = false;
        int attempt = 0;

        while (!inserted) {
            if(attempt++ > RETRY_ATTEMPT){
                throw new RuntimeException("Too many retry attempts...");
            }

            try {
                record = IdempotencyRecord.builder()
                        .userId(userId)
                        .idempotencyKey(idempotencyKey)
                        .requestHash(requestHash)
                        .status(IdempotencyStatus.PROCESSING)
                        .createdAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusHours(24))
                        .build();

                record = idempotencyRepository.save(record);
                inserted = true;

            } catch (DataIntegrityViolationException ex) {

                IdempotencyRecord existing =
                        idempotencyRepository
                                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                                .orElseThrow(() -> new RuntimeException("Unexpected state"));

                if (!(existing.getRequestHash().equals(requestHash))) {
                    throw new RuntimeException("Idempotency key reused with different payload");
                }

                if (existing.getStatus() == IdempotencyStatus.SUCCESS) {
                    return existing.getResponseBody();
                }

                if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                    if (existing.getCreatedAt()
                            .isBefore(LocalDateTime.now().minusMinutes(2))) {
                        idempotencyRepository.delete(existing);
                    } else {
                        throw new RuntimeException("Request still processing");
                    }
                }

                if (existing.getStatus() == IdempotencyStatus.FAILED) {
                    idempotencyRepository.delete(existing);
                }
            }
        }

        return transactionService.processDebitWithIdempotency(record, senderId, receiverId, amount, objectMapper);
    }

    private String generateHash(Long senderId,
                                Long receiverId,
                                BigDecimal amount) {
        try {
            String raw = senderId + ":" + receiverId + ":" + amount.toPlainString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate hash", e);
        }
    }
}