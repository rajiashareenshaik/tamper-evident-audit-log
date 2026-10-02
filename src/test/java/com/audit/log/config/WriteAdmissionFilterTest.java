package com.audit.log.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class WriteAdmissionFilterTest {
    @Test void rejectsExcessWritesAndReleasesPermitAfterFailure() throws Exception {
        var metrics = new SimpleMeterRegistry();
        var filter = new WriteAdmissionFilter(1, metrics);
        var outer = new MockHttpServletRequest("POST", "/api/v1/audit/events");
        assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(outer, new MockHttpServletResponse(), (req, res) -> {
            var rejected = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/audit/client-account-access"), rejected,
                    (r, s) -> fail("An excess write reached the controller"));
            assertEquals(503, rejected.getStatus());
            assertEquals("1", rejected.getHeader("Retry-After"));
            filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/audit/events"), new MockHttpServletResponse(),
                    (r, s) -> assertNotNull(r));
            throw new jakarta.servlet.ServletException("test failure");
        }));
        var recovered = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/audit/events"), recovered, (r, s) -> s.setContentType("text/plain"));
        assertEquals(200, recovered.getStatus());
        assertEquals(1, metrics.get("audit.writes.rejected").counter().count());
        assertEquals(0, metrics.get("audit.writes.active").gauge().value());
    }
}
