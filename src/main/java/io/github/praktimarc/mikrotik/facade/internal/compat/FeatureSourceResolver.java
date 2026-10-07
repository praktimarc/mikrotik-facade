package io.github.praktimarc.mikrotik.facade.internal.compat;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.internal.diagnostic.FacadeDiagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Generic executor for already-decided compatibility source plans. */
public final class FeatureSourceResolver {

    private final FacadeDiagnostics diagnostics;

    /** Creates a stateless generic source resolver without emitted diagnostics. */
    public FeatureSourceResolver() {
        this(FacadeDiagnostics.noOp("standalone-session"));
    }

    /** Creates a source resolver sharing one session diagnostic context. */
    public FeatureSourceResolver(FacadeDiagnostics diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /**
     * Resolves successful source result sets according to an explicit source plan.
     *
     * <p>Map presence means that a source was successfully queried. An empty list is
     * therefore a successful empty result and never triggers fallback by itself.</p>
     *
     * @param plan explicit compatibility plan
     * @param successfulResults successful result sets keyed by source identifier
     * @return immutable ordered records with preserved provenance
     */
    public List<ResolvedRecord> resolve(
            DataSourcePlan plan,
            Map<String, ? extends List<RouterOsRecord>> successfulResults) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(successfulResults, "successfulResults");

        List<String> selected = selectedSources(plan, successfulResults);
        diagnostics.sourceSelection(plan, selected);
        if (plan.strategy() == DataSourceStrategy.PREFERRED_FALLBACK
                && selected.size() == 1
                && selected.get(0).equals(plan.sources().get(1))) {
            diagnostics.compatibilityFallback(
                    plan.feature(),
                    plan.sources().get(0),
                    plan.sources().get(1));
        }
        List<ResolvedRecord> resolved = new ArrayList<>();
        for (String source : selected) {
            List<RouterOsRecord> records = Objects.requireNonNull(
                    successfulResults.get(source),
                    "successful source result list");
            for (RouterOsRecord record : records) {
                resolved.add(new ResolvedRecord(
                        Objects.requireNonNull(record, "record"),
                        source));
            }
        }
        return List.copyOf(resolved);
    }

    /**
     * Returns a secret-safe explanation of strategy and selected source identifiers.
     *
     * @param plan explicit compatibility plan
     * @param successfulResults successful result sets keyed by source identifier
     * @return safe source-selection diagnostic
     */
    public String diagnostic(
            DataSourcePlan plan,
            Map<String, ? extends List<RouterOsRecord>> successfulResults) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(successfulResults, "successfulResults");
        return "SourceSelection{feature=" + plan.feature()
                + ", strategy=" + plan.strategy()
                + ", selected=" + selectedSources(plan, successfulResults) + '}';
    }

    private static List<String> selectedSources(
            DataSourcePlan plan,
            Map<String, ? extends List<RouterOsRecord>> successfulResults) {
        return switch (plan.strategy()) {
            case SINGLE, CONDITIONAL -> successfulResults.containsKey(plan.sources().get(0))
                    ? List.of(plan.sources().get(0))
                    : List.of();
            case PREFERRED_FALLBACK -> {
                String preferred = plan.sources().get(0);
                String fallback = plan.sources().get(1);
                if (successfulResults.containsKey(preferred)) {
                    yield List.of(preferred);
                }
                yield successfulResults.containsKey(fallback)
                        ? List.of(fallback)
                        : List.of();
            }
            case COMPOSITE -> plan.sources().stream()
                    .filter(successfulResults::containsKey)
                    .toList();
        };
    }
}
