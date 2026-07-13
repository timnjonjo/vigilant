package com.turing.vigilant.ipreputation;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Parses IP <em>literals</em> (IPv4 and IPv6) into {@link InetAddress} without
 * ever triggering DNS — the module must have zero network dependency at runtime.
 * A non-literal or malformed input raises {@link IllegalArgumentException} rather
 * than being handed to a resolver that might attempt a name lookup.
 */
final class IpAddresses {

    private IpAddresses() {
    }

    static InetAddress parseLiteral(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("ipAddress must not be blank");
        }
        String value = ipAddress.trim();
        if (!isIpv4Literal(value) && !isIpv6Literal(value)) {
            throw new IllegalArgumentException("Not a valid IP literal: " + ipAddress);
        }
        try {
            // Safe: value is already a literal, so getByName never performs DNS.
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Not a valid IP literal: " + ipAddress, e);
        }
    }

    private static boolean isIpv4Literal(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) {
                return false;
            }
            for (int i = 0; i < octet.length(); i++) {
                if (!Character.isDigit(octet.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String value) {
        if (value.indexOf(':') < 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = c == ':' || c == '.' || Character.digit(c, 16) >= 0;
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
