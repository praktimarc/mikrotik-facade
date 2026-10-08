package io.github.praktimarc.mikrotik.facade.contract;

import me.legrange.mikrotik.ApiConnectionException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Minimal loopback RouterOS wire peer for public low-level contract tests.
 *
 * <p>This fixture deliberately does not use {@code me.legrange.mikrotik.impl.*}.
 * It speaks only the RouterOS sentence framing needed by the facade's consumer
 * contract suite.</p>
 */
final class RouterOsWireTestServer implements AutoCloseable {

    @FunctionalInterface
    interface Handler {
        void handle(RouterOsWireTestServer server, CommandSentence command) throws Exception;
    }

    static final class CommandSentence {
        private final String command;
        private final String tag;
        private final Map<String, String> parameters;
        private final List<String> queries;
        private final List<String> words;

        private CommandSentence(
                String command,
                String tag,
                Map<String, String> parameters,
                List<String> queries,
                List<String> words) {
            this.command = command;
            this.tag = tag;
            this.parameters = Map.copyOf(parameters);
            this.queries = List.copyOf(queries);
            this.words = List.copyOf(words);
        }

        String command() { return command; }
        String tag() { return tag; }
        String parameter(String name) { return parameters.get(name); }
        List<String> queries() { return queries; }
        List<String> words() { return words; }
    }

    private final Handler handler;
    private final ServerSocket server;
    private final Thread thread;
    private final CountDownLatch clientConnected = new CountDownLatch(1);
    private volatile Socket client;
    private volatile InputStream in;
    private volatile OutputStream out;
    private volatile Throwable failure;
    private volatile boolean closed;

    RouterOsWireTestServer(Handler handler) throws IOException {
        this.handler = handler;
        this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        this.thread = new Thread(this::run, "mikrotik-facade-routeros-wire-test-server");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    int port() {
        return server.getLocalPort();
    }

    boolean awaitClientConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return clientConnected.await(timeout, unit);
    }

    void closeClientConnection() throws IOException {
        Socket current = client;
        if (current != null) {
            current.close();
        }
    }

    synchronized void reply(String type, String tag, String... attributes) throws IOException {
        List<byte[]> words = new ArrayList<>();
        words.add(text(type));
        for (String attribute : attributes) {
            words.add(text(attribute));
        }
        if (tag != null) {
            words.add(text(".tag=" + tag));
        }
        writeSentence(words);
    }

    synchronized void replyData(String tag, byte[] data) throws IOException {
        List<byte[]> words = new ArrayList<>();
        words.add(text("!re"));
        byte[] prefix = text("=data=");
        byte[] dataWord = new byte[prefix.length + data.length];
        System.arraycopy(prefix, 0, dataWord, 0, prefix.length);
        System.arraycopy(data, 0, dataWord, prefix.length, data.length);
        words.add(dataWord);
        words.add(text(".tag=" + tag));
        writeSentence(words);
    }

    void assertHealthy() throws Exception {
        Throwable current = failure;
        if (current == null) {
            return;
        }
        if (current instanceof Exception exception) {
            throw exception;
        }
        throw new AssertionError(current);
    }

    @Override
    public void close() throws Exception {
        closed = true;
        Socket current = client;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
        server.close();
        thread.join(1_000);
        assertHealthy();
    }

    private void run() {
        try (Socket accepted = server.accept()) {
            client = accepted;
            clientConnected.countDown();
            in = accepted.getInputStream();
            out = accepted.getOutputStream();
            while (!closed) {
                List<byte[]> words;
                try {
                    words = readSentence(in);
                } catch (ApiConnectionException expectedAfterClose) {
                    return;
                }
                if (words.isEmpty()) {
                    continue;
                }
                handler.handle(this, parse(words));
            }
        } catch (Throwable throwable) {
            if (!closed) {
                failure = throwable;
            }
        }
    }

