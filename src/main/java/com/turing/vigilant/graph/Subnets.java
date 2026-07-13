package com.turing.vigilant.graph;

/**
 * Derives the /24 subnet of an IPv4 address, used to build SHARES_IP_SUBNET
 * edges (spec section 5). Non-IPv4 input is passed through unchanged so
 * malformed or IPv6 values never break ingestion — ingestion is never gated.
 */
public final class Subnets {

    private Subnets() {
    }

    public static String subnetOf(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) {
            return ipAddress;
        }
        for (String octet : octets) {
            if (!isByte(octet)) {
                return ipAddress;
            }
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + ".0/24";
    }

    private static boolean isByte(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 && parsed <= 255;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
