package io.github.praktimarc.mikrotik.facade.files.internal;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.files.RouterFile;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandResult;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import io.github.praktimarc.mikrotik.facade.internal.operation.RouterOsOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared finite file-metadata operations. */
public final class FileOperations {
    public static final String PRINT_PATH = "/file/print";

    private FileOperations() {}

    public static RouterOsOperation<List<RouterFile>> find(
            RouterOsProperties queries,
            RouterFileMapper mapper) {
        Objects.requireNonNull(queries, "queries");
        Objects.requireNonNull(mapper, "mapper");
        RouterOsCommand.Builder builder = RouterOsCommand.builder(PRINT_PATH);
        queries.asMap().forEach(builder::query);
        RouterOsCommand command = builder.build();
        return new RouterOsOperation<>() {
            @Override public String name() { return "list RouterOS files"; }
            @Override public RouterOsCommand command() { return command; }
            @Override public List<RouterFile> map(CommandResult result) throws MikrotikFacadeException {
                ArrayList<RouterFile> mapped = new ArrayList<>(result.records().size());
                for (RouterOsRecord record : result.records()) mapped.add(mapper.map(record));
                return List.copyOf(mapped);
            }
        };
    }

    public static RouterOsOperation<Optional<RouterFile>> findByName(
            String name,
            RouterFileMapper mapper) {
        String expected = requireNonBlank(name, "name");
        RouterOsOperation<List<RouterFile>> find = find(
                RouterOsProperties.builder().set("name", expected).build(),
                mapper);
        return new RouterOsOperation<>() {
            @Override public String name() { return "find RouterOS file by name"; }
            @Override public RouterOsCommand command() { return find.command(); }
            @Override public Optional<RouterFile> map(CommandResult result) throws MikrotikFacadeException {
                List<RouterFile> matches = find.map(result);
                if (matches.size() > 1) {
                    throw new MikrotikDataException(
                            "Expected at most one RouterOS file with the requested name");
                }
                return matches.stream().findFirst();
            }
        };
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
