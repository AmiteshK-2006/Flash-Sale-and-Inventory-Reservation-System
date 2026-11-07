package com.flashsale.reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
    Optional<Reservation> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    List<Reservation> findByUserId(Long userId);
    List<Reservation> findByStatusAndExpiresAtBefore(String status, OffsetDateTime time);
    
    @Modifying
    @Query("UPDATE Reservation r SET r.status = :newStatus WHERE r.id = :id AND r.status = :oldStatus")
    int updateStatus(@Param("id") String id, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus);
}