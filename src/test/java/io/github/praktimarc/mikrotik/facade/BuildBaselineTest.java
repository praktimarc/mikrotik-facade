package io.github.praktimarc.mikrotik.facade;

import me.legrange.mikrotik.ApiCommandException;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.ApiDataException;
import me.legrange.mikrotik.ConnectionListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildBaselineTest {

    @Test
    void loadsRequiredPublicLowLevelApiTypes() {
        assertNotNull(ApiConnection.class);
        assertNotNull(ApiCommandException.class);
        assertNotNull(ApiDataException.class);
        assertNotNull(ConnectionListener.class);
    }
}
