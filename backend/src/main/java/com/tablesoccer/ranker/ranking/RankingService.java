package com.tablesoccer.ranker.ranking;

import com.tablesoccer.ranker.match.Match;
import com.tablesoccer.ranker.match.MatchPlayer;
import com.tablesoccer.ranker.match.MatchPlayerRepository;
import com.tablesoccer.ranker.match.MatchRepository;
import com.tablesoccer.ranker.user.User;
import com.tablesoccer.ranker.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    private final RankingStrategyFactory strategyFactory;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final EloSnapshotRepository eloSnapshotRepository;

    public RankingService(RankingStrategyFactory strategyFactory,
                          UserRepository userRepository,
                          MatchRepository matchRepository,
                          MatchPlayerRepository matchPlayerRepository,
                          EloSnapshotRepository eloSnapshotRepository) {
        this.strategyFactory = strategyFactory;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.matchPlayerRepository = matchPlayerRepository;
        this.eloSnapshotRepository = eloSnapshotRepository;
    }

    public List<PlayerRanking> getLongTermRankings() {
        var strategy = strategyFactory.getActiveLongTermStrategy();
        var players = userRepository.findByActiveTrue();
        var matches = matchRepository.findAllByOrderByPlayedAtAsc();
        return strategy.calculateRankings(players, matches);
    }

    public List<PlayerRanking> getMonthlyRankings(YearMonth month) {
        var strategy = strategyFactory.getActiveMonthlyStrategy();
        var players = userRepository.findByActiveTrue();
        Instant from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var matches = matchRepository.findByPlayedAtInRange(from, to);
        return strategy.calculateRankings(players, matches, month);
    }

    /**
     * Preview ELO changes on MatchPlayer entities without persisting.
     * Populates eloBefore, eloAfter, eloChange, teamElo, winProbability.
     */
    public void previewRatings(Match match) {
        var strategy = strategyFactory.getActiveLongTermStrategy();
        // applyMatch populates MatchPlayer ELO fields using current user ratings
        // but since we don't call save, nothing is persisted
        strategy.previewMatch(match);
    }

    @Transactional
    public void updateRatingsAfterMatch(Match match) {
        var strategy = strategyFactory.getActiveLongTermStrategy();
        strategy.updateRatingsAfterMatch(match);

        // Save daily snapshot for all players in this match
        LocalDate matchDay = match.getPlayedAt().atZone(ZoneOffset.UTC).toLocalDate();
        for (MatchPlayer mp : match.getPlayers()) {
            upsertSnapshot(mp.getUser(), matchDay);
        }
    }

    @Transactional
    public void recalculateAllRankings() {
        log.info("Recalculating all rankings...");
        var players = userRepository.findByActiveTrue();
        var matches = matchRepository.findAllByOrderByPlayedAtAsc();

        // Clear all snapshots — will be rebuilt during replay
        eloSnapshotRepository.deleteAll();

        // Reset all ELO to default
        players.forEach(p -> {
            p.setEloRating(1000);
            p.setAttackerElo(1000);
            p.setDefenderElo(1000);
        });
        userRepository.saveAll(players);

        // Replay all matches — create daily snapshots after each match
        var strategy = strategyFactory.getActiveLongTermStrategy();
        for (Match match : matches) {
            strategy.updateRatingsAfterMatch(match);

            // Snapshot all players in this match for the match day
            LocalDate matchDay = match.getPlayedAt().atZone(ZoneOffset.UTC).toLocalDate();
            for (MatchPlayer mp : match.getPlayers()) {
                upsertSnapshot(mp.getUser(), matchDay);
            }
        }

        log.info("Recalculation complete. {} matches replayed.", matches.size());
    }

    public List<PlayerEloTimeline> getEloTimeline(Instant from, Instant to) {
        List<MatchPlayer> allData = matchPlayerRepository.findWithEloDataInPeriod(from, to);

        Map<UUID, PlayerEloTimeline> byUser = new LinkedHashMap<>();
        for (MatchPlayer mp : allData) {
            var user = mp.getUser();
            byUser.computeIfAbsent(user.getId(), k ->
                new PlayerEloTimeline(user.getId(), user.getDisplayName(), user.getAvatarUrl(), new ArrayList<>())
            );
            byUser.get(user.getId()).dataPoints().add(
                new EloDataPoint(mp.getMatch().getId(), mp.getMatch().getPlayedAt(), mp.getEloAfter())
            );
        }

        return new ArrayList<>(byUser.values());
    }

    public List<EloSnapshot> getEloHistory(UUID userId) {
        return eloSnapshotRepository.findByUserIdOrderBySnapshotDateAsc(userId);
    }

    private void upsertSnapshot(User user, LocalDate date) {
        var existing = eloSnapshotRepository.findByUserIdAndSnapshotDate(user.getId(), date);
        if (existing.isPresent()) {
            existing.get().setEloRating(user.getEloRating());
        } else {
            eloSnapshotRepository.save(new EloSnapshot(user, user.getEloRating(), date));
        }
    }

    public record PlayerEloTimeline(UUID userId, String displayName, String avatarUrl, List<EloDataPoint> dataPoints) {}
    public record EloDataPoint(UUID matchId, Instant playedAt, int eloAfter) {}
}
