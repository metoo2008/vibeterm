package dev.vibeterm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownHostsTest {

    @Test
    fun fingerprint_matchesOpenSshFormat() {
        // 与 ssh-keygen -lf 一致:SHA256: + base64(无填充),不带尾部 '='
        val fp = KnownHosts.fingerprint("hello".toByteArray())
        assertTrue(fp.startsWith("SHA256:"))
        assertTrue("指纹不应有 base64 填充", !fp.contains("="))
        // 已知向量:SHA-256("hello") 的 base64(无填充)
        assertEquals("SHA256:LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ", fp)
    }

    @Test
    fun fingerprint_isDeterministic() {
        val key = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals(KnownHosts.fingerprint(key), KnownHosts.fingerprint(key.copyOf()))
    }
}
