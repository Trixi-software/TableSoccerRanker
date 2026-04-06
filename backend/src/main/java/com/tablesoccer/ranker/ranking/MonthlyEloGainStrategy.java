package com.tablesoccer.ranker.ranking;

import com.tablesoccer.ranker.match.Match;
import com.tablesoccer.ranker.match.MatchPlayer;
import com.tablesoccer.ranker.user.User;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public final class MonthlyEloGainStrategy implements MonthlyRankingStrategy {

    @Override
    public List<PlayerRanking> calculateRankings(List<User> players, List<Match> matches, YearMonth month) {
        // Sum ELO changes from all matches in the month per player
        Map<UUID, Integer> eloGainByPlayer = new HashMap<>();
        Map<UUID, User> playerById = new HashMap<>();
        for (Match m : matches) {
            for (MatchPlayer mp : m.getPlayers()) {
                UUID pid = mp.getUser().getId();
                int change = mp.getEloChange() != null ? mp.getEloChange() : 0;
                eloGainByPlayer.merge(pid, change, Integer::sum);
                playerById.put(pid, mp.getUser());
            }
        }

        var rank = new AtomicInteger(1);
        return eloGainByPlayer.entrySet().stream()
            .map(e -> {
                User u = playerById.get(e.getKey());
                return new PlayerRanking(0, e.getKey(), u.getDisplayName(), u.getAvatarUrl(), e.getValue());
            })
            .sorted(Comparator.comparingDouble(PlayerRanking::score).reversed())
            .map(pr -> new PlayerRanking(rank.getAndIncrement(), pr.userId(), pr.displayName(), pr.avatarUrl(), pr.score()))
            .toList();
    }

    @Override
    public MonthlyAlgorithm algorithm() {
        return MonthlyAlgorithm.MONTHLY_ELO_GAIN;
    }
}
