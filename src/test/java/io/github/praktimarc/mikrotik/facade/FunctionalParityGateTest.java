package io.github.praktimarc.mikrotik.facade;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionalParityGateTest {
    private static final Set<String> TERMINAL = Set.of(
            "IMPLEMENTED",
            "ACCEPTED_RAW_FALLBACK",
            "INTENTIONALLY_OBSOLETE");

    @Test
    void everyParityMatrixRowHasTerminalTask17Status() throws Exception {
        String markdown = Files.readString(Path.of("docs/functional-parity.md"));
        boolean inMatrix = false;
        int checked = 0;

        for (String line : markdown.split("\\R")) {
            if (line.equals("## Parity matrix")) {
                inMatrix = true;
                continue;
            }
            if (inMatrix && line.startsWith("## ") && !line.equals("## Parity matrix")) {
                break;
            }
            if (!inMatrix || line.length() < 3 || !line.startsWith("| ") || line.charAt(2) != 96) {
                continue;
            }

            String[] cells = line.split("\\|");
            String statusCell = cells[cells.length - 1].trim();
            if (statusCell.isEmpty() && cells.length > 1) {
                statusCell = cells[cells.length - 2].trim();
            }
            String status = statusCell.replace(String.valueOf((char) 96), "");
            assertTrue(
                    TERMINAL.contains(status),
                    () -> "Non-terminal parity status in row: " + line);
            checked++;
        }

        assertTrue(checked > 0, "No parity rows were found");
    }
}
