package kaf.audiobookshelfwearos.app.userdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigFileImporterTest {

    @Test
    fun `jump values present are applied unconditionally even when credentials already set`() {
        val config = SideloadConfig(
            protocol = "https",
            serverAddress = "example.com",
            login = "someone",
            password = "secret",
            jumpBackwardSeconds = 15,
            jumpForwardSeconds = 45
        )

        val result = ConfigFileImporter.resolveUpdates(
            config,
            currentServerAddress = "already-set.example.com",
            currentLogin = "already-set-login",
            currentPassword = "already-set-password"
        )

        // Credential fields are one-time secrets: not overwritten since they're already set.
        assertNull(result.protocol)
        assertNull(result.serverAddress)
        assertNull(result.login)
        assertNull(result.password)

        // Jump-seconds are preference defaults: applied regardless of existing values.
        assertEquals(15, result.jumpBackwardSeconds)
        assertEquals(45, result.jumpForwardSeconds)
        assertTrue(result.anyApplied)
    }

    @Test
    fun `jump values absent from config leave existing values untouched`() {
        val config = SideloadConfig(
            protocol = null,
            serverAddress = null,
            login = null,
            password = null,
            jumpBackwardSeconds = null,
            jumpForwardSeconds = null
        )

        val result = ConfigFileImporter.resolveUpdates(
            config,
            currentServerAddress = "already-set.example.com",
            currentLogin = "already-set-login",
            currentPassword = "already-set-password"
        )

        assertNull(result.jumpBackwardSeconds)
        assertNull(result.jumpForwardSeconds)
        assertEquals(false, result.anyApplied)
    }

    @Test
    fun `credential fields only fill in when currently empty`() {
        val config = SideloadConfig(
            protocol = "http",
            serverAddress = "new-server.example.com",
            login = "new-login",
            password = "new-password"
        )

        val result = ConfigFileImporter.resolveUpdates(
            config,
            currentServerAddress = "",
            currentLogin = "",
            currentPassword = ""
        )

        assertEquals("http", result.protocol)
        assertEquals("new-server.example.com", result.serverAddress)
        assertEquals("new-login", result.login)
        assertEquals("new-password", result.password)
        assertTrue(result.anyApplied)
    }

    @Test
    fun `credential fields are not overwritten when already set`() {
        val config = SideloadConfig(
            protocol = "http",
            serverAddress = "new-server.example.com",
            login = "new-login",
            password = "new-password"
        )

        val result = ConfigFileImporter.resolveUpdates(
            config,
            currentServerAddress = "existing-server.example.com",
            currentLogin = "existing-login",
            currentPassword = "existing-password"
        )

        assertNull(result.protocol)
        assertNull(result.serverAddress)
        assertNull(result.login)
        assertNull(result.password)
        assertEquals(false, result.anyApplied)
    }

    @Test
    fun `jump values present and credentials empty both apply together`() {
        val config = SideloadConfig(
            serverAddress = "new-server.example.com",
            login = "new-login",
            password = "new-password",
            jumpBackwardSeconds = 5,
            jumpForwardSeconds = 60
        )

        val result = ConfigFileImporter.resolveUpdates(
            config,
            currentServerAddress = "",
            currentLogin = "",
            currentPassword = ""
        )

        assertEquals("new-server.example.com", result.serverAddress)
        assertEquals("new-login", result.login)
        assertEquals("new-password", result.password)
        assertEquals(5, result.jumpBackwardSeconds)
        assertEquals(60, result.jumpForwardSeconds)
        assertTrue(result.anyApplied)
    }
}
