/**
 * Cursor (keyset) pagination primitives — the signed cursor codec, page envelope
 * and limit guard — shared by the query modules (case queue, campaigns). Exposed
 * as a Spring Modulith named interface so those modules may depend on it as a
 * deliberate, public part of the {@code web} module's API rather than reaching into
 * its internals.
 */
@org.springframework.modulith.NamedInterface("pagination")
package com.turing.vigilant.web.pagination;
