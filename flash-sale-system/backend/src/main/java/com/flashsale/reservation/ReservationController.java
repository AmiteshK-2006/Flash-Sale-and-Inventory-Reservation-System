package com.flashsale.reservation;

import com.flashsale.config.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/sales/{saleId}/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<?> reserve(@PathVariable Long saleId,
                                     @RequestBody Map<String, Integer> request,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key header is required"));
        }
        int quantity = request.getOrDefault("quantity", 1);
        try {
            Long userId = (userDetails != null) ? userDetails.getId() : 1L;
            Reservation res = reservationService.reserve(userId, saleId, quantity, idempotencyKey);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
