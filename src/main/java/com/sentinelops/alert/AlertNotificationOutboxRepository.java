package com.sentinelops.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertNotificationOutboxRepository extends JpaRepository<AlertNotificationOutbox, Long> {
    Optional<AlertNotificationOutbox> findFirstByRule_IdOrderByCreatedAtDescIdDesc(Long ruleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AlertNotificationOutbox n set n.status = :failed,
              n.leaseToken = null, n.leaseExpiresAt = null, n.lastError = :reason
            where n.attemptCount >= :maxAttempts and
              ((n.status = :pending and n.nextAttemptAt <= :now) or
               (n.status = :sending and n.leaseExpiresAt <= :now))
            """)
    int failExhausted(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts,
                      @Param("pending") AlertNotificationStatus pending,
                      @Param("sending") AlertNotificationStatus sending,
                      @Param("failed") AlertNotificationStatus failed,
                      @Param("reason") String reason);

    @Query("""
            select n.id from AlertNotificationOutbox n
            where n.attemptCount < :maxAttempts and
              ((n.status = :pending and n.nextAttemptAt <= :now) or
               (n.status = :sending and n.leaseExpiresAt <= :now))
            order by n.nextAttemptAt asc, n.id asc
            """)
    List<Long> findDueIds(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts,
                          @Param("pending") AlertNotificationStatus pending,
                          @Param("sending") AlertNotificationStatus sending,
                          Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AlertNotificationOutbox n set n.status = :sending,
              n.attemptCount = n.attemptCount + 1,
              n.leaseToken = :token, n.leaseExpiresAt = :expires
            where n.id = :id and n.attemptCount < :maxAttempts and
              ((n.status = :pending and n.nextAttemptAt <= :now) or
               (n.status = :sending and n.leaseExpiresAt <= :now))
            """)
    int claim(@Param("id") Long id, @Param("now") Instant now,
              @Param("expires") Instant expires, @Param("token") String token,
              @Param("maxAttempts") int maxAttempts,
              @Param("pending") AlertNotificationStatus pending,
              @Param("sending") AlertNotificationStatus sending);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AlertNotificationOutbox n set n.status = :sent,
              n.leaseToken = null, n.leaseExpiresAt = null,
              n.deliveredAt = :deliveredAt, n.lastError = null
            where n.id = :id and n.status = :sending and n.leaseToken = :token
            """)
    int markSent(@Param("id") Long id, @Param("token") String token,
                 @Param("sent") AlertNotificationStatus sent,
                 @Param("sending") AlertNotificationStatus sending,
                 @Param("deliveredAt") Instant deliveredAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AlertNotificationOutbox n set n.status = :newStatus,
              n.leaseToken = null, n.leaseExpiresAt = null,
              n.nextAttemptAt = :nextAttempt, n.lastError = :reason
            where n.id = :id and n.status = :sending and n.leaseToken = :token
            """)
    int markFailure(@Param("id") Long id, @Param("token") String token,
                    @Param("sending") AlertNotificationStatus sending,
                    @Param("newStatus") AlertNotificationStatus newStatus,
                    @Param("nextAttempt") Instant nextAttempt,
                    @Param("reason") String reason);
    /** Single bounded aggregate query: one row per delivery state, including oldest timestamp. */
    @Query("select n.status, count(n), min(n.createdAt) from AlertNotificationOutbox n group by n.status")
    List<Object[]> summarizeByStatus();

    /** Bounded result set (one row per enum value) for monitoring without loading entities. */
    @Query("select n.status, count(n) from AlertNotificationOutbox n group by n.status")
    List<Object[]> countGroupedByStatus();

    @Query("select min(n.createdAt) from AlertNotificationOutbox n where n.status = :status")
    Instant oldestCreatedAtForStatus(@Param("status") AlertNotificationStatus status);

    long countByRule_Id(Long ruleId);
    boolean existsByOccurrence_Id(Long occurrenceId);
}
