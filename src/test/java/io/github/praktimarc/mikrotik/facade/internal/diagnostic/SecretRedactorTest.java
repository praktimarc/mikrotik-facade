package io.github.praktimarc.mikrotik.facade.internal.diagnostic;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretRedactorTest {

    @Test
    void recognizesRepresentativeSensitiveRouterOsKeys() {
        assertTrue(SecretRedactor.isSensitiveKey("password"));
        assertTrue(SecretRedactor.isSensitiveKey("wpa2-pre-shared-key"));
        assertTrue(SecretRedactor.isSensitiveKey("private-key"));
        assertTrue(SecretRedactor.isSensitiveKey("authentication-response"));
        assertTrue(SecretRedactor.isSensitiveKey("snmp-community"));
        assertTrue(SecretRedactor.isSensitiveKey("authentication-password"));
        assertTrue(SecretRedactor.isSensitiveKey("encryption-password"));
        assertFalse(SecretRedactor.isSensitiveKey("public-key"));
        assertFalse(SecretRedactor.isSensitiveKey("interface"));
    }

    @Test
    void mapRedactionPreservesOrderAndNonSecretValues() {
        LinkedHashMap<String, String> input = new LinkedHashMap<>();
        input.put("name", "peer-a");
        input.put("private-key", "PRIVATE");
        input.put("comment", "visible");

        Map<String, String> safe = SecretRedactor.redactMap(input);

        assertEquals("[name, private-key, comment]", safe.keySet().toString());
        assertEquals("peer-a", safe.get("name"));
        assertEquals(SecretRedactor.REDACTED, safe.get("private-key"));
        assertEquals("visible", safe.get("comment"));
    }

    @Test
    void freeTextRedactsInlineAndReflectedSecrets() {
        Map<String, String> args = Map.of(
                "password", "REFLECTED",
                "comment", "visible");
        String text = "failure for REFLECTED password=INLINE community:PUBLIC private-key='KEY'";

        String safe = SecretRedactor.redactText(text, args, Map.of());

        assertFalse(safe.contains("REFLECTED"));
        assertFalse(safe.contains("INLINE"));
        assertFalse(safe.contains("PUBLIC"));
        assertFalse(safe.contains("KEY"));
        assertTrue(safe.contains("password=<redacted>"));
        assertTrue(safe.contains("community:<redacted>"));
    }
}
