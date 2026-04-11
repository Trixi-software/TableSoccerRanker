package com.tablesoccer.ranker.match;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    Page<Match> findAllByOrderByPlayedAtDesc(Pageable pageable);

    @Query("SELECT m FROM Match m JOIN m.players mp WHERE mp.user.id = :userId ORDER BY m.playedAt DESC")
    Page<Match> findByPlayerId(UUID userId, Pageable pageable);

    @Query("SELECT m FROM Match m JOIN m.players mp WHERE mp.user.id = :userId ORDER BY m.playedAt ASC")
    List<Match> findAllByPlayerId(UUID userId);

    List<Match> findAllByOrderByPlayedAtAsc();

    List<Match> findByPlayedAtBetweenOrderByPlayedAtAsc(Instant from, Instant to);

    @Query("SELECT m FROM Match m WHERE m.playedAt >= :from AND m.playedAt < :to ORDER BY m.playedAt ASC")
    List<Match> findByPlayedAtInRange(Instant from, Instant to);

    @Query("SELECT m FROM Match m JOIN m.players mp WHERE mp.user.id = :userId " +
           "AND m.playedAt >= :from AND m.playedAt < :to ORDER BY m.playedAt ASC")
    List<Match> findByPlayerIdAndPeriod(UUID userId, Instant from, Instant to);
}
