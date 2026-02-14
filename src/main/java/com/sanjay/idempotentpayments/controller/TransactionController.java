package com.sanjay.idempotentpayments.controller;

import com.sanjay.idempotentpayments.dto.DebitRequest;
import com.sanjay.idempotentpayments.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final IdempotencyService idempotencyService;

    @PostMapping("debit")
    public ResponseEntity<String> debit(@RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody DebitRequest request){

        /**
         * userId is temporarily taken from the request payload.
         * In production, this should be obtained from the security/authentication layer.
         */
        Long userId  = request.getUserId();
        Long senderId = request.getSenderAccountId();
        Long receiverId = request.getReceiverAccountId();
        BigDecimal amount = request.getAmount();

        String response = idempotencyService.handleDebit(userId, idempotencyKey, senderId, receiverId, amount
        );

        return ResponseEntity.ok(response);
    }
}
