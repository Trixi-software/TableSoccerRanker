package com.tablesoccer.ranker.ranking;

import com.tablesoccer.ranker.TestDataFactory;
import com.tablesoccer.ranker.match.Match;
import com.tablesoccer.ranker.match.MatchPlayer;
import com.tablesoccer.ranker.match.MatchPlayerRepository;
import com.tablesoccer.ranker.match.MatchRepository;
import com.tablesoccer.ranker.user.User;
import com.tablesoccer.ranker.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceMonthlyTest {

    @Mock UserRepository userRepository;
    @Mock MatchRepository matchRepository;
    @Mock MatchPlayerRepository matchPlayerRepository;
    @Mock EloSnapshotRepository eloSnapshotRepository;
    @Mock RankingStrategyFactory strategyFactory;

    RankingService rankingService;
    User alice, bob, charlie, dave;

    @BeforeEach
    void setUp() {
        rankingService = new RankingService(strategyFactory, userRepository, matchRepository,
            matchPlayerRepository, eloSnapshotRepository);
        alice = TestDataFactory.createUser("Alice", 1000);
        bob = TestDataFactory.createUser("Bob", 1000);
        charlie = TestDataFactory.createUser("Charlie", 1000);
        dave = TestDataFactory.createUser("Dave", 1000);
    }

    @Test
    void getMonthlyRankings_usesHalfOpenInterval() {
        YearMonth may2025 = YearMonth.of(2025, 5);
        Instant expectedFrom = may2025.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant expectedTo = may2025.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        when(userRepository.findByActiveTrue()).thenReturn(List.of(alice, bob, charlie, dave));
        when(matchRepository.findByPlayedAtInRange(any(), any())).thenReturn(List.of());
        when(strategyFactory.getActiveMonthlyStrategy()).thenReturn(new MonthlyEloGainStrategy());

        rankingService.getMonthlyRankings(may2025);

        // Verify the half-open interval [May 1, June 1) is used
        verify(matchRepository).findByPlayedAtInRange(eq(expectedFrom), eq(expectedTo));
    }

    @Test
    void getMonthlyRankings_correctBoundariesForMay() {
        YearMonth may2025 = YearMonth.of(2025, 5);
        Instant expectedFrom = Instant.parse("2025-05-01T00:00:00Z");
        Instant expectedTo = Instant.parse("2025-06-01T00:00:00Z");

        when(userRepository.findByActiveTrue()).thenReturn(List.of(alice, bob, charlie, dave));
        when(matchRepository.findByPlayedAtInRange(any(), any())).thenReturn(List.of());
        when(strategyFactory.getActiveMonthlyStrategy()).thenReturn(new MonthlyEloGainStrategy());

        rankingService.getMonthlyRankings(may2025);

        verify(matchRepository).findByPlayedAtInRange(expectedFrom, expectedTo);
    }

    @Test
    void getMonthlyRankings_correctBoundariesForDecember() {
        // December crosses year boundary
        YearMonth dec2025 = YearMonth.of(2025, 12);
        Instant expectedFrom = Instant.parse("2025-12-01T00:00:00Z");
        Instant expectedTo = Instant.parse("2026-01-01T00:00:00Z");

        when(userRepository.findByActiveTrue()).thenReturn(List.of(alice, bob, charlie, dave));
        when(matchRepository.findByPlayedAtInRange(any(), any())).thenReturn(List.of());
        when(strategyFactory.getActiveMonthlyStrategy()).thenReturn(new MonthlyEloGainStrategy());

        rankingService.getMonthlyRankings(dec2025);

        verify(matchRepository).findByPlayedAtInRange(expectedFrom, expectedTo);
    }

    @Test
    void getMonthlyRankings_correctBoundariesForFebruary() {
        // February — shorter month
        YearMonth feb2025 = YearMonth.of(2025, 2);
        Instant expectedFrom = Instant.parse("2025-02-01T00:00:00Z");
        Instant expectedTo = Instant.parse("2025-03-01T00:00:00Z");

        when(userRepository.findByActiveTrue()).thenReturn(List.of(alice, bob, charlie, dave));
        when(matchRepository.findByPlayedAtInRange(any(), any())).thenReturn(List.of());
        when(strategyFactory.getActiveMonthlyStrategy()).thenReturn(new MonthlyEloGainStrategy());

        rankingService.getMonthlyRankings(feb2025);

        verify(matchRepository).findByPlayedAtInRange(expectedFrom, expectedTo);
    }

    @Test
    void getMonthlyRankings_onlyIncludesMatchesFromThatMonth() {
        YearMonth may2025 = YearMonth.of(2025, 5);
        MonthlyEloGainStrategy eloGainStrategy = new MonthlyEloGainStrategy();

        // Create matches: one in May, the result from the repository should only include May matches
        Match mayMatch = TestDataFactory.createMatch(alice, bob, charlie, dave, 10, 5);
        mayMatch.setPlayedAt(Instant.parse("2025-05-15T14:00:00Z"));
        for (MatchPlayer mp : mayMatch.getPlayers()) {
            if (mp.getUser().getId().equals(alice.getId()) || mp.getUser().getId().equals(bob.getId())) {
                mp.setEloChange(20);
            } else {
                mp.setEloChange(-20);
            }
        }

        when(userRepository.findByActiveTrue()).thenReturn(List.of(alice, bob, charlie, dave));
        when(matchRepository.findByPlayedAtInRange(any(), any())).thenReturn(List.of(mayMatch));
        when(strategyFactory.getActiveMonthlyStrategy()).thenReturn(eloGainStrategy);

        var rankings = rankingService.getMonthlyRankings(may2025);

        assertEquals(4, rankings.size());
        var aliceRanking = rankings.stream()
            .filter(r -> r.userId().equals(alice.getId())).findFirst().orElseThrow();
        assertEquals(20, aliceRanking.score());
    }

    @Test
    void getMonthlyRankings_matchAtExactMonthBoundary_excluded() {
        // This test documents that a match at exactly the start of the NEXT month
        // should NOT be included. The repository query uses >= from AND < to.
        // We verify the correct range is passed to the repository.
        YearMonth may2025 = YearMonth.of(2025, 5);

        when(userRepository.findByActiveTrue()).thenReturn(List.of(alice, bob, charlie, dave));
        when(matchRepository.findByPlayedAtInRange(any(), any())).thenReturn(List.of());
        when(strategyFactory.getActiveMonthlyStrategy()).thenReturn(new MonthlyEloGainStrategy());

        rankingService.getMonthlyRankings(may2025);

        // The 'to' should be June 1 00:00:00, and the query uses < (not <=)
        // so a match at exactly 2025-06-01T00:00:00Z will be excluded
        Instant to = Instant.parse("2025-06-01T00:00:00Z");
        verify(matchRepository).findByPlayedAtInRange(any(), eq(to));
    }
}
