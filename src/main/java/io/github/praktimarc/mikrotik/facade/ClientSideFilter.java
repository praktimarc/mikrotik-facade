package io.github.praktimarc.mikrotik.facade;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Explicit client-side filter for already returned RouterOS records.
 *
 * <p>This type never changes the RouterOS server query. Regex matching therefore remains
 * correct even though the RouterOS API query protocol itself has no regex operator.</p>
 */
public final class ClientSideFilter {

    private final Predicate<RouterOsRecord> predicate;
    private final String description;

    private ClientSideFilter(Predicate<RouterOsRecord> predicate, String description) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
        this.description = Objects.requireNonNull(description, "description");
    }

    /**
     * Creates a client-side Java regex search over one RouterOS property.
     *
     * <p>{@link java.util.regex.Matcher#find()} semantics are used. Add {@code ^} and
     * {@code $} when a full-property match is required. Missing properties do not match.</p>
     */
    public static ClientSideFilter regex(String property, String regularExpression) {
        return regex(property, Pattern.compile(
                Objects.requireNonNull(regularExpression, "regularExpression")));
    }

    /** Creates a client-side regex search using a precompiled pattern. */
    public static ClientSideFilter regex(String property, Pattern pattern) {
        String checkedProperty = requireNonBlank(property, "property");
        Pattern checkedPattern = Objects.requireNonNull(pattern, "pattern");
        return new ClientSideFilter(
                record -> record.find(checkedProperty)
                        .map(value -> checkedPattern.matcher(value).find())
                        .orElse(false),
                "regex(property=" + checkedProperty + ")");
    }

    /** Creates an arbitrary explicit client-side record predicate. */
    public static ClientSideFilter predicate(Predicate<RouterOsRecord> predicate) {
        return new ClientSideFilter(
                Objects.requireNonNull(predicate, "predicate"),
                "predicate");
    }

    /** Returns a boolean AND of two client-side filters. */
    public ClientSideFilter and(ClientSideFilter other) {
        ClientSideFilter checked = Objects.requireNonNull(other, "other");
        return new ClientSideFilter(
                record -> matches(record) && checked.matches(record),
                "and");
    }

    /** Returns a boolean OR of two client-side filters. */
    public ClientSideFilter or(ClientSideFilter other) {
        ClientSideFilter checked = Objects.requireNonNull(other, "other");
        return new ClientSideFilter(
                record -> matches(record) || checked.matches(record),
                "or");
    }

    /** Returns the boolean negation of this client-side filter. */
    public ClientSideFilter not() {
        return new ClientSideFilter(record -> !matches(record), "not");
    }

    /** Evaluates this filter against one complete RouterOS record. */
    public boolean matches(RouterOsRecord record) {
        return predicate.test(Objects.requireNonNull(record, "record"));
    }

    /** Filters an immutable snapshot of RouterOS records client-side. */
    public List<RouterOsRecord> apply(List<RouterOsRecord> records) {
        Objects.requireNonNull(records, "records");
        return records.stream()
                .filter(this::matches)
                .toList();
    }

    @Override
    public String toString() {
        return "ClientSideFilter{" + description + '}';
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
