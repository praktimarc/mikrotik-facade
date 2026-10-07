package io.github.praktimarc.mikrotik.facade.internal.error;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.CommandDiagnosticRenderer;
import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnectionException;
import me.legrange.mikrotik.ApiDataException;
import me.legrange.mikrotik.MikrotikApiException;

import java.util.Map;
import java.util.Objects;

/**
 * Maps public low-level mikrotik-java exceptions to the stable facade hierarchy.
 */
public final class ExceptionMapper {

    private ExceptionMapper() {
    }

    /**
     * Maps a low-level failure using safe command context.
     *
     * @param failure public low-level exception
     * @param operation safe facade operation name, or null
     * @param commandPath RouterOS command path or raw command string, or null
     * @param arguments command arguments used only for safe redaction
     * @param queries command queries used only for safe redaction
     * @return mapped facade exception retaining the original failure as cause
     */
    public static MikrotikFacadeException map(
            MikrotikApiException failure,
            String operation,
            String commandPath,
            Map<String, String> arguments,
            Map<String, String> queries) {
        Objects.requireNonNull(failure, "failure");

        if (failure instanceof ApiConnectionException connectionFailure) {
            return new MikrotikConnectionException(
                    "RouterOS connection failed",
                    connectionFailure);
        }
        if (failure instanceof ApiCommandException commandFailure) {
            return mapCommand(commandFailure, operation, commandPath, arguments, queries);
        }
        if (failure instanceof ApiDataException dataFailure) {
            return new MikrotikDataException(
                    "RouterOS API data could not be decoded safely",
                    dataFailure);
        }
        return new MikrotikFacadeException(
                "RouterOS API operation failed",
                failure);
    }

    /**
     * Maps a command failure while preserving only safe structured RouterOS context.
     *
     * @param failure low-level command failure
     * @param operation safe facade operation name, or null
     * @param commandPath RouterOS command path or raw command string, or null
     * @param arguments command arguments used only for safe redaction
     * @param queries command queries used only for safe redaction
     * @return mapped command exception
     */
    public static MikrotikCommandException mapCommand(
            ApiCommandException failure,
            String operation,
            String commandPath,
            Map<String, String> arguments,
            Map<String, String> queries) {
        Objects.requireNonNull(failure, "failure");
        Integer category = failure.hasCategory() ? failure.getCategory() : null;
        String safePath = CommandDiagnosticRenderer.safeCommandPath(commandPath);
        String safeRouterOsMessage = CommandDiagnosticRenderer.sanitizeRouterOsMessage(
                commandPath,
                failure.getMessage(),
                arguments,
                queries);

        return new MikrotikCommandException(
                "RouterOS command failed",
                operation,
                safePath,
                category,
                safeRouterOsMessage,
                failure);
    }
}
