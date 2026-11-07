package com.flashsale.payment;

import com.flashsale.reservation.Reservation;
import com.flashsale.reservation.ReservationRepository;
import com.flashsale.inventory.InventoryBalanceRepository;
import com.flashsale.messaging.OutboxEvent;
import com.flashsale.messaging.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Value("${flashsale.payment.success-probability:0.9}")
    private double successProbability;

    public PaymentService(PaymentRepository paymentRepository,
                          ReservationRepository reservationRepository,
                          InventoryBalanceRepository inventoryBalanceRepository,
                          OutboxEventRepository outboxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public Payment processPayment(Long userId, String reservationId, String idempotencyKey) {
        return processPayment(userId, reservationId, idempotencyKey, null);
    }

    @Transactional
    public Payment processPayment(Long userId, String reservationId, String idempotencyKey, Double customSuccessRate) {
        var existingOpt = paymentRepository.findByReservationIdAndIdempotencyKey(reservationId, idempotencyKey);
        if (existingOpt.isPresent()) {
            return existingOpt.get();
        }

        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (!res.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        if (!res.getStatus().equals("RESERVED")) {
            throw new RuntimeException("Invalid reservation state");
        }

        if (res.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Reservation expired");
        }

        double rate = customSuccessRate != null ? customSuccessRate : successProbability;
        boolean success = Math.random() < rate;
        
        Payment p = new Payment();
        p.setId(UUID.randomUUID().toString());
        p.setReservationId(reservationId);
        p.setIdempotencyKey(idempotencyKey);
        p.setAttemptNumber(1);
        p.setAmount(res.getUnitPrice().multiply(java.math.BigDecimal.valueOf(res.getQuantity())));
        p.setStatus(success ? "SUCCESS" : "FAILED");
        p.setMockTransactionReference("MOCK-" + UUID.randomUUID().toString().substring(0, 8));
        p.setCreatedAt(OffsetDateTime.now());
        p.setUpdatedAt(OffsetDateTime.now());
        paymentRepository.save(p);

        if (success) {
            reservationRepository.updateStatus(reservationId, "RESERVED", "PAYMENT_SUCCEEDED_PENDING_ORDER");
            inventoryBalanceRepository.commitStock(res.getSaleId(), res.getQuantity());

            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID().toString());
            event.setEventType("ORDER_CONFIRMATION");
            event.setAggregateId(reservationId);
            event.setPayload("{\"reservationId\":\"" + reservationId + "\"}");
            event.setCreatedAt(OffsetDateTime.now());
            outboxEventRepository.save(event);
        }

        return p;
    }
}
