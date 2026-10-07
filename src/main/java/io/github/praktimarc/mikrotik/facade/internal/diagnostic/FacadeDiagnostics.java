package io.github.praktimarc.mikrotik.facade.internal.diagnostic;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikCommandException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikConnectionException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikTimeoutException;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.compat.DataSourcePlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Session-scoped secret-safe diagnostics for lifecycle, commands and compatibility decisions.
 *
 * <p>Diagnostic messages never contain RouterOS argument/query values, record values,
 * credentials, low-level tags, host names or addresses. Stable local session and operation
 * identifiers provide correlation instead.</p>
 */
public final class FacadeDiagnostics {

    /** Diagnostic severity understood by the internal sink. */
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    /** Minimal sink abstraction used to test logging without installing a backend. */
    @FunctionalInterface
    public interface Sink {
        void log(Level level, String message);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(FacadeDiagnostics.class);
    private static final Sink NO_OP = (level, message) -> {};

    private final String sessionId;
    private final Sink sink;
    private final AtomicLong operations = new AtomicLong();
    private final Set<String> emittedFallbackWarnings = ConcurrentHashMap.newKeySet();

    /** Creates a diagnostic context with an explicit sink. Intended for internal tests and wiring. */
    public FacadeDiagnostics(String sessionId, Sink sink) {
        this.sessionId = requireIdentifier(sessionId, "sessionId");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Creates the production SLF4J-backed diagnostic context. */
    public static FacadeDiagnostics slf4j(String sessionId) {
        return new FacadeDiagnostics(sessionId, FacadeDiagnostics::writeSlf4j);
    }

    /** Creates a silent diagnostic context for isolated internal components and tests. */
    public static FacadeDiagnostics noOp(String sessionId) {
        return new FacadeDiagnostics(sessionId, NO_OP);
    }

    /** Returns the stable local session identifier. */
    public String sessionId() {
        return sessionId;
    }

    /** Allocates the next local operation identifier for this session. */
    public String nextOperationId() {
        return "op-" + operations.incrementAndGet();
    }

    /** Records that the authenticated facade session is ready. */
    public void sessionReady() {
        info("state=ready");
    }

    /** Records controlled close start. */
    public void sessionClosing() {
        info("state=closing");
    }

    /** Records controlled close completion. */
    public void sessionClosed() {
        info("state=closed");
    }

    /** Records an unexpected connection-loss transition without the low-level message. */
    public void connectionLost(Throwable cause) {
        warn("state=broken cause=" + safeType(cause));
    }

    /** Records a controlled-close transport failure without the low-level message. */
    public void closeFailure(Throwable cause) {
        warn("state=close-failed cause=" + safeType(cause));
    }

    /** Records one finite command dispatch using structural command metadata only. */
    public void commandStarted(String operationId, String operation, RouterOsCommand command) {
        debug("operation=" + requireIdentifier(operationId, "operationId")
                + " event=start name=" + safeOperation(operation)
                + " " + CommandDiagnosticRenderer.structural(command));
    }

    /** Records the terminal command-engine state without free-form failure text. */
    public void commandTerminal(
            String operationId,
            String operation,
            RouterOsCommand command,
            Throwable failure) {
        String prefix = "operation=" + requireIdentifier(operationId, "operationId")
                + " name=" + safeOperation(operation)
                + " path=" + command.path();
        if (failure == null) {
            debug(prefix + " event=completed");
            return;
        }
        Throwable cause = unwrap(failure);
        if (cause instanceof CancellationException) {
            debug(prefix + " event=cancelled");
        } else if (cause instanceof MikrotikTimeoutException) {
            warn(prefix + " event=timeout");
        } else if (cause instanceof MikrotikCommandException commandFailure) {
            String category = commandFailure.category().isPresent()
                    ? Integer.toString(commandFailure.category().getAsInt())
                    : "unknown";
            debug(prefix + " event=routeros-rejected category=" + category);
        } else if (cause instanceof MikrotikConnectionException) {
            warn(prefix + " event=connection-failure cause=" + safeType(cause));
        } else if (cause instanceof MikrotikDataException) {
            warn(prefix + " event=data-failure cause=" + safeType(cause));
        } else {
            warn(prefix + " event=facade-failure cause=" + safeType(cause));
        }
    }

    /** Records an internal invariant/runtime failure. */
    public void internalError(
            String operationId,
            String operation,
            RouterOsCommand command,
            Throwable failure) {
        error("operation=" + requireIdentifier(operationId, "operationId")
                + " event=internal-error name=" + safeOperation(operation)
                + " path=" + command.path()
                + " cause=" + safeType(failure));
    }

    /** Records definitive or probed capability knowledge at DEBUG. */
    public void capabilityDecision(String capability, String state, String source) {
        debug("capability=" + requireIdentifier(capability, "capability")
                + " state=" + requireIdentifier(state, "state")
                + " source=" + requireIdentifier(source, "source"));
    }

    /** Records a compatibility source selection at DEBUG. */
    public void sourceSelection(DataSourcePlan plan, List<String> selected) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(selected, "selected");
        debug("feature=" + plan.feature()
                + " strategy=" + plan.strategy()
                + " selected=" + List.copyOf(selected));
    }

    /** Records an actual compatibility fallback with both paths at WARN. */
    public void compatibilityFallback(String feature, String unavailable, String selected) {
        String safeFeature = requireIdentifier(feature, "feature");
        String safeUnavailable = requireIdentifier(unavailable, "unavailable");
        String safeSelected = requireIdentifier(selected, "selected");
        String key = safeFeature + '|' + safeUnavailable + '|' + safeSelected;
        if (emittedFallbackWarnings.add(key)) {
            warn("compatibility-fallback feature=" + safeFeature
                    + " unavailable=" + safeUnavailable
                    + " selected=" + safeSelected);
        }
    }

    private void debug(String message) {
        sink.log(Level.DEBUG, prefix(message));
    }

    private void info(String message) {
        sink.log(Level.INFO, prefix(message));
    }

    private void warn(String message) {
        sink.log(Level.WARN, prefix(message));
    }

    private void error(String message) {
        sink.log(Level.ERROR, prefix(message));
    }

    private String prefix(String message) {
        return "session=" + sessionId + " " + message;
    }

    private static void writeSlf4j(Level level, String message) {
        switch (level) {
            case DEBUG -> LOGGER.debug(message);
            case INFO -> LOGGER.info(message);
            case WARN -> LOGGER.warn(message);
            case ERROR -> LOGGER.error(message);
        }
    }

    private static String safeOperation(String operation) {
        Objects.requireNonNull(operation, "operation");
        String trimmed = operation.trim();
        return trimmed.isEmpty() ? "<unnamed>" : trimmed.replaceAll("[\\r\\n\\t]", " ");
    }

    private static String safeType(Throwable failure) {
        return failure == null ? "unknown" : failure.getClass().getSimpleName();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (!(Character.isLetterOrDigit(ch)
                    || ch == '/' || ch == '.' || ch == '_' || ch == '-' || ch == ':')) {
                throw new IllegalArgumentException(label + " contains unsupported diagnostic characters");
            }
        }
        return trimmed;
    }
}
