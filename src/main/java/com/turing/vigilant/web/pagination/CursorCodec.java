package com.turing.vigilant.web.pagination;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Base64url-encodes keyset state and authenticates it with HMAC-SHA256. */
@Component
public class CursorCodec {

    private static final int VERSION = 1;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] signingKey;

    public CursorCodec(ObjectMapper objectMapper,
                       @Value("${vigilant.pagination.cursor-secret:}") String configuredSecret) {
        this.objectMapper = objectMapper;
        this.signingKey = configuredSecret == null || configuredSecret.isBlank()
                ? randomKey()
                : configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(String resource, String queryKey, String sort,
                         Long epochMillis, Double score, Long numericId,
                         String stringId, Integer eventOrder) {
        CursorState state = new CursorState(
                VERSION, resource, hash(queryKey), sort, epochMillis, score,
                numericId, stringId, eventOrder);
        byte[] payload = objectMapper.writeValueAsBytes(state);
        return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload));
    }

    public CursorState decode(String token, String expectedResource,
                              String expectedQueryKey, String expectedSort) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) {
                throw new InvalidCursorException();
            }
            byte[] payload = DECODER.decode(parts[0]);
            byte[] suppliedSignature = DECODER.decode(parts[1]);
            if (!MessageDigest.isEqual(sign(payload), suppliedSignature)) {
                throw new InvalidCursorException();
            }
            CursorState state = objectMapper.readValue(payload, CursorState.class);
            if (state.version() != VERSION
                    || !expectedResource.equals(state.resource())
                    || !hash(expectedQueryKey).equals(state.queryHash())
                    || !expectedSort.equals(state.sort())) {
                throw new InvalidCursorException();
            }
            return state;
        } catch (InvalidCursorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidCursorException();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new IllegalStateException("cannot sign pagination cursor", e);
        }
    }

    private static String hash(String queryKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(queryKey.getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("cannot hash pagination query", e);
        }
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
