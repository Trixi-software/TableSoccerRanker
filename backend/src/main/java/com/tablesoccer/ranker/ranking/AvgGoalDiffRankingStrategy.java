package com.tablesoccer.ranker.ranking;

import com.tablesoccer.ranker.match.Match;
import com.tablesoccer.ranker.match.MatchPlayer;
import com.tablesoccer.ranker.user.User;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public final class AvgGoalDiffRankingStrategy implements LongTermRankingStrategy {

    @Override
    public List<PlayerRanking> calculateRankings(List<User> players, List<Match> matches) {
        Map<UUID, List<Integer>> goalDiffs = new HashMap<>();
        Map<UUID, User> playersById = new HashMap<>();
        players.forEach(p -> {
            goalDiffs.put(p.getId(), new ArrayList<>());
            playersById.put(p.getId(), p);
        });

        for (Match match : matches) {
            for (MatchPlayer mp : match.getPlayers()) {
                int scored = match.scoreFor(mp.getTeamColor());
                int conceded = match.scoreAgainst(mp.getTeamColor());
                goalDiffs.computeIfAbsent(mp.getUser().getId(), k -> new ArrayList<>())
                    .add(scored - conceded);
            }
        }

        var rank = new AtomicInteger(1);
        return goalDiffs.entrySet().stream()
            .map(entry -> {
                double avg = entry.getValue().isEmpty() ? 0 :
                    entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
                User user = playersById.get(entry.getKey());
                return new PlayerRanking(0, user.getId(), user.getDisplayName(), user.getAvatarUrl(), avg);
            })
            .sorted(Comparator.comparingDouble(PlayerRanking::score).reversed())
            .map(pr -> new PlayerRanking(rank.getAndIncrement(), pr.userId(), pr.displayName(), pr.avatarUrl(), pr.score()))
            .toList();
    }

    @Override
    public void updateRatingsAfterMatch(Match match) {
        // No persistent rating to update — computed on the fly
    }

    @Override
    public LongTermAlgorithm algorithm() {
        return LongTermAlgorithm.AVG_GOAL_DIFF;
    }
}
