package com.tablesoccer.ranker.ranking;

import com.tablesoccer.ranker.TestDataFactory;
import com.tablesoccer.ranker.match.Match;
import com.tablesoccer.ranker.match.MatchPlayer;
import com.tablesoccer.ranker.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MonthlyEloGainStrategyTest {

    MonthlyEloGainStrategy strategy;
    User alice, bob, charlie, dave;

    @BeforeEach
    void setUp() {
        strategy = new MonthlyEloGainStrategy();
        alice = TestDataFactory.createUser("Alice", 1250);
        bob = TestDataFactory.createUser("Bob", 1220);
        charlie = TestDataFactory.createUser("Charlie", 1180);
        dave = TestDataFactory.createUser("Dave", 1150);
    }

    @Test
    void eloGain_sumsEloChangeFromMatches() {
        YearMonth month = YearMonth.of(2026, 4);

        Match match = TestDataFactory.createMatch(alice, bob, charlie, dave, 10, 5);
        // Simulate ELO changes on match players
        for (MatchPlayer mp : match.getPlayers()) {
            if (mp.getUser() == alice || mp.getUser() == bob) {
                mp.setEloChange(15);
            } else {
                mp.setEloChange(-10);
            }
        }

        List<PlayerRanking> rankings = strategy.calculateRankings(
            List.of(alice, bob, charlie, dave), List.of(match), month);

        PlayerRanking aliceR = rankings.stream().filter(r -> r.userId().equals(alice.getId())).findFirst().orElseThrow();
        assertEquals(15, aliceR.score(), "Alice gained +15");

        PlayerRanking charlieR = rankings.stream().filter(r -> r.userId().equals(charlie.getId())).findFirst().orElseThrow();
        assertEquals(-10, charlieR.score(), "Charlie lost -10");
    }

    @Test
    void multipleMatches_sumsAllChanges() {
        YearMonth month = YearMonth.of(2026, 4);

        Match m1 = TestDataFactory.createMatch(alice, bob, charlie, dave, 10, 5);
        for (MatchPlayer mp : m1.getPlayers()) {
            mp.setEloChange(mp.getUser() == alice || mp.getUser() == bob ? 12 : -8);
        }

        Match m2 = TestDataFactory.createMatch(alice, bob, charlie, dave, 7, 10);
        for (MatchPlayer mp : m2.getPlayers()) {
            mp.setEloChange(mp.getUser() == alice || mp.getUser() == bob ? -5 : 9);
        }

        List<PlayerRanking> rankings = strategy.calculateRankings(
            List.of(alice, bob, charlie, dave), List.of(m1, m2), month);

        PlayerRanking aliceR = rankings.stream().filter(r -> r.userId().equals(alice.getId())).findFirst().orElseThrow();
        assertEquals(7, aliceR.score(), "Alice: 12 + (-5) = 7");

        PlayerRanking charlieR = rankings.stream().filter(r -> r.userId().equals(charlie.getId())).findFirst().orElseThrow();
        assertEquals(1, charlieR.score(), "Charlie: -8 + 9 = 1");
    }

    @Test
    void inactivePlayers_excluded() {
        YearMonth month = YearMonth.of(2026, 4);
        var eve = TestDataFactory.createUser("Eve", 1300);

        Match match = TestDataFactory.createMatch(alice, bob, charlie, dave, 10, 5);
        for (MatchPlayer mp : match.getPlayers()) mp.setEloChange(5);

        List<PlayerRanking> rankings = strategy.calculateRankings(
            List.of(alice, bob, charlie, dave, eve), List.of(match), month);

        assertTrue(rankings.stream().noneMatch(r -> r.userId().equals(eve.getId())),
            "Inactive player should not appear");
    }

    @Test
    void rankings_sortedByGainDescending() {
        YearMonth month = YearMonth.of(2026, 4);

        Match match = TestDataFactory.createMatch(alice, bob, charlie, dave, 10, 5);
        for (MatchPlayer mp : match.getPlayers()) {
            if (mp.getUser() == alice) mp.setEloChange(20);
            else if (mp.getUser() == bob) mp.setEloChange(15);
            else if (mp.getUser() == charlie) mp.setEloChange(-5);
            else mp.setEloChange(-10);
        }

        List<PlayerRanking> rankings = strategy.calculateRankings(
            List.of(alice, bob, charlie, dave), List.of(match), month);

        assertEquals(1, rankings.get(0).rank());
        assertTrue(rankings.get(0).score() >= rankings.get(1).score());
        assertTrue(rankings.get(1).score() >= rankings.get(2).score());
    }

    @Test
    void algorithm_returnsMONTHLY_ELO_GAIN() {
        assertEquals(MonthlyAlgorithm.MONTHLY_ELO_GAIN, strategy.algorithm());
    }

    @Test
    void nullEloChange_treatedAsZero() {
        YearMonth month = YearMonth.of(2026, 4);

        Match match = TestDataFactory.createMatch(alice, bob, charlie, dave, 10, 5);
        // Don't set eloChange — stays null

        List<PlayerRanking> rankings = strategy.calculateRankings(
            List.of(alice, bob, charlie, dave), List.of(match), month);

        PlayerRanking aliceR = rankings.stream().filter(r -> r.userId().equals(alice.getId())).findFirst().orElseThrow();
        assertEquals(0, aliceR.score(), "Null eloChange → 0");
    }
}
