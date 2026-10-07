package io.github.praktimarc.mikrotik.facade.snmp;

import io.github.praktimarc.mikrotik.facade.RouterOsEntity;
import io.github.praktimarc.mikrotik.facade.RouterOsRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable typed view of one RouterOS SNMP community.
 *
 * <p>Credential-bearing password/key fields are deliberately not exposed as typed
 * getters. They remain available through {@link #raw()} for explicit low-level use.</p>
 */
public final class SnmpCommunity implements RouterOsEntity {
    private final RouterOsRecord raw;
    private final String name;
    private final Optional<String> id;
    private final Optional<String> address;
    private final Optional<String> security;
    private final Optional<Boolean> readAccess;
    private final Optional<Boolean> writeAccess;
    private final Optional<String> authenticationProtocol;
    private final Optional<String> encryptionProtocol;
    private final Optional<Boolean> disabled;

    /** Creates one immutable SNMP-community entity. */
    public SnmpCommunity(
            RouterOsRecord raw,
            String name,
            Optional<String> id,
            Optional<String> address,
            Optional<String> security,
            Optional<Boolean> readAccess,
            Optional<Boolean> writeAccess,
            Optional<String> authenticationProtocol,
            Optional<String> encryptionProtocol,
            Optional<Boolean> disabled) {
        this.raw = Objects.requireNonNull(raw, "raw");
        this.name = requireNonBlank(name, "name");
        this.id = Objects.requireNonNull(id, "id");
        this.address = Objects.requireNonNull(address, "address");
        this.security = Objects.requireNonNull(security, "security");
        this.readAccess = Objects.requireNonNull(readAccess, "readAccess");
        this.writeAccess = Objects.requireNonNull(writeAccess, "writeAccess");
        this.authenticationProtocol = Objects.requireNonNull(authenticationProtocol, "authenticationProtocol");
        this.encryptionProtocol = Objects.requireNonNull(encryptionProtocol, "encryptionProtocol");
        this.disabled = Objects.requireNonNull(disabled, "disabled");
    }

    public String name() { return name; }
    public Optional<String> id() { return id; }
    public Optional<String> address() { return address; }
    public Optional<String> security() { return security; }
    public Optional<Boolean> readAccess() { return readAccess; }
    public Optional<Boolean> writeAccess() { return writeAccess; }
    public Optional<String> authenticationProtocol() { return authenticationProtocol; }
    public Optional<String> encryptionProtocol() { return encryptionProtocol; }
    public Optional<Boolean> disabled() { return disabled; }

    @Override
    public RouterOsRecord raw() { return raw; }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