    private CommandSentence parse(List<byte[]> rawWords) {
        List<String> words = new ArrayList<>(rawWords.size());
        for (byte[] word : rawWords) {
            words.add(new String(word, StandardCharsets.UTF_8));
        }

        String command = words.get(0);
        String tag = null;
        Map<String, String> parameters = new LinkedHashMap<>();
        List<String> queries = new ArrayList<>();
        for (int index = 1; index < words.size(); index++) {
            String word = words.get(index);
            if (word.startsWith(".tag=")) {
                tag = word.substring(5);
            } else if (word.startsWith("?")) {
                queries.add(word);
            } else if (word.startsWith("=")) {
                int separator = word.indexOf('=', 1);
                if (separator >= 0) {
                    parameters.put(
                            word.substring(1, separator),
                            word.substring(separator + 1));
                }
            }
        }
        return new CommandSentence(command, tag, parameters, queries, words);
    }

    private synchronized void writeSentence(List<byte[]> words) throws IOException {
        OutputStream current = out;
        if (current == null) {
            throw new IOException("No RouterOS test client is connected");
        }
        for (byte[] word : words) {
            writeWord(current, word);
        }
        current.write(0);
        current.flush();
    }

    private static List<byte[]> readSentence(InputStream input) throws ApiConnectionException {
        List<byte[]> sentence = new ArrayList<>();
        while (true) {
            int length = readLength(input);
            if (length == 0) {
                return sentence;
            }
            byte[] word = new byte[length];
            int offset = 0;
            try {
                while (offset < length) {
                    int read = input.read(word, offset, length - offset);
                    if (read < 0) {
                        throw new ApiConnectionException("Unexpected EOF in RouterOS test sentence");
                    }
                    offset += read;
                }
            } catch (IOException exception) {
                throw new ApiConnectionException("Unable to read RouterOS test sentence", exception);
            }
            sentence.add(word);
        }
    }

    private static int readLength(InputStream input) throws ApiConnectionException {
        final int first;
        try {
            first = input.read();
        } catch (IOException exception) {
            throw new ApiConnectionException("Unable to read RouterOS word length", exception);
        }
        if (first < 0) {
            throw new ApiConnectionException("RouterOS test peer closed the connection");
        }
        try {
            if ((first & 0x80) == 0) {
                return first;
            }
            if ((first & 0xC0) == 0x80) {
                return ((first & 0x3F) << 8) | requiredByte(input);
            }
            if ((first & 0xE0) == 0xC0) {
                return ((first & 0x1F) << 16)
                        | (requiredByte(input) << 8)
                        | requiredByte(input);
            }
            if ((first & 0xF0) == 0xE0) {
                return ((first & 0x0F) << 24)
                        | (requiredByte(input) << 16)
                        | (requiredByte(input) << 8)
                        | requiredByte(input);
            }
            if ((first & 0xF8) == 0xF0) {
                return (requiredByte(input) << 24)
                        | (requiredByte(input) << 16)
                        | (requiredByte(input) << 8)
                        | requiredByte(input);
            }
            throw new ApiConnectionException("Unsupported RouterOS word-length control byte");
        } catch (IOException exception) {
            throw new ApiConnectionException("Unable to read RouterOS word length", exception);
        }
    }

    private static int requiredByte(InputStream input) throws IOException, ApiConnectionException {
        int value = input.read();
        if (value < 0) {
            throw new ApiConnectionException("Unexpected EOF in RouterOS word length");
        }
        return value;
    }

    private static void writeWord(OutputStream output, byte[] payload) throws IOException {
        int length = payload.length;
        if (length < 0x80) {
            output.write(length);
        } else if (length < 0x4000) {
            int encoded = length | 0x8000;
            output.write(encoded >> 8);
            output.write(encoded);
        } else if (length < 0x200000) {
            int encoded = length | 0xC00000;
            output.write(encoded >> 16);
            output.write(encoded >> 8);
            output.write(encoded);
        } else if (length < 0x10000000) {
            int encoded = length | 0xE0000000;
            output.write(encoded >> 24);
            output.write(encoded >> 16);
            output.write(encoded >> 8);
            output.write(encoded);
        } else {
            output.write(0xF0);
            output.write(length >> 24);
            output.write(length >> 16);
            output.write(length >> 8);
            output.write(length);
        }
        output.write(payload);
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
