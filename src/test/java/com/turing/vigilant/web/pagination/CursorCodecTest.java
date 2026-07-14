package com.turing.vigilant.web.pagination;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The cursor is an opaque, tamper-evident token: it round-trips keyset state but is
 * bound to the exact resource, filter and sort it was issued for, and is rejected
 * if any byte — payload or signature — is altered. This is what stops a client from
 * walking another filter's rows or forging a position.
 */
class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec(new ObjectMapper(), "unit-test-secret");

    private String issue() {
        return codec.encode("cases", "loob-bankOPENSCORE", "SCORE",
                1_700_000_000_000L, 0.42, 99L, null, null);
    }

    @Test
    void roundTripsKeysetStateForTheMatchingResourceFilterAndSort() {
        CursorState state = codec.decode(issue(), "cases",
                "loob-bankOPENSCORE", "SCORE");

        assertThat(state).isNotNull();
        assertThat(state.resource()).isEqualTo("cases");
        assertThat(state.sort()).isEqualTo("SCORE");
        assertThat(state.epochMillis()).isEqualTo(1_700_000_000_000L);
        assertThat(state.score()).isEqualTo(0.42);
        assertThat(state.numericId()).isEqualTo(99L);
    }

    @Test
    void blankOrNullCursorMeansFirstPage() {
        assertThat(codec.decode(null, "cases", "q", "SCORE")).isNull();
        assertThat(codec.decode("", "cases", "q", "SCORE")).isNull();
        assertThat(codec.decode("   ", "cases", "q", "SCORE")).isNull();
    }

    @Test
    void aTamperedPayloadIsRejected() {
        String token = issue();
        String[] parts = token.split("\\.");
        // Flip a character in the base64 payload; the HMAC no longer matches.
        char[] payload = parts[0].toCharArray();
        payload[0] = payload[0] == 'A' ? 'B' : 'A';
        String tampered = new String(payload) + "." + parts[1];

        assertThatExceptionOfType(InvalidCursorException.class)
                .isThrownBy(() -> codec.decode(tampered, "cases",
                        "loob-bankOPENSCORE", "SCORE"));
    }

    @Test
    void aTamperedSignatureIsRejected() {
        String token = issue();
        String[] parts = token.split("\\.");
        char[] sig = parts[1].toCharArray();
        sig[0] = sig[0] == 'A' ? 'B' : 'A';

        assertThatExceptionOfType(InvalidCursorException.class)
                .isThrownBy(() -> codec.decode(parts[0] + "." + new String(sig), "cases",
                        "loob-bankOPENSCORE", "SCORE"));
    }

    @Test
    void malformedTokenShapeIsRejected() {
        assertThatExceptionOfType(InvalidCursorException.class)
                .isThrownBy(() -> codec.decode("no-dot-here", "cases", "q", "SCORE"));
        assertThatExceptionOfType(InvalidCursorException.class)
                .isThrownBy(() -> codec.decode("a.b.c", "cases", "q", "SCORE"));
    }

    @Test
    void aCursorForOneResourceIsRejectedForAnother() {
        assertThatExceptionOfType(InvalidCursorException.class)
                .isThrownBy(() -> codec.decode(issue(), "campaigns",
                        "loob-bankOPENSCORE", "SCORE"));
    }

    @Test
    void aStaleFilterCursorIsRejected() {
        // Cursor issued while filtering status=OPEN, replayed after switching to RESOLVED.
        assertThatExceptionOfType(InvalidCursorException.class)
                .isThrownBy(() -> codec.decode(issue(), "cases",
                        "loob-bankRESOLVEDSCORE", "SCORE"));
    }

    @Test
    void aCursorForOneSortIsRejectedForAnother() {
        assertThatExceptionOfType(InvalidCursorException.class)
                .isThrownBy(() -> codec.decode(issue(), "cases",
                        "loob-bankOPENSCORE", "AGE"));
    }

    @Test
    void aCursorSignedWithAnotherKeyIsRejected() {
        CursorCodec other = new CursorCodec(new ObjectMapper(), "a-different-secret");
        assertThatExceptionOfType(InvalidCursorException.class)
                .isThrownBy(() -> other.decode(issue(), "cases",
                        "loob-bankOPENSCORE", "SCORE"));
    }
}
