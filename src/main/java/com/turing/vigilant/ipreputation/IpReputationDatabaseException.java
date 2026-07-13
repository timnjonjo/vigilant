package com.turing.vigilant.ipreputation;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Raised when the local ASN database is missing, unreadable, or corrupt. Extends
 * {@link UncheckedIOException} so it fits the project's "unchecked at the I/O
 * boundary" convention while giving startup failures a clear, specific type.
 */
public class IpReputationDatabaseException extends UncheckedIOException {

    public IpReputationDatabaseException(String message, IOException cause) {
        super(message, cause);
    }
}
