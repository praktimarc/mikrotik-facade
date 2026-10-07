package io.github.praktimarc.mikrotik.facade.files;

import io.github.praktimarc.mikrotik.facade.RouterOsProperties;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFileException;
import io.github.praktimarc.mikrotik.facade.files.internal.FileDownloadSupport;
import io.github.praktimarc.mikrotik.facade.files.internal.FileOperations;
import io.github.praktimarc.mikrotik.facade.files.internal.RouterFileMapper;
import io.github.praktimarc.mikrotik.facade.internal.command.CommandEngine;
import io.github.praktimarc.mikrotik.facade.internal.session.SessionLifecycle;
import me.legrange.mikrotik.ApiConnection;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Synchronous RouterOS file metadata and binary-download facade. */
public final class FilesApi {
    private final ApiConnection connection;
    private final CommandEngine engine;
    private final SessionLifecycle lifecycle;
    private final RouterFileMapper mapper;

    /** Creates the files facade for session wiring. */
    public FilesApi(
            ApiConnection connection,
            CommandEngine engine,
            SessionLifecycle lifecycle) {
        this(connection, engine, lifecycle, new RouterFileMapper());
    }

    FilesApi(
            ApiConnection connection,
            CommandEngine engine,
            SessionLifecycle lifecycle,
            RouterFileMapper mapper) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /** Lists all RouterOS file metadata rows. */
    public List<RouterFile> list() throws MikrotikFacadeException {
        return find(RouterOsProperties.builder().build());
    }

    /** Lists files matching exact RouterOS properties. */
    public List<RouterFile> find(RouterOsProperties queries) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(FileOperations.find(queries, mapper));
    }

    /** Finds one exact RouterOS filename, returning empty when absent. */
    public Optional<RouterFile> findByName(String name) throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        return engine.executeSync(FileOperations.findByName(name, mapper));
    }

    /**
     * Downloads one existing RouterOS file through the low-level binary-safe path.
     *
     * @throws MikrotikFileException when the requested RouterOS file is absent or
     *                               the local target cannot be written safely
     */
    public FileDownloadResult download(String remoteFile, Path localFile)
            throws MikrotikFacadeException {
        lifecycle.ensureOpen();
        String remote = requireNonBlank(remoteFile, "remoteFile");
        Objects.requireNonNull(localFile, "localFile");
        if (findByName(remote).isEmpty()) {
            throw new MikrotikFileException("Requested RouterOS file does not exist");
        }
        lifecycle.ensureOpen();
        return FileDownloadSupport.download(connection, remote, localFile);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
