package com.flashsale.common;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKeyAndOperationType(Long userId, String idempotencyKey, String operationType);
}