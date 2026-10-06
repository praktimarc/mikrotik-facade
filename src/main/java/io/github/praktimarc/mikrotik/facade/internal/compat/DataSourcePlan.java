package io.github.praktimarc.mikrotik.facade.internal.compat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable source-resolution plan for one compatibility-dependent feature. */
public final class DataSourcePlan {

    private final String feature;
    private final DataSourceStrategy strategy;
    private final List<String> sources;

    private DataSourcePlan(String feature, DataSourceStrategy strategy, List<String> sources) {
        this.feature = validateIdentifier(feature, "feature");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.sources = List.copyOf(validateSources(sources));
        validateShape();
    }

    /**
     * Creates a plan with one authoritative source.
     *
     * @param feature stable feature identifier
     * @param source authoritative source identifier
     * @return immutable plan
     */
    public static DataSourcePlan single(String feature, String source) {
        return new DataSourcePlan(feature, DataSourceStrategy.SINGLE, List.of(source));
    }

    /**
     * Creates a preferred source plus explicit fallback plan.
     *
     * @param feature stable feature identifier
     * @param preferred preferred source identifier
     * @param fallback fallback source identifier
     * @return immutable plan
     */
    public static DataSourcePlan preferredFallback(String feature, String preferred, String fallback) {
        return new DataSourcePlan(feature, DataSourceStrategy.PREFERRED_FALLBACK, List.of(preferred, fallback));
    }

    /**
     * Creates a conditionally selected single-source plan.
     *
     * @param feature stable feature identifier
     * @param condition resolved compatibility condition
     * @param whenTrue source selected when the condition is true
     * @param whenFalse source selected when the condition is false
     * @return immutable plan
     */
    public static DataSourcePlan conditional(
            String feature,
            boolean condition,
            String whenTrue,
            String whenFalse) {
        return new DataSourcePlan(
                feature,
                DataSourceStrategy.CONDITIONAL,
                List.of(condition ? whenTrue : whenFalse));
    }

    /**
     * Creates a composite plan in deterministic source order.
     *
     * @param feature stable feature identifier
     * @param sources simultaneously relevant source identifiers
     * @return immutable plan
     */
    public static DataSourcePlan composite(String feature, List<String> sources) {
        return new DataSourcePlan(feature, DataSourceStrategy.COMPOSITE, sources);
    }

    /**
     * Returns the stable feature identifier.
     *
     * @return feature identifier
     */
    public String feature() {
        return feature;
    }

    /**
     * Returns the resolution strategy.
     *
     * @return source-resolution strategy
     */
    public DataSourceStrategy strategy() {
        return strategy;
    }

    /**
     * Returns the immutable ordered source identifiers.
     *
     * @return immutable source identifiers
     */
    public List<String> sources() {
        return sources;
    }

    /**
     * Returns a secret-safe compatibility diagnostic containing identifiers only.
     *
     * @return safe plan diagnostic
     */
    public String diagnostic() {
        return "DataSourcePlan{feature=" + feature
                + ", strategy=" + strategy
                + ", sources=" + sources + '}';
    }

    @Override
    public String toString() {
        return diagnostic();
    }

    private void validateShape() {
        int size = sources.size();
        switch (strategy) {
            case SINGLE, CONDITIONAL -> {
                if (size != 1) {
                    throw new IllegalArgumentException(strategy + " requires exactly one source");
                }
            }
            case PREFERRED_FALLBACK -> {
                if (size != 2) {
                    throw new IllegalArgumentException("PREFERRED_FALLBACK requires exactly two sources");
                }
            }
            case COMPOSITE -> {
                if (size < 2) {
                    throw new IllegalArgumentException("COMPOSITE requires at least two sources");
                }
            }
        }
    }

    private static List<String> validateSources(List<String> sources) {
        Objects.requireNonNull(sources, "sources");
        Set<String> unique = new LinkedHashSet<>();
        for (String source : sources) {
            String checked = validateIdentifier(source, "source");
            if (!unique.add(checked)) {
                throw new IllegalArgumentException("source identifiers must be unique");
            }
        }
        return List.copyOf(unique);
    }

    static String validateIdentifier(String value, String label) {
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
