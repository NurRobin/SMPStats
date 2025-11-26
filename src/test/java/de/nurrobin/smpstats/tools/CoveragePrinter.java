package de.nurrobin.smpstats.tools;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class CoveragePrinter {
    private CoveragePrinter() {
    }

    public static void main(String[] args) {
        Path csv = args != null && args.length > 0
                ? Paths.get(args[0])
                : Paths.get("target", "site", "jacoco", "jacoco.csv");
        if (!Files.exists(csv)) {
            System.out.println("[coverage] jacoco.csv not found (" + csv + ")");
            return;
        }
        long missedInstr = 0;
        long coveredInstr = 0;
        long missedLines = 0;
        long coveredLines = 0;
        try (BufferedReader reader = Files.newBufferedReader(csv)) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 9) {
                    continue;
                }
                missedInstr += Long.parseLong(parts[3]);
                coveredInstr += Long.parseLong(parts[4]);
                missedLines += Long.parseLong(parts[7]);
                coveredLines += Long.parseLong(parts[8]);
            }
        } catch (Exception e) {
            System.out.println("[coverage] Could not read jacoco.csv: " + e.getMessage());
            return;
        }
        long totalInstr = missedInstr + coveredInstr;
        long totalLines = missedLines + coveredLines;
        double instrPct = totalInstr == 0 ? 0.0 : coveredInstr * 100.0 / totalInstr;
        double linePct = totalLines == 0 ? 0.0 : coveredLines * 100.0 / totalLines;

        System.out.printf("%n[coverage] Instructions: %.1f%% (%d/%d)%n", instrPct, coveredInstr, totalInstr);
        System.out.printf("[coverage] Lines: %.1f%% (%d/%d)%n", linePct, coveredLines, totalLines);
        System.out.println("[coverage] HTML report: target/site/jacoco/index.html");
    }
}
