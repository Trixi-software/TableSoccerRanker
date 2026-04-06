package com.tablesoccer.ranker;

import com.tablesoccer.ranker.match.Match;
import com.tablesoccer.ranker.match.PlayerRole;
import com.tablesoccer.ranker.match.TeamColor;
import com.tablesoccer.ranker.user.Role;
import com.tablesoccer.ranker.user.User;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;

/**
 * Factory for creating test entities without a database.
 */
public class TestDataFactory {

    public static User createUser(String name, int eloRating) {
        var user = new User();
        setId(user, UUID.randomUUID());
        user.setDisplayName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@test.com");
        user.setUsername(name.toLowerCase().replace(" ", "_"));
        user.setRole(Role.PLAYER);
        user.setEloRating(eloRating);
        user.setAttackerElo(eloRating);
        user.setDefenderElo(eloRating);
        return user;
    }

    public static User createUser(String name) {
        return createUser(name, 1000);
    }

    public static Match createMatch(User yellowAtt, User yellowDef,
                                     User whiteAtt, User whiteDef,
                                     int yellowScore, int whiteScore) {
        var match = new Match();
        setId(match, UUID.randomUUID());
        match.setYellowScore(yellowScore);
        match.setWhiteScore(whiteScore);
        match.setPlayedAt(Instant.now());
        match.setRecordedBy(yellowAtt);
        match.addPlayer(yellowAtt, TeamColor.YELLOW, PlayerRole.ATTACKER);
        match.addPlayer(yellowDef, TeamColor.YELLOW, PlayerRole.DEFENDER);
        match.addPlayer(whiteAtt, TeamColor.WHITE, PlayerRole.ATTACKER);
        match.addPlayer(whiteDef, TeamColor.WHITE, PlayerRole.DEFENDER);
        return match;
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
