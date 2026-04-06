package com.tablesoccer.ranker.stats;

import com.tablesoccer.ranker.match.*;
import com.tablesoccer.ranker.user.User;
import com.tablesoccer.ranker.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatsService {

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final UserRepository userRepository;

    public StatsService(MatchRepository matchRepository, MatchPlayerRepository matchPlayerRepository,
                        UserRepository userRepository) {
        this.matchRepository = matchRepository;
        this.matchPlayerRepository = matchPlayerRepository;
        this.userRepository = userRepository;
    }

    public CompanyStats getCompanyStats() {
        List<Match> allMatches = matchRepository.findAllByOrderByPlayedAtAsc();
        Map<UUID, User> usersById = userRepository.findByActiveTrue().stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        long totalMatches = allMatches.size();
        long totalGoals = allMatches.stream()
            .mapToLong(m -> m.getYellowScore() + m.getWhiteScore()).sum();

        // Most active player
        Map<UUID, Long> matchCounts = new HashMap<>();
        Map<UUID, Long> goalCounts = new HashMap<>();
        for (Match m : allMatches) {
            for (MatchPlayer mp : m.getPlayers()) {
                matchCounts.merge(mp.getUser().getId(), 1L, Long::sum);
                goalCounts.merge(mp.getUser().getId(), (long) m.scoreFor(mp.getTeamColor()), Long::sum);
            }
        }

        var mostActive = matchCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> {
                User u = usersById.get(e.getKey());
                return new CompanyStats.PlayerStat(e.getKey(), u != null ? u.getDisplayName() : "Unknown", e.getValue());
            }).orElse(null);

        var topScorer = goalCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> {
                User u = usersById.get(e.getKey());
                return new CompanyStats.PlayerStat(e.getKey(), u != null ? u.getDisplayName() : "Unknown", e.getValue());
            }).orElse(null);

        // Biggest win
        var biggestWin = allMatches.stream()
            .max(Comparator.comparingInt(m -> Math.abs(m.getYellowScore() - m.getWhiteScore())))
            .map(m -> new CompanyStats.MatchStat(m.getId(),
                Math.abs(m.getYellowScore() - m.getWhiteScore()),
                m.getYellowScore() + " - " + m.getWhiteScore()))
            .orElse(null);

        // Win streaks per player
        var longestWin = computeStreak(allMatches, usersById, true);
        var longestLose = computeStreak(allMatches, usersById, false);

        // Most common pairing
        var pairing = computeMostCommonPairing(allMatches, usersById);

        // Current streaks per player
        Map<UUID, Integer> currentWinStreaks = new HashMap<>();
        Map<UUID, Integer> currentLoseStreaks = new HashMap<>();
        for (Match match : allMatches) {
            TeamColor winner = match.winnerColor();
            for (MatchPlayer mp : match.getPlayers()) {
                UUID pid = mp.getUser().getId();
                boolean isWin = winner == mp.getTeamColor();
                boolean isDraw = winner == null;
                if (isWin) {
                    currentWinStreaks.merge(pid, 1, Integer::sum);
                    currentLoseStreaks.put(pid, 0);
                } else if (isDraw) {
                    currentWinStreaks.put(pid, 0);
                    currentLoseStreaks.put(pid, 0);
                } else {
                    currentLoseStreaks.merge(pid, 1, Integer::sum);
                    currentWinStreaks.put(pid, 0);
                }
            }
        }
        List<CompanyStats.CurrentStreak> currentStreaksList = new ArrayList<>();
        for (UUID pid : currentWinStreaks.keySet()) {
            int ws = currentWinStreaks.getOrDefault(pid, 0);
            int ls = currentLoseStreaks.getOrDefault(pid, 0);
            if (ws == 0 && ls == 0) continue;
            User u = usersById.get(pid);
            String name = u != null ? u.getDisplayName() : "Unknown";
            String type = ws > 0 ? "WIN" : "LOSE";
            int count = ws > 0 ? ws : ls;
            currentStreaksList.add(new CompanyStats.CurrentStreak(pid, name, type, count));
        }
        currentStreaksList.sort((a, b) -> Integer.compare(b.count(), a.count()));

        // Zlatý Bludišťák
        var bludistakData = computeBludistakWinners(allMatches);
        CompanyStats.BludistakChamp currentBludistak = null;
        CompanyStats.BludistakChamp mostBludistakWins = null;
        if (!bludistakData.monthlyWinners().isEmpty()) {
            // Current champ = winner of last completed month
            var lastWinner = bludistakData.monthlyWinners().get(bludistakData.monthlyWinners().size() - 1);
            UUID lastWinnerId = lastWinner.getValue();
            User lastWinnerUser = usersById.get(lastWinnerId);
            currentBludistak = new CompanyStats.BludistakChamp(lastWinnerId,
                lastWinnerUser != null ? lastWinnerUser.getDisplayName() : "Unknown",
                bludistakData.winsPerPlayer().getOrDefault(lastWinnerId, 0),
                lastWinner.getKey().toString());

            // Player with most total wins
            bludistakData.winsPerPlayer().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> {
                    User u = usersById.get(e.getKey());
                    // find last month they won
                    String lastMonth = bludistakData.monthlyWinners().stream()
                        .filter(mw -> mw.getValue().equals(e.getKey()))
                        .reduce((a, b) -> b)
                        .map(mw -> mw.getKey().toString())
                        .orElse("");
                });
            var topWinner = bludistakData.winsPerPlayer().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
            if (topWinner != null) {
                User u = usersById.get(topWinner.getKey());
                mostBludistakWins = new CompanyStats.BludistakChamp(topWinner.getKey(),
                    u != null ? u.getDisplayName() : "Unknown",
                    topWinner.getValue(), "");
            }
        }

        // Color stats
        long yellowWins = allMatches.stream().filter(m -> m.getYellowScore() > m.getWhiteScore()).count();
        long whiteWins = allMatches.stream().filter(m -> m.getWhiteScore() > m.getYellowScore()).count();
        double yellowAvg = allMatches.isEmpty() ? 0 :
            allMatches.stream().mapToInt(Match::getYellowScore).average().orElse(0);
        double whiteAvg = allMatches.isEmpty() ? 0 :
            allMatches.stream().mapToInt(Match::getWhiteScore).average().orElse(0);
        var colorStats = new CompanyStats.ColorStats(yellowWins, whiteWins, yellowAvg, whiteAvg);

        // Monthly activity
        var monthlyActivity = allMatches.stream()
            .collect(Collectors.groupingBy(
                m -> YearMonth.from(m.getPlayedAt().atZone(ZoneOffset.UTC)),
                TreeMap::new,
                Collectors.counting()))
            .entrySet().stream()
            .map(e -> new CompanyStats.MonthlyActivity(e.getKey().toString(), e.getValue()))
            .toList();

        return new CompanyStats(totalMatches, totalGoals, mostActive, topScorer, biggestWin,
            longestWin, longestLose, pairing, currentStreaksList,
            currentBludistak, mostBludistakWins, colorStats, monthlyActivity);
    }

    public PlayerStats getPlayerStats(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("User not found: " + userId));
        List<Match> playerMatches = matchRepository.findAllByPlayerId(userId);

        int wins = 0, losses = 0, draws = 0;
        int totalScored = 0, totalConceded = 0;
        Map<UUID, int[]> partnerRecord = new HashMap<>(); // [matches, wins]
        Map<UUID, int[]> opponentRecord = new HashMap<>(); // [matches, losses]
        int attMatches = 0, attWins = 0, attGoalDiffSum = 0;
        int defMatches = 0, defWins = 0, defGoalDiffSum = 0;
        int yellowMatches = 0, yellowWins = 0, yellowScored = 0, yellowConceded = 0;
        int whiteMatches = 0, whiteWins = 0, whiteScored = 0, whiteConceded = 0;

        // Streak and biggest win tracking
        int longestWinStreak = 0, longestLoseStreak = 0;
        int currentWinStreak = 0, currentLoseStreak = 0;
        int biggestWinDiff = 0;
        String biggestWinDesc = null;

        List<PlayerStats.FormEntry> recentForm = new ArrayList<>();

        for (Match match : playerMatches) {
            MatchPlayer myEntry = match.getPlayers().stream()
                .filter(mp -> mp.getUser().getId().equals(userId))
                .findFirst().orElse(null);
            if (myEntry == null) continue;

            TeamColor myColor = myEntry.getTeamColor();
            int scored = match.scoreFor(myColor);
            int conceded = match.scoreAgainst(myColor);
            boolean won = match.winnerColor() == myColor;
            boolean draw = match.winnerColor() == null;
            boolean lost = !won && !draw;

            if (won) {
                wins++;
                currentWinStreak++;
                currentLoseStreak = 0;
                longestWinStreak = Math.max(longestWinStreak, currentWinStreak);
                int diff = scored - conceded;
                if (diff > biggestWinDiff) {
                    biggestWinDiff = diff;
                    biggestWinDesc = scored + " - " + conceded;
                }
            } else if (draw) {
                draws++;
                currentWinStreak = 0;
                currentLoseStreak = 0;
            } else {
                losses++;
                currentLoseStreak++;
                currentWinStreak = 0;
                longestLoseStreak = Math.max(longestLoseStreak, currentLoseStreak);
            }
            totalScored += scored;
            totalConceded += conceded;

            // Partner/opponent tracking
            for (MatchPlayer mp : match.getPlayers()) {
                if (mp.getUser().getId().equals(userId)) continue;
                if (mp.getTeamColor() == myColor) {
                    partnerRecord.computeIfAbsent(mp.getUser().getId(), k -> new int[2]);
                    partnerRecord.get(mp.getUser().getId())[0]++;
                    if (won) partnerRecord.get(mp.getUser().getId())[1]++;
                } else {
                    opponentRecord.computeIfAbsent(mp.getUser().getId(), k -> new int[2]);
                    opponentRecord.get(mp.getUser().getId())[0]++;
                    if (lost) opponentRecord.get(mp.getUser().getId())[1]++;
                }
            }

            // Role stats
            if (myEntry.getPlayerRole() == PlayerRole.ATTACKER) {
                attMatches++;
                if (won) attWins++;
                attGoalDiffSum += scored - conceded;
            } else {
                defMatches++;
                if (won) defWins++;
                defGoalDiffSum += scored - conceded;
            }

            // Color stats
            if (myColor == TeamColor.YELLOW) {
                yellowMatches++;
                if (won) yellowWins++;
                yellowScored += scored;
                yellowConceded += conceded;
            } else {
                whiteMatches++;
                if (won) whiteWins++;
                whiteScored += scored;
                whiteConceded += conceded;
            }

            recentForm.add(new PlayerStats.FormEntry(match.getId(), won, scored - conceded));
        }

        int total = playerMatches.size();
        double winRate = total > 0 ? (double) wins / total * 100 : 0;

        // Find best/worst partner — only load users we actually need
        Set<UUID> relevantUserIds = new HashSet<>();
        partnerRecord.keySet().forEach(relevantUserIds::add);
        opponentRecord.keySet().forEach(relevantUserIds::add);
        Map<UUID, User> usersById = userRepository.findAllById(relevantUserIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        var bestPartner = findBestPartner(partnerRecord, usersById, true);
        var worstPartner = findBestPartner(partnerRecord, usersById, false);
        var nemesis = findNemesisOrFavorite(opponentRecord, usersById, true);
        var favoriteOpponent = findNemesisOrFavorite(opponentRecord, usersById, false);

        var attackerStats = new PlayerStats.RoleStats(attMatches, attWins,
            attMatches > 0 ? (double) attWins / attMatches * 100 : 0,
            attMatches > 0 ? (double) attGoalDiffSum / attMatches : 0);
        var defenderStats = new PlayerStats.RoleStats(defMatches, defWins,
            defMatches > 0 ? (double) defWins / defMatches * 100 : 0,
            defMatches > 0 ? (double) defGoalDiffSum / defMatches : 0);

        var yellowStat = new PlayerStats.ColorStat(yellowMatches, yellowWins,
            yellowMatches > 0 ? (double) yellowWins / yellowMatches * 100 : 0,
            yellowScored, yellowConceded);
        var whiteStat = new PlayerStats.ColorStat(whiteMatches, whiteWins,
            whiteMatches > 0 ? (double) whiteWins / whiteMatches * 100 : 0,
            whiteScored, whiteConceded);

        // Last 10 for form
        var last10 = recentForm.size() > 10
            ? recentForm.subList(recentForm.size() - 10, recentForm.size())
            : recentForm;

        // ELO statistics from per-match data
        List<MatchPlayer> eloRecords = matchPlayerRepository.findByUserIdWithEloData(userId);
        Integer highestElo = null, lowestElo = null, biggestGain = null, biggestLoss = null;
        int totalEloChange = 0;
        for (MatchPlayer mp : eloRecords) {
            if (mp.getEloAfter() != null) {
                highestElo = highestElo == null ? mp.getEloAfter() : Math.max(highestElo, mp.getEloAfter());
                lowestElo = lowestElo == null ? mp.getEloAfter() : Math.min(lowestElo, mp.getEloAfter());
            }
            if (mp.getEloChange() != null) {
                biggestGain = biggestGain == null ? mp.getEloChange() : Math.max(biggestGain, mp.getEloChange());
                biggestLoss = biggestLoss == null ? mp.getEloChange() : Math.min(biggestLoss, mp.getEloChange());
                totalEloChange += mp.getEloChange();
            }
        }
        double avgEloChange = eloRecords.isEmpty() ? 0 : (double) totalEloChange / eloRecords.size();

        var biggestWinStat = biggestWinDesc != null
            ? new PlayerStats.BiggestWin(biggestWinDiff, biggestWinDesc) : null;
        String streakType = currentWinStreak > 0 ? "WIN" : currentLoseStreak > 0 ? "LOSE" : "NONE";
        int streakCount = currentWinStreak > 0 ? currentWinStreak : currentLoseStreak;
        var currentStreakInfo = new PlayerStats.StreakInfo(streakType, streakCount);

        // Zlatý Bludišťák wins for this player — reuse player's matches
        var bludistakData = computeBludistakWinners(playerMatches);
        int bludistakWins = bludistakData.winsPerPlayer().getOrDefault(userId, 0);

        return new PlayerStats(
            userId, user.getDisplayName(), total, wins, losses, draws,
            Math.round(winRate * 10) / 10.0,
            totalScored, totalConceded,
            total > 0 ? Math.round((double) totalScored / total * 10) / 10.0 : 0,
            total > 0 ? Math.round((double) totalConceded / total * 10) / 10.0 : 0,
            user.getEloRating(),
            user.getAttackerElo(),
            user.getDefenderElo(),
            highestElo, lowestElo, biggestGain, biggestLoss,
            Math.round(avgEloChange * 100) / 100.0,
            longestWinStreak, longestLoseStreak, biggestWinStat, currentStreakInfo,
            bludistakWins,
            bestPartner, worstPartner, nemesis, favoriteOpponent,
            attackerStats, defenderStats, yellowStat, whiteStat, last10
        );
    }

    private PlayerStats.PartnerStat findBestPartner(Map<UUID, int[]> record, Map<UUID, User> users, boolean best) {
        return record.entrySet().stream()
            .filter(e -> e.getValue()[0] >= 2) // at least 2 matches together
            .max((a, b) -> {
                double rateA = (double) a.getValue()[1] / a.getValue()[0];
                double rateB = (double) b.getValue()[1] / b.getValue()[0];
                return best ? Double.compare(rateA, rateB) : Double.compare(rateB, rateA);
            })
            .map(e -> {
                User u = users.get(e.getKey());
                double rate = (double) e.getValue()[1] / e.getValue()[0] * 100;
                return new PlayerStats.PartnerStat(e.getKey(),
                    u != null ? u.getDisplayName() : "Unknown",
                    e.getValue()[0], e.getValue()[1], Math.round(rate * 10) / 10.0);
            })
            .orElse(null);
    }

    private PlayerStats.OpponentStat findNemesisOrFavorite(Map<UUID, int[]> record, Map<UUID, User> users, boolean nemesis) {
        return record.entrySet().stream()
            .filter(e -> e.getValue()[0] >= 2)
            .max((a, b) -> {
                double rateA = (double) a.getValue()[1] / a.getValue()[0];
                double rateB = (double) b.getValue()[1] / b.getValue()[0];
                return nemesis ? Double.compare(rateA, rateB) : Double.compare(rateB, rateA);
            })
            .map(e -> {
                User u = users.get(e.getKey());
                double rate = (double) e.getValue()[1] / e.getValue()[0] * 100;
                return new PlayerStats.OpponentStat(e.getKey(),
                    u != null ? u.getDisplayName() : "Unknown",
                    e.getValue()[0], e.getValue()[1], Math.round(rate * 10) / 10.0);
            })
            .orElse(null);
    }

    private CompanyStats.StreakStat computeStreak(List<Match> matches, Map<UUID, User> users, boolean winStreak) {
        Map<UUID, Integer> currentStreak = new HashMap<>();
        Map<UUID, Integer> bestStreak = new HashMap<>();

        for (Match match : matches) {
            TeamColor winner = match.winnerColor();
            for (MatchPlayer mp : match.getPlayers()) {
                UUID pid = mp.getUser().getId();
                boolean isWin = winner == mp.getTeamColor();
                boolean relevant = winStreak ? isWin : (!isWin && winner != null);

                if (relevant) {
                    currentStreak.merge(pid, 1, Integer::sum);
                    bestStreak.merge(pid, currentStreak.get(pid), Integer::max);
                } else {
                    currentStreak.put(pid, 0);
                }
            }
        }

        return bestStreak.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> {
                User u = users.get(e.getKey());
                return new CompanyStats.StreakStat(e.getKey(),
                    u != null ? u.getDisplayName() : "Unknown", e.getValue());
            })
            .orElse(null);
    }

    /**
     * Compute Zlatý Bludišťák winners — for each completed month, the player
     * with the highest sum of ELO changes wins.
     * Returns map: userId → number of months won, plus ordered list of (month, winnerId).
     */
    private record BludistakData(Map<UUID, Integer> winsPerPlayer, List<Map.Entry<YearMonth, UUID>> monthlyWinners) {}

    private BludistakData computeBludistakWinners(List<Match> allMatches) {
        // Group matches by month
        Map<YearMonth, List<Match>> byMonth = new TreeMap<>();
        for (Match m : allMatches) {
            YearMonth ym = YearMonth.from(m.getPlayedAt().atZone(ZoneOffset.UTC));
            byMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(m);
        }

        Map<UUID, Integer> winsPerPlayer = new HashMap<>();
        List<Map.Entry<YearMonth, UUID>> monthlyWinners = new ArrayList<>();
        YearMonth current = YearMonth.now();

        for (var entry : byMonth.entrySet()) {
            YearMonth month = entry.getKey();
            // Only count completed months
            if (!month.isBefore(current)) continue;

            // Sum ELO changes per player for this month
            Map<UUID, Integer> eloGain = new HashMap<>();
            for (Match m : entry.getValue()) {
                for (MatchPlayer mp : m.getPlayers()) {
                    int change = mp.getEloChange() != null ? mp.getEloChange() : 0;
                    eloGain.merge(mp.getUser().getId(), change, Integer::sum);
                }
            }

            // Find the winner (highest ELO gain)
            eloGain.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(winner -> {
                    winsPerPlayer.merge(winner.getKey(), 1, Integer::sum);
                    monthlyWinners.add(Map.entry(month, winner.getKey()));
                });
        }

        return new BludistakData(winsPerPlayer, monthlyWinners);
    }

    private CompanyStats.PairStat computeMostCommonPairing(List<Match> matches, Map<UUID, User> users) {
        Map<String, long[]> pairings = new HashMap<>(); // key = "uuid1|uuid2", value = [count]

        for (Match match : matches) {
            Map<TeamColor, List<MatchPlayer>> teams = match.getPlayers().stream()
                .collect(Collectors.groupingBy(MatchPlayer::getTeamColor));
            for (List<MatchPlayer> team : teams.values()) {
                if (team.size() == 2) {
                    UUID id1 = team.get(0).getUser().getId();
                    UUID id2 = team.get(1).getUser().getId();
                    String key = id1.compareTo(id2) < 0 ? id1 + "|" + id2 : id2 + "|" + id1;
                    pairings.computeIfAbsent(key, k -> new long[1])[0]++;
                }
            }
        }

        return pairings.entrySet().stream()
            .max(Comparator.comparingLong(e -> e.getValue()[0]))
            .map(e -> {
                String[] ids = e.getKey().split("\\|");
                User u1 = users.get(UUID.fromString(ids[0]));
                User u2 = users.get(UUID.fromString(ids[1]));
                return new CompanyStats.PairStat(
                    UUID.fromString(ids[0]), u1 != null ? u1.getDisplayName() : "Unknown",
                    UUID.fromString(ids[1]), u2 != null ? u2.getDisplayName() : "Unknown",
                    e.getValue()[0]);
            })
            .orElse(null);
    }
}
