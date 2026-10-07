package io.github.praktimarc.mikrotik.facade.files;

import java.nio.file.Path;
import java.util.Objects;

/** Result of one complete binary-safe RouterOS file download. */
public final class FileDownloadResult {
    private final String remoteFile;
    private final Path localFile;
    private final long bytesWritten;

    /** Creates one immutable download result. */
    public FileDownloadResult(String remoteFile, Path localFile, long bytesWritten) {
        this.remoteFile = requireNonBlank(remoteFile, "remoteFile");
        this.localFile = Objects.requireNonNull(localFile, "localFile");
        if (bytesWritten < 0) {
            throw new IllegalArgumentException("bytesWritten must not be negative");
        }
        this.bytesWritten = bytesWritten;
    }

    public String remoteFile() { return remoteFile; }
    public Path localFile() { return localFile; }
    public long bytesWritten() { return bytesWritten; }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
