package com.turing.vigilant.shared;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * One business event, logged as one structured record. Fields are attached as
 * SLF4J key/value pairs, so the structured (JSON) output emits them as discrete,
 * queryable fields and the dev console prints them via {@code %kvp} — the same line
 * is easy to read and easy to grep or index. Lives in {@code shared} so any module
 * can log events without depending on the web layer.
 *
 * <p>Log only non-sensitive fields. Never pass tokens, credentials, raw device or
 * IP identifiers, or referee user ids: those must not appear at INFO. Referral
 * codes, tenant/campaign ids, decisions, scores and case ids are safe and are what
 * makes a decision traceable for compliance.
 *
 * <pre>{@code
 * DomainEvent.of(log, "payout_decision")
 *     .field("tenantId", tenantId.value())
 *     .field("action", action)
 *     .field("caseId", caseId)
 *     .log();
 * }</pre>
 */
public final class DomainEvent {

    private final LoggingEventBuilder builder;

    private DomainEvent(Logger log, String event) {
        this.builder = log.atInfo().setMessage(event).addKeyValue("event", event);
    }

    /** Begins an INFO event named {@code event} (also emitted as the {@code event} field). */
    public static DomainEvent of(Logger log, String event) {
        return new DomainEvent(log, event);
    }

    /** Adds one non-sensitive field. A null value is recorded as the string "null". */
    public DomainEvent field(String key, Object value) {
        builder.addKeyValue(key, value == null ? "null" : value);
        return this;
    }

    public void log() {
        builder.log();
    }
}
