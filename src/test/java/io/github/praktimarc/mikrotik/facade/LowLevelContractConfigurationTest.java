package io.github.praktimarc.mikrotik.facade;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LowLevelContractConfigurationTest {

    @Test
    void pomPinsExactLowLevelBaselineAndKeepsRouterItOutOfNormalSurefire() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<artifactId>mikrotik</artifactId>"));
        assertTrue(pom.contains("<version>3.0.8-praktimarc.4</version>"));
        assertTrue(pom.contains("<exclude>**/*IT.java</exclude>"));
        assertTrue(pom.contains("<id>router-it</id>"));
        assertTrue(pom.contains("<artifactId>maven-failsafe-plugin</artifactId>"));
        assertTrue(pom.contains("<failIfNoTests>true</failIfNoTests>"));
        assertTrue(pom.contains("<include>**/*RouterIT.java</include>"));

        String contract = Files.readString(Path.of("docs/low-level-contract.md"));
        assertTrue(contract.contains("3.0.8-praktimarc.4"));
        assertTrue(contract.contains("c170858efaac04fc78771903ef4c2bdbb6d35325"));
    }

    @Test
    void documentedRouterProfilesStayComplete() throws Exception {
        String profiles = Files.readString(Path.of("docs/routeros-test-profiles.md"));
        for (String profile : new String[]{
                "ROS6_LEGACY",
                "ROS7_NO_WIFI",
                "ROS7_LEGACY_WIRELESS",
                "ROS7_MODERN_WIFI",
                "LEGACY_CAPSMAN",
                "MODERN_WIFI_CAPSMAN",
                "PARALLEL_CAPSMAN"}) {
            assertTrue(profiles.contains(profile), () -> "Missing RouterOS profile " + profile);
        }
    }

    @Test
    void lowLevelContractSuiteUsesOnlyPublicMikrotikApiPackages() throws Exception {
        Path contractRoot = Path.of(
                "src/test/java/io/github/praktimarc/mikrotik/facade/contract");
        try (Stream<Path> sources = Files.walk(contractRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source);
                assertFalse(
                        text.contains("import me.legrange.mikrotik.impl."),
                        () -> "Low-level contract test depends on impl package: " + source);
            }
        }
    }

    @Test
    void realRouterTestIsCredentialGatedByFailsafeNaming() {
        Path routerIt = Path.of(
                "src/test/java/io/github/praktimarc/mikrotik/facade/integration/"
                        + "RouterReadOnlyRouterIT.java");
        assertTrue(Files.exists(routerIt));
        assertTrue(routerIt.getFileName().toString().endsWith("RouterIT.java"));
    }
}
