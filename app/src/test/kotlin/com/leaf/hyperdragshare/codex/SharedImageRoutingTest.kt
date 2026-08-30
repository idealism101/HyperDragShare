package com.leaf.hyperdragshare.codex

import android.app.job.JobScheduler
import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SharedImageRoutingTest {
    @Test
    fun mediaStoreUriIsRecognisedAsTheSharedCopy() {
        val shared = Uri.parse("content://media/external/images/media/42")

        assertTrue(SharedImagePublisher.isMediaStoreUri(shared))
        assertFalse(ImageStagingClient.isOwnAuthority(shared))
    }

    @Test
    fun moduleAuthorityIsTheOnlyGrantableUri() {
        val owned = ImageStagingClient.BASE_URI.buildUpon()
            .appendPath("shared")
            .appendPath("11111111-2222-3333-4444-555555555555.png")
            .build()

        assertTrue(ImageStagingClient.isOwnAuthority(owned))
        assertFalse(SharedImagePublisher.isMediaStoreUri(owned))
        assertFalse(ImageStagingClient.isOwnAuthority(null))
        assertFalse(SharedImagePublisher.isMediaStoreUri(null))
    }

    @Test
    fun aSharedCopyIsOnlyEverPublishedFromAStagedModuleUri() {
        val context = RuntimeEnvironment.getApplication()
        val shared = Uri.parse("content://media/external/images/media/42")

        assertNull(ImageStagingClient.publishShared(context, null))
        assertNull(ImageStagingClient.publishShared(context, shared))
    }

    @Test
    fun anUnavailableSharedCollectionLeavesThePrivateUriInPlace() {
        val context = RuntimeEnvironment.getApplication()
        val owned = Uri.parse(
            "content://" + ImageStagingClient.AUTHORITY +
                "/shared/11111111-2222-3333-4444-555555555555.png",
        )

        // No provider is registered in the test runtime, so publication fails and the caller
        // keeps using the private URI instead of losing the image.
        assertNull(ImageStagingClient.publishShared(context, owned))
    }

    @Test
    fun aPublishedCopyGetsADeadlineThatOutlivesTheProcess() {
        val context = RuntimeEnvironment.getApplication()
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE)
            as JobScheduler

        SharedImageCleanupJob.schedule(context)
        SharedImageCleanupJob.schedule(context)

        // One job id, re-armed rather than accumulated, and never earlier than the retention
        // cutoff — running before it would sweep nothing and leave the row behind.
        val pending = scheduler.allPendingJobs
        assertEquals(1, pending.size)
        assertTrue(pending[0].minLatencyMillis >= SharedImagePublisher.RETENTION_MS)
        assertTrue(pending[0].isPeriodic.not())
    }
}
