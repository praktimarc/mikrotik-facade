package io.github.praktimarc.mikrotik.facade.firewall.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared finite RouterOS operations for typed firewall modules. */
public final class FirewallOperations {
    private FirewallOperations() {}

    @FunctionalInterface
    public interface RecordMapper<T> {
        T map(RouterOsRecord record) throws MikrotikFacadeException;
    }

    public static <T> RouterOsOperation<List<T>> find(
            String name, String path, RouterOsProperties queries, RecordMapper<T> mapper) {
        Objects.requireNonNull(queries, "queries");
        Objects.requireNonNull(mapper, "mapper");
        RouterOsCommand.Builder builder = RouterOsCommand.builder(path);
        queries.asMap().forEach(builder::query);
        RouterOsCommand command = builder.build();
        return new RouterOsOperation<>() {
            @Override public String name() { return name; }
            @Override public RouterOsCommand command() { return command; }
            @Override public List<T> map(CommandResult result) throws MikrotikFacadeException {
                java.util.ArrayList<T> mapped = new java.util.ArrayList<>(result.records().size());
                for (RouterOsRecord record : result.records()) mapped.add(mapper.map(record));
                return List.copyOf(mapped);
            }
        };
    }

    public static RouterOsOperation<Optional<String>> add(
            String name, String path, RouterOsProperties properties) {
        Objects.requireNonNull(properties, "properties");
        RouterOsCommand.Builder builder = RouterOsCommand.builder(path);
        properties.asMap().forEach(builder::argument);
        RouterOsCommand command = builder.build();
        return new RouterOsOperation<>() {
            @Override public String name() { return name; }
            @Override public RouterOsCommand command() { return command; }
            @Override public Optional<String> map(CommandResult result) { return result.completion().find("ret"); }
        };
    }

    public static RouterOsOperation<Void> set(
            String name, String path, String id, RouterOsProperties properties) {
        Objects.requireNonNull(properties, "properties");
        RouterOsCommand.Builder builder = RouterOsCommand.builder(path)
                .argument(".id", requireNonBlank(id, "id"));
        properties.asMap().forEach(builder::argument);
        return voidOperation(name, builder.build());
    }

    public static RouterOsOperation<Void> remove(String name, String path, String id) {
        return voidOperation(name, RouterOsCommand.builder(path)
                .argument(".id", requireNonBlank(id, "id")).build());
    }

    public static RouterOsProperties query(String key, String value) {
        return RouterOsProperties.builder()
                .set(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value")).build();
    }

    public static RouterOsProperties booleanProperty(String key, boolean value) {
        return RouterOsProperties.builder().set(key, Boolean.toString(value)).build();
    }

    private static RouterOsOperation<Void> voidOperation(String name, RouterOsCommand command) {
        return new RouterOsOperation<>() {
            @Override public String name() { return name; }
            @Override public RouterOsCommand command() { return command; }
            @Override public Void map(CommandResult result) { return null; }
        };
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
