package com.malyszczuk.ingredients_retriever.net;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicAddressesTest {

    // Literals only, so these never touch DNS.
    private static InetAddress ip(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    /** An IPv6 address built from its eight 16-bit groups, always kept as a real 16-byte IPv6 address. */
    private static InetAddress ipv6(int... groups) throws UnknownHostException {
        assertEquals(8, groups.length);
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[2 * i] = (byte) (groups[i] >> 8);
            bytes[2 * i + 1] = (byte) groups[i];
        }
        return Inet6Address.getByAddress(null, bytes, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "93.184.216.34", "172.15.255.255", "172.32.0.1", "100.63.255.255",
            "100.128.0.1", "192.169.0.1", "11.0.0.1", "126.255.255.255", "128.0.0.1", "223.255.255.255",
            "2606:4700:4700::1111", "2001:4860:4860::8888", "2a00:1450:4001:81b::200e"})
    void isPublic_acceptsOrdinaryPublicAddresses(String literal) throws Exception {
        assertTrue(PublicAddresses.isPublic(ip(literal)), literal);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // loopback, "this network", unspecified
            "127.0.0.1", "127.255.255.254", "0.0.0.0", "0.1.2.3",
            // private ranges, at both edges
            "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.0.1", "192.168.255.255",
            // link-local: the cloud metadata service lives here
            "169.254.169.254", "169.254.0.1", "100.64.0.1", "100.100.100.200", "100.127.255.255",
            // protocol assignments, documentation, benchmarking, multicast, reserved, broadcast
            "192.0.0.1", "192.0.2.1", "198.18.0.1", "198.19.255.255", "198.51.100.7", "203.0.113.9",
            "224.0.0.1", "239.255.255.255", "240.0.0.1", "255.255.255.255",
            // IPv6: loopback, unspecified, link-local, unique-local (AWS metadata is fd00:ec2::254), multicast
            "::1", "::", "fe80::1", "fc00::1", "fd00:ec2::254", "fd12:3456::1", "ff02::1", "fec0::1",
            // IPv6 documentation and Teredo
            "2001:db8::1", "2001::1", "2001:1::1"})
    void isPublic_refusesEverythingThatCouldReachThisMachineOrItsNetwork(String literal) throws Exception {
        assertFalse(PublicAddresses.isPublic(ip(literal)), literal);
    }

    @Test
    void isPublic_looksInsideIpv4MappedAddresses() throws Exception {
        // ::ffff:127.0.0.1 and ::ffff:10.0.0.1 are just the IPv4 address in disguise
        assertFalse(PublicAddresses.isPublic(ipv6(0, 0, 0, 0, 0, 0xffff, 0x7f00, 0x0001)));
        assertFalse(PublicAddresses.isPublic(ipv6(0, 0, 0, 0, 0, 0xffff, 0x0a00, 0x0001)));
        assertFalse(PublicAddresses.isPublic(ipv6(0, 0, 0, 0, 0, 0xffff, 0xa9fe, 0xa9fe)));
        assertTrue(PublicAddresses.isPublic(ipv6(0, 0, 0, 0, 0, 0xffff, 0x0808, 0x0808)));
    }

    @Test
    void isPublic_looksInsideDeprecatedIpv4CompatibleAddresses() throws Exception {
        assertFalse(PublicAddresses.isPublic(ipv6(0, 0, 0, 0, 0, 0, 0x7f00, 0x0001)));
        assertFalse(PublicAddresses.isPublic(ipv6(0, 0, 0, 0, 0, 0, 0, 1)));
        assertFalse(PublicAddresses.isPublic(ipv6(0, 0, 0, 0, 0, 0, 0, 0)));
    }

    @Test
    void isPublic_looksInsideNat64Addresses() throws Exception {
        assertFalse(PublicAddresses.isPublic(ipv6(0x64, 0xff9b, 0, 0, 0, 0, 0x7f00, 0x0001)));
        assertFalse(PublicAddresses.isPublic(ipv6(0x64, 0xff9b, 0, 0, 0, 0, 0xa9fe, 0xa9fe)));
        assertTrue(PublicAddresses.isPublic(ipv6(0x64, 0xff9b, 0, 0, 0, 0, 0x0808, 0x0808)));
    }

    @Test
    void isPublic_looksInside6to4Addresses() throws Exception {
        assertFalse(PublicAddresses.isPublic(ipv6(0x2002, 0x7f00, 0x0001, 0, 0, 0, 0, 1)));
        assertFalse(PublicAddresses.isPublic(ipv6(0x2002, 0xc0a8, 0x0001, 0, 0, 0, 0, 1)));
        assertTrue(PublicAddresses.isPublic(ipv6(0x2002, 0x0808, 0x0808, 0, 0, 0, 0, 1)));
    }

    @Test
    void isPublic_onlyTrustsGlobalUnicastForIpv6() throws Exception {
        assertTrue(PublicAddresses.isPublic(ipv6(0x2000, 0, 0, 0, 0, 0, 0, 1)));
        assertTrue(PublicAddresses.isPublic(ipv6(0x3fff, 1, 0, 0, 0, 0, 0, 1)));
        assertFalse(PublicAddresses.isPublic(ipv6(0x4000, 0, 0, 0, 0, 0, 0, 1)));
        assertFalse(PublicAddresses.isPublic(ipv6(0x1000, 0, 0, 0, 0, 0, 0, 1)));
        assertFalse(PublicAddresses.isPublic(ipv6(0x0100, 0, 0, 0, 0, 0, 0, 1)));
    }
}
