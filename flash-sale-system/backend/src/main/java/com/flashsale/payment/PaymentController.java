package com.flashsale.payment;

import com.flashsale.config.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations/{reservationId}/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<?> processPayment(@PathVariable String reservationId,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key header is required"));
        }
        try {
            Payment payment = paymentService.processPayment(userDetails.getId(), reservationId, idempotencyKey);
            return ResponseEntity.ok(payment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
