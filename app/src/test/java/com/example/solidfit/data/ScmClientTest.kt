package com.example.solidfit.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScmClientTest {

    private val webId = "https://alice.example/profile/card#me"
    private val minute = 60_000L

    private fun reply(status: String, extra: JSONObject.() -> Unit = {}) =
        JSONObject().put("v", 1).put("status", status).put("webId", webId).apply(extra).toString()

    @Test
    fun grantedReplyCarriesTokenKeyExpiryAndWebIdTogether() {
        val parsed = ScmReply.parse(
            reply("GRANTED") { put("accessToken", "at"); put("expiresAt", 123_000L); put("dpopKey", "{\"kty\":\"EC\"}") },
            null,
        )
        assertEquals(ScmReply.Granted(webId, "at", 123_000L, "{\"kty\":\"EC\"}"), parsed)
    }

    @Test
    fun everyStatusParsesToItsReply() {
        assertEquals(ScmReply.ConsentRequired(webId, null), ScmReply.parse(reply("CONSENT_REQUIRED"), null))
        assertEquals(ScmReply.LoginRequired(webId, null), ScmReply.parse(reply("LOGIN_REQUIRED"), null))
        assertEquals(ScmReply.UnknownWebId, ScmReply.parse(reply("UNKNOWN_WEBID"), null))
        assertEquals(ScmReply.Denied, ScmReply.parse(reply("DENIED"), null))
        assertEquals(ScmReply.TransientError, ScmReply.parse(reply("TRANSIENT_ERROR"), null))
    }

    @Test
    fun unreadableOrUnknownReplyIsTransient() {
        assertEquals(ScmReply.TransientError, ScmReply.parse(null, null))
        assertEquals(ScmReply.TransientError, ScmReply.parse("not json", null))
        assertEquals(ScmReply.TransientError, ScmReply.parse(JSONObject().put("v", 2).put("status", "GRANTED").toString(), null))
        assertEquals(ScmReply.TransientError, ScmReply.parse(reply("GRANTED"), null))
    }

    @Test
    fun scmSessionAsksForATokenOnlyNearExpiry() {
        // An hour-long token: ask in its last two minutes.
        assertEquals(2 * minute, scmRefreshLeadMs(expiresAt = 60 * minute, receivedAt = 0L))
        // A 5-minute token: ask in its last 50 s (a sixth), never more often.
        assertEquals(50_000L, scmRefreshLeadMs(expiresAt = 300_000L, receivedAt = 0L))
        // Nonsense times never produce a negative lead.
        assertEquals(0L, scmRefreshLeadMs(expiresAt = 0L, receivedAt = 10L))
    }

    @Test
    fun backgroundRepliesThatNeedTheUserSignOutWithAReason() {
        val now = 1_000L
        assertEquals(BackgroundOutcome.SignOut(SignInProblem.ACCESS_DENIED), backgroundOutcome(ScmReply.ConsentRequired(webId, null), now + 1, now))
        assertEquals(BackgroundOutcome.SignOut(SignInProblem.ACCESS_DENIED), backgroundOutcome(ScmReply.Denied, now + 1, now))
        assertEquals(BackgroundOutcome.SignOut(SignInProblem.NEEDS_LOGIN), backgroundOutcome(ScmReply.LoginRequired(webId, null), now + 1, now))
        assertEquals(BackgroundOutcome.SignOut(null), backgroundOutcome(ScmReply.UnknownWebId, now + 1, now))
    }

    @Test
    fun temporaryFailuresKeepTheTokenUntilItExpiresThenSignOut() {
        val now = 1_000L
        assertEquals(BackgroundOutcome.Keep, backgroundOutcome(ScmReply.TransientError, now + 5_000, now))
        assertEquals(BackgroundOutcome.Keep, backgroundOutcome(ScmReply.Unavailable, now + 5_000, now))
        assertEquals(BackgroundOutcome.SignOut(null), backgroundOutcome(ScmReply.Unavailable, now - 1, now))
    }

    @Test
    fun aSecondRequestWhileOneIsInFlightWaitsForTheFirst() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val requests = SingleFlightRequests {
            calls++
            gate.await()
            ScmReply.Denied
        }

        val first = async { requests.request(webId) }
        val second = async { requests.request(webId) }
        testScheduler.runCurrent()
        gate.complete(Unit)

        assertEquals(listOf(ScmReply.Denied, ScmReply.Denied), listOf(first, second).awaitAll())
        assertEquals(1, calls)
    }

    @Test
    fun threeCallersInOneProcessShareOneScmRequest() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val requests = SingleFlightRequests {
            calls++
            gate.await()
            ScmReply.TransientError
        }

        val callers = List(3) { async { requests.request(webId) } }
        testScheduler.runCurrent()
        gate.complete(Unit)

        assertEquals(List(3) { ScmReply.TransientError }, callers.awaitAll())
        assertEquals(1, calls)
    }

    @Test
    fun cancellingTheFirstCallerGivesJoinedCallersARetryableReply() = runTest {
        val gate = CompletableDeferred<Unit>()
        val requests = SingleFlightRequests {
            gate.await()
            ScmReply.Denied
        }

        val first = async { requests.request(webId) }
        testScheduler.runCurrent()
        val joined = async { requests.request(webId) }
        testScheduler.runCurrent()
        first.cancel()

        assertEquals(ScmReply.TransientError, joined.await())
    }
}
