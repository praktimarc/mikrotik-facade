package io.github.praktimarc.mikrotik.facade.testing;

import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Minimal overridable test double for the abstract low-level connection. */
public class StubApiConnection extends ApiConnection {
    @Override public boolean isConnected() { return true; }
    @Override public void login(String username, String password) throws MikrotikApiException {
        throw new UnsupportedOperationException();
    }
    @Override public List<Map<String, String>> execute(String command) throws MikrotikApiException {
        throw new UnsupportedOperationException();
    }
    @Override public String execute(String command, ResultListener listener) throws MikrotikApiException {
        throw new UnsupportedOperationException();
    }
    @Override public long downloadFile(String remoteFile, Path localFile)
            throws MikrotikApiException, IOException {
        throw new UnsupportedOperationException();
    }
    @Override public void cancel(String tag) throws MikrotikApiException {
        throw new UnsupportedOperationException();
    }
    @Override public void setTimeout(int timeout) throws MikrotikApiException {}
    @Override public void close() {}
}
