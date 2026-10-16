package com.malyszczuk.ingredients_retriever.net;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Decides whether an IP address is an ordinary public internet address. Everything that could reach this machine
 * or its network is refused: loopback, private (RFC 1918), link-local (which includes the 169.254.169.254 cloud
 * metadata endpoint), carrier-grade NAT, documentation/benchmark/reserved ranges, multicast, and IPv6 equivalents,
 * including IPv4 addresses hidden inside IPv6 (mapped, NAT64, 6to4).
 */
public final class PublicAddresses {

    private record Cidr(int network, int mask) {
        boolean contains(int address) {
            return (address & mask) == network;
        }
    }

    private static Cidr cidr(String notation) {
        String[] parts = notation.split("/");
        String[] octets = parts[0].split("\\.");
        int network = 0;
        for (String octet : octets) {
            network = (network << 8) | Integer.parseInt(octet);
        }
        int prefix = Integer.parseInt(parts[1]);
        int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
        return new Cidr(network & mask, mask);
    }

    private static final List<Cidr> BLOCKED_IPV4 = List.of(
            cidr("0.0.0.0/8"),        // "this network"
            cidr("10.0.0.0/8"),       // private
            cidr("100.64.0.0/10"),    // carrier-grade NAT (also used by some clouds for internal services)
            cidr("127.0.0.0/8"),      // loopback
            cidr("169.254.0.0/16"),   // link-local, incl. the cloud metadata service 169.254.169.254
            cidr("172.16.0.0/12"),    // private
            cidr("192.0.0.0/24"),     // IETF protocol assignments
            cidr("192.0.2.0/24"),     // documentation
            cidr("192.88.99.0/24"),   // deprecated 6to4 relay
            cidr("192.168.0.0/16"),   // private
            cidr("198.18.0.0/15"),    // benchmarking
            cidr("198.51.100.0/24"),  // documentation
            cidr("203.0.113.0/24"),   // documentation
            cidr("224.0.0.0/4"),      // multicast
            cidr("240.0.0.0/4")       // reserved, incl. the 255.255.255.255 broadcast
    );

    private PublicAddresses() {
    }

    public static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        return switch (bytes.length) {
            case 4 -> isPublicIpv4(bytes);
            case 16 -> isPublicIpv6(bytes);
            default -> false;
        };
    }

    private static boolean isPublicIpv4(byte[] bytes) {
        int address = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        return BLOCKED_IPV4.stream().noneMatch(range -> range.contains(address));
    }

    private static boolean isPublicIpv6(byte[] b) {
        // ::ffff:a.b.c.d (IPv4-mapped) and ::a.b.c.d (deprecated IPv4-compatible, which also covers :: and ::1):
        // the real destination is the embedded IPv4 address.
        boolean zeroPrefix = isZero(b, 0, 10);
        boolean mapped = zeroPrefix && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
        boolean compatible = zeroPrefix && b[10] == 0 && b[11] == 0;
        if (mapped || compatible) {
            return isPublicIpv4(Arrays.copyOfRange(b, 12, 16));
        }

        // NAT64 64:ff9b::/96 embeds the IPv4 destination in the last 32 bits.
        if (b[0] == 0x00 && b[1] == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B && isZero(b, 4, 12)) {
            return isPublicIpv4(Arrays.copyOfRange(b, 12, 16));
        }

        // 6to4 2002::/16 embeds an IPv4 address in bits 16-47.
        if (b[0] == 0x20 && b[1] == 0x02) {
            return isPublicIpv4(Arrays.copyOfRange(b, 2, 6));
        }

        // Of the whole IPv6 space only global unicast 2000::/3 is public; this excludes ::1, fe80::/10 (link-local),
        // fc00::/7 (unique local, e.g. AWS's fd00:ec2::254 metadata address), fec0::/10 and multicast ff00::/8.
        if ((b[0] & 0xE0) != 0x20) {
            return false;
        }

        // Inside 2000::/3, the IETF-reserved 2001::/23 (which contains Teredo) and documentation 2001:db8::/32.
        boolean ietfReserved = b[0] == 0x20 && b[1] == 0x01 && (b[2] & 0xFE) == 0x00;
        boolean documentation = b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0D && (b[3] & 0xFF) == 0xB8;
        return !ietfReserved && !documentation;
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
