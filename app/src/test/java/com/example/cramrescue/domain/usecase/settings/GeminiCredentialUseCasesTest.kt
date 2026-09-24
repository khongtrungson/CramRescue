package com.example.cramrescue.domain.usecase.settings

import com.example.cramrescue.domain.analysis.FakeCredentialRepository
import com.example.cramrescue.domain.analysis.FakeStudyAnalysisClient
import com.example.cramrescue.domain.analysis.RemoteFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiCredentialUseCasesTest {

    private val repository = FakeCredentialRepository(key = null)
    private val client = FakeStudyAnalysisClient()
    private val save = SaveGeminiCredentialUseCase(repository, client)
    private val check = CheckStoredGeminiCredentialUseCase(repository, client)

    @Test
    fun validKeyIsTrimmedAndSaved() = runBlocking {
        assertEquals(CredentialCheckResult.Valid, save("  new-key  "))
        assertEquals("new-key", repository.key)
    }

    @Test
    fun blankKeyIsNotVerifiedOrSaved() = runBlocking {
        assertEquals(CredentialCheckResult.Blank, save("   "))
        assertEquals(0, client.verifyCalls)
        assertNull(repository.key)
    }

    @Test
    fun rejectedKeyIsNeverSavedAndExistingKeyIsKept() = runBlocking {
        repository.key = "old-key"
        client.nextFailure = RemoteFailure.InvalidCredential

        assertEquals(CredentialCheckResult.Failed(RemoteFailure.InvalidCredential), save("bad-key"))
        assertEquals("old-key", repository.key)
    }

    @Test
    fun networkOrRateLimitFailureDoesNotSave() = runBlocking {
        listOf(RemoteFailure.Network, RemoteFailure.RateLimited(null), RemoteFailure.Service).forEach { failure ->
            client.nextFailure = failure
            assertEquals(CredentialCheckResult.Failed(failure), save("some-key"))
            assertNull(repository.key)
        }
    }

    @Test
    fun checkingWithNoStoredKeyMakesNoCall() = runBlocking {
        assertEquals(CredentialCheckResult.NotConfigured, check())
        assertEquals(0, client.verifyCalls)
    }

    @Test
    fun checkingStoredKeyReportsCategory() = runBlocking {
        repository.key = "stored"
        assertEquals(CredentialCheckResult.Valid, check())
        client.nextFailure = RemoteFailure.InvalidCredential
        assertEquals(CredentialCheckResult.Failed(RemoteFailure.InvalidCredential), check())
    }
}
