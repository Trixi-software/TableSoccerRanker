package com.tablesoccer.ranker.dataimport;

import com.tablesoccer.ranker.match.*;
import com.tablesoccer.ranker.ranking.RankingService;
import com.tablesoccer.ranker.user.Role;
import com.tablesoccer.ranker.user.User;
import com.tablesoccer.ranker.user.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final RankingService rankingService;

    public ExcelImportService(UserRepository userRepository,
                              MatchRepository matchRepository,
                              RankingService rankingService) {
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.rankingService = rankingService;
    }

    @Transactional
    public ImportResult importFromExcel(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            int imported = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    Instant playedAt = parseExcelDate(row.getCell(0));
                    String yellowAtt = getCellString(row.getCell(1));
                    String yellowDef = getCellString(row.getCell(2));
                    String whiteAtt = getCellString(row.getCell(3));
                    String whiteDef = getCellString(row.getCell(4));
                    Cell yellowScoreCell = row.getCell(5);
                    Cell whiteScoreCell = row.getCell(6);
                    if (yellowScoreCell == null || whiteScoreCell == null) {
                        throw new IllegalArgumentException("Score cells are missing");
                    }
                    int yellowScore = (int) yellowScoreCell.getNumericCellValue();
                    int whiteScore = (int) whiteScoreCell.getNumericCellValue();
                    if (yellowScore < 0 || whiteScore < 0) {
                        throw new IllegalArgumentException("Scores must be non-negative");
                    }

                    saveMatch(yellowAtt, yellowDef, whiteAtt, whiteDef, yellowScore, whiteScore, playedAt);
                    imported++;
                } catch (Exception e) {
                    skipped++;
                    errors.add("Row " + (i + 1) + ": " + e.getMessage());
                    log.warn("Skipping row {}: {}", i + 1, e.getMessage());
                }
            }

            if (imported > 0) {
                rankingService.recalculateAllRankings();
            }
            return new ImportResult(imported, skipped, errors);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
        }
    }

    /**
     * Imports match data from a CSV file.
     * Expected columns: datum,zluty_obrance,zluty_utocnik,bily_obrance,bily_utocnik,skore_zluty,skore_bily
     */
    @Transactional
    public ImportResult importFromCsv(InputStream inputStream) {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine(); // skip header
            if (header != null && header.startsWith("\uFEFF")) {
                header = header.substring(1); // strip BOM
            }

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    String[] cols = line.split(",");
                    if (cols.length < 7) {
                        throw new IllegalArgumentException("Expected 7 columns, got " + cols.length);
                    }

                    String dateStr = cols[0].trim();
                    String yellowDef = cols[1].trim();  // zluty_obrance = yellow defender
                    String yellowAtt = cols[2].trim();  // zluty_utocnik = yellow attacker
                    String whiteDef = cols[3].trim();   // bily_obrance = white defender
                    String whiteAtt = cols[4].trim();   // bily_utocnik = white attacker
                    int yellowScore = Integer.parseInt(cols[5].trim());
                    int whiteScore = Integer.parseInt(cols[6].trim());

                    Instant playedAt = dateStr.contains("T")
                        ? LocalDateTime.parse(dateStr).toInstant(ZoneOffset.UTC)
                        : LocalDate.parse(dateStr).atTime(12, 0).toInstant(ZoneOffset.UTC);

                    saveMatch(yellowAtt, yellowDef, whiteAtt, whiteDef, yellowScore, whiteScore, playedAt);
                    imported++;
                } catch (Exception e) {
                    skipped++;
                    errors.add("Line " + lineNum + ": " + e.getMessage());
                    log.warn("Skipping CSV line {}: {}", lineNum, e.getMessage());
                }
            }

            if (imported > 0) {
                rankingService.recalculateAllRankings();
            }
            return new ImportResult(imported, skipped, errors);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage(), e);
        }
    }

    private void saveMatch(String yellowAtt, String yellowDef, String whiteAtt, String whiteDef,
                           int yellowScore, int whiteScore, Instant playedAt) {
        User ya = findOrCreateUser(yellowAtt);
        User yd = findOrCreateUser(yellowDef);
        User wa = findOrCreateUser(whiteAtt);
        User wd = findOrCreateUser(whiteDef);

        var match = new Match();
        match.setYellowScore(yellowScore);
        match.setWhiteScore(whiteScore);
        match.setPlayedAt(playedAt);
        match.setRecordedBy(ya);

        match.addPlayer(ya, TeamColor.YELLOW, PlayerRole.ATTACKER);
        match.addPlayer(yd, TeamColor.YELLOW, PlayerRole.DEFENDER);
        match.addPlayer(wa, TeamColor.WHITE, PlayerRole.ATTACKER);
        match.addPlayer(wd, TeamColor.WHITE, PlayerRole.DEFENDER);

        matchRepository.save(match);
    }

    private User findOrCreateUser(String displayName) {
        // Try to find by display name first (case-insensitive)
        return userRepository.findByDisplayNameIgnoreCase(displayName)
            .orElseGet(() -> {
                var user = new User();
                user.setDisplayName(displayName);
                user.setEmail(displayName.toLowerCase().replace(" ", ".") + "@local");
                user.setUsername(displayName.toLowerCase().replace(" ", "_") + "_" + UUID.randomUUID().toString().substring(0, 4));
                user.setRole(Role.PLAYER);
                log.info("Created new user: {}", displayName);
                return userRepository.save(user);
            });
    }

    private Instant parseExcelDate(Cell cell) {
        if (cell == null) throw new IllegalArgumentException("Date cell is missing");
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant();
        }
        return LocalDateTime.parse(getCellString(cell)).toInstant(ZoneOffset.UTC);
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> cell.toString().trim();
        };
    }

    public record ImportResult(int imported, int skipped, List<String> errors) {}
}
