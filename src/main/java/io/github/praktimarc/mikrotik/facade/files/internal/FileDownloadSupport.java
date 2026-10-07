package io.github.praktimarc.mikrotik.facade.files.internal;

import io.github.praktimarc.mikrotik.facade.exception.MikrotikFacadeException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikFileException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikUnsupportedFeatureException;
import io.github.praktimarc.mikrotik.facade.files.FileDownloadResult;
import io.github.praktimarc.mikrotik.facade.internal.error.ExceptionMapper;
import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Shared binary download adapter around the low-level byte-safe implementation. */
public final class FileDownloadSupport {
    private FileDownloadSupport() {}

    public static FileDownloadResult download(
            ApiConnection connection,
            String remoteFile,
            Path localFile) throws MikrotikFacadeException {
        Objects.requireNonNull(connection, "connection");
        String remote = requireNonBlank(remoteFile, "remoteFile");
        Path local = Objects.requireNonNull(localFile, "localFile");
        try {
            long bytes = connection.downloadFile(remote, local);
            return new FileDownloadResult(remote, local, bytes);
        } catch (IOException localFailure) {
            throw new MikrotikFileException("Local file download target could not be written safely", localFailure);
        } catch (ApiCommandException commandFailure) {
            if (isDefinitiveMissingBinaryRead(commandFailure)) {
                throw new MikrotikUnsupportedFeatureException(
                        "RouterOS binary file read is not supported by this session",
                        commandFailure);
            }
            throw ExceptionMapper.map(
                    commandFailure,
                    "download RouterOS file",
                    "/file/read",
                    Map.of("file", remote),
                    Map.of());
        } catch (MikrotikApiException apiFailure) {
            throw ExceptionMapper.map(
                    apiFailure,
                    "download RouterOS file",
                    "/file/read",
                    Map.of("file", remote),
                    Map.of());
        }
    }

    private static boolean isDefinitiveMissingBinaryRead(ApiCommandException failure) {
        if (!failure.hasCategory() || failure.getCategory() != 0 || failure.getMessage() == null) {
            return false;
        }
        String message = failure.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("no such command")
                || message.contains("unknown command")
                || message.contains("command not found")
                || message.contains("unknown parameter") && message.contains("chunk-size");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
