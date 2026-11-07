package com.flashsale.order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findByReservationId(String reservationId);
    List<Order> findByUserId(Long userId);
}