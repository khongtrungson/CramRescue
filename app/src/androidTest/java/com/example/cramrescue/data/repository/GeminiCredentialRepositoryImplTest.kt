package com.example.cramrescue.data.repository

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiCredentialRepositoryImplTest {

    // Placeholder only — not a real credential.
    private val placeholderKey = "placeholder-not-a-real-key-1234567890"

    private lateinit var context: Context
    private lateinit var repository: GeminiCredentialRepositoryImpl

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = GeminiCredentialRepositoryImpl(context)
        runBlocking { repository.clearCredential() }
    }

    @After
    fun tearDown() = runBlocking { repository.clearCredential() }

    @Test
    fun keyRoundTripsAndIsNotStoredAsPlaintext() = runBlocking {
        assertFalse(repository.hasCredential())

        repository.saveCredential(placeholderKey)

        assertTrue(repository.hasCredential())
        assertEquals(placeholderKey, GeminiCredentialRepositoryImpl(context).getCredential())
        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/gemini_credential.xml")
        assertTrue(prefsFile.exists())
        assertFalse(prefsFile.readText().contains(placeholderKey))
    }

    @Test
    fun clearRemovesTheKey() = runBlocking {
        repository.saveCredential(placeholderKey)
        repository.clearCredential()

        assertFalse(repository.hasCredential())
        assertNull(repository.getCredential())
    }
}
