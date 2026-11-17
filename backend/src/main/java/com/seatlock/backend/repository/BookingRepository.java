package com.seatlock.backend.repository;

import com.seatlock.backend.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Integer> {
    List<Booking> findByStatusAndCreatedAtBefore(String status, LocalDateTime time);

    @Query("SELECT b FROM Booking b WHERE b.status = :status AND (b.createdAt IS NULL OR b.createdAt < :time)")
    List<Booking> findByStatusAndCreatedAtBeforeOrCreatedAtIsNull(@Param("status") String status, @Param("time") LocalDateTime time);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Integer id);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    boolean existsBySeatIdAndStatusInAndIdNot(Integer seatId, Collection<String> statuses, Integer id);
}
