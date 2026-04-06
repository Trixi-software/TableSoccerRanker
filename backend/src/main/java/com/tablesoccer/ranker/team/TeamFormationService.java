package com.tablesoccer.ranker.team;

import com.tablesoccer.ranker.ranking.PlayerRanking;
import com.tablesoccer.ranker.ranking.RankingService;
import com.tablesoccer.ranker.user.UserDto;
import com.tablesoccer.ranker.user.UserService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TeamFormationService {

    private final RankingService rankingService;
    private final UserService userService;

    public TeamFormationService(RankingService rankingService, UserService userService) {
        this.rankingService = rankingService;
        this.userService = userService;
    }

    public TeamSuggestion suggestTeams(List<UUID> playerIds) {
        if (playerIds.size() != 4) {
            throw new IllegalArgumentException("Exactly 4 players are required");
        }
        if (new HashSet<>(playerIds).size() != 4) {
            throw new IllegalArgumentException("All four players must be different");
        }

        // Get long-term rankings to sort players
        List<PlayerRanking> allRankings = rankingService.getLongTermRankings();

        // Sort the 4 selected players by their ranking score (descending)
        Map<UUID, Double> scoreById = new HashMap<>();
        for (PlayerRanking r : allRankings) {
            scoreById.put(r.userId(), r.score());
        }
        List<UUID> sorted = playerIds.stream()
            .sorted((a, b) -> Double.compare(
                scoreById.getOrDefault(b, 0.0),
                scoreById.getOrDefault(a, 0.0)))
            .toList();

        // 1st + 4th (best + worst) = Yellow, 2nd + 3rd = White
        UserDto best = userService.findById(sorted.get(0));
        UserDto second = userService.findById(sorted.get(1));
        UserDto third = userService.findById(sorted.get(2));
        UserDto worst = userService.findById(sorted.get(3));

        return new TeamSuggestion(best, worst, second, third);
    }
}
