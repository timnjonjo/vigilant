package com.turing.vigilant.webhook;

/** Raised when a webhook cannot be delivered for a non-I/O reason. */
public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
