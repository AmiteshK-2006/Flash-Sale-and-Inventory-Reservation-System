package com.flashsale.order;

import com.flashsale.reservation.Reservation;
import com.flashsale.reservation.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ReservationRepository reservationRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, ReservationRepository reservationRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.reservationRepository = reservationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processOrderConfirmation(String payload) throws Exception {
        var root = objectMapper.readTree(payload);
        String reservationId = root.get("reservationId").asText();
        
        var orderOpt = orderRepository.findByReservationId(reservationId);
        if (orderOpt.isPresent()) {
            return; // Already processed
        }

        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        if (!"PAYMENT_SUCCEEDED_PENDING_ORDER".equals(res.getStatus())) {
            return; // Invalid state
        }

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setReservationId(reservationId);
        order.setUserId(res.getUserId());
        order.setProductId(res.getProductId());
        order.setQuantity(res.getQuantity());
        order.setUnitPrice(res.getUnitPrice());
        order.setTotalAmount(res.getUnitPrice().multiply(java.math.BigDecimal.valueOf(res.getQuantity())));
        order.setStatus("CONFIRMED");
        order.setCreatedAt(OffsetDateTime.now());
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.save(order);

        reservationRepository.updateStatus(reservationId, "PAYMENT_SUCCEEDED_PENDING_ORDER", "CONFIRMED");
    }
}
