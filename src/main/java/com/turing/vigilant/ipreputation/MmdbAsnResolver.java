package com.turing.vigilant.ipreputation;

import com.maxmind.db.Reader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link AsnResolver} backed by a locally-hosted MaxMind-format {@code .mmdb}
 * (DB-IP's free IP-to-ASN Lite in production). The database is loaded once at
 * construction and the service fails fast if it is absent or unreadable — it
 * must not start silently degraded. Reads are field-name tolerant so the same
 * code works across DB-IP / GeoLite2 / IPinfo record schemas.
 */
public class MmdbAsnResolver implements AsnResolver {

    /** Candidate ASN field names across MaxMind-format ASN databases. */
    private static final List<String> ASN_FIELDS =
            List.of("autonomous_system_number", "as_number", "asn");

    private final Reader reader;

    public MmdbAsnResolver(Path databasePath) {
        if (!Files.isReadable(databasePath)) {
            throw new com.turing.vigilant.ipreputation.IpReputationDatabaseException(
                    "IP reputation database not found or unreadable at " + databasePath
                            + " — set vigilant.ip-reputation.database-path to a valid DB-IP "
                            + "IP-to-ASN Lite .mmdb file",
                    new FileNotFoundException(databasePath.toString()));
        }
        try {
            this.reader = new Reader(databasePath.toFile());
        } catch (IOException e) {
            throw new com.turing.vigilant.ipreputation.IpReputationDatabaseException(
                    "Failed to open IP reputation database at " + databasePath, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Long> resolveAsn(InetAddress address) {
        try {
            Map<String, Object> record = reader.get(address, Map.class);
            return record == null ? Optional.empty() : extractAsn(record);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read ASN for " + address.getHostAddress(), e);
        }
    }

    private static Optional<Long> extractAsn(Map<String, Object> record) {
        for (String field : ASN_FIELDS) {
            Object value = record.get(field);
            if (value != null) {
                return normalize(value);
            }
        }
        return Optional.empty();
    }

    private static Optional<Long> normalize(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        // Some databases store "AS16509"; keep only the digits.
        String digits = value.toString().replaceAll("\\D", "");
        return digits.isEmpty() ? Optional.empty() : Optional.of(Long.parseLong(digits));
    }
}
