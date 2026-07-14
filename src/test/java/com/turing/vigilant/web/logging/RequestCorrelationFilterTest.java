package com.turing.vigilant.web.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The correlation id must be present for the whole request (so every log line can
 * be tied together), echoed to the caller, honoured when the caller supplies a safe
 * one, regenerated when they don't, and cleared afterwards so it never leaks onto
 * the next request on a pooled thread.
 */
class RequestCorrelationFilterTest {

    private static final Pattern UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void generatesAnIdWhenNoneSuppliedAndExposesItForTheRequestThenClearsIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> idDuringChain.set(MDC.get(RequestCorrelationFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String echoed = response.getHeader(RequestCorrelationFilter.HEADER);
        assertThat(echoed).matches(UUID);
        assertThat(idDuringChain.get()).isEqualTo(echoed);
        // Cleared after the request so a pooled thread starts clean.
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }

    @Test
    void honoursAWellFormedInboundRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.HEADER, "host-req-42_ABC.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idDuringChain = new AtomicReference<>();

        filter.doFilter(request, response,
                (req, res) -> idDuringChain.set(MDC.get(RequestCorrelationFilter.MDC_KEY)));

        assertThat(idDuringChain.get()).isEqualTo("host-req-42_ABC.1");
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("host-req-42_ABC.1");
    }

    @Test
    void replacesAnUnsafeInboundRequestIdRatherThanLoggingItVerbatim() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Contains whitespace/newline — a log-injection vector; must not be trusted.
        request.addHeader(RequestCorrelationFilter.HEADER, "evil\nINFO forged log line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String echoed = response.getHeader(RequestCorrelationFilter.HEADER);
        assertThat(echoed).matches(UUID);
        assertThat(echoed).doesNotContain("forged");
    }
}
