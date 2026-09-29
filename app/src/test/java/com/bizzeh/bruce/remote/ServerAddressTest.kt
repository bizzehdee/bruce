package com.bizzeh.bruce.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerAddressTest {
    private fun allowed(input: String) = ServerAddress.check(input) as AddressCheck.Allowed
    private fun refused(input: String) = (ServerAddress.check(input) as AddressCheck.Refused).reason

    @Test
    fun httpsIsAllowedAnywhereAndTheV1SuffixIsDropped() {
        assertEquals(AddressCheck.Allowed("https://llm.example.com", plain = false), allowed("https://llm.example.com/v1/"))
        assertEquals(AddressCheck.Allowed("https://llm.example.com:8443", plain = false), allowed("llm.example.com:8443"))
    }

    @Test
    fun plainHttpOnlyOnTheLocalNetwork() {
        assertEquals(AddressCheck.Allowed("http://192.168.1.20:8080", plain = true), allowed("192.168.1.20:8080"))
        assertEquals(true, allowed("http://10.0.0.5:8080").plain)
        assertEquals(true, allowed("http://172.20.1.1").plain)
        assertEquals(true, allowed("http://gpu-box.local:8080").plain)
        assertEquals(true, allowed("http://server.home.arpa").plain)
        assertEquals(true, allowed("http://localhost:8080").plain)
        assertEquals(true, allowed("http://[fd00::1]:8080").plain)
        assertTrue(refused("http://llm.example.com").contains("local network"))
        assertTrue(refused("http://8.8.8.8").contains("local network"))
        assertTrue(refused("http://172.32.0.1").contains("local network"))
    }

    @Test
    fun anythingElseIsRefused() {
        assertEquals("Enter the server's address.", refused("  "))
        assertEquals("Use an http:// or https:// address.", refused("ftp://192.168.1.2"))
        assertTrue(refused("https://user:pass@example.com").contains("API key"))
        assertTrue(refused("https://example.com/?x=1").contains("?"))
        assertEquals("That is not a web address.", refused("https://exa mple.com"))
        assertEquals("That is not a web address.", refused("https://"))
    }

    @Test
    fun localIsDecidedFromTheAddressAsWritten() {
        assertTrue(ServerAddress.isLocal("127.0.0.1"))
        assertTrue(ServerAddress.isLocal("169.254.1.1"))
        assertTrue(ServerAddress.isLocal("::1"))
        assertTrue(ServerAddress.isLocal("fe80::1"))
        assertFalse(ServerAddress.isLocal("192.168.1"))
        assertFalse(ServerAddress.isLocal("192.168.1.999"))
        assertFalse(ServerAddress.isLocal("2001:db8::1"))
        assertFalse(ServerAddress.isLocal("local"))
        assertFalse(ServerAddress.isLocal("zz::1"))
    }
}
