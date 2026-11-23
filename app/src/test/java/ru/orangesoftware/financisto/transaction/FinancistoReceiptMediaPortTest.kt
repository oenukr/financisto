// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.content.ContentResolver
import android.content.ContextWrapper
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Shadows
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.utils.PicturesUtil
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class FinancistoReceiptMediaPortTest : AbstractDbTest() {

    private lateinit var mediaPort: FinancistoReceiptMediaPort

    override fun setUp() {
        super.setUp()
        mediaPort = FinancistoReceiptMediaPort(context, UnconfinedTestDispatcher())
    }

    @Test
    fun stageFromUriDeletesPartialFileWhenCopyThrowsIoException() = runTest {
        val picturesDir = requireNotNull(PicturesUtil.pictureFile("probe.jpg", false).parentFile)
        val beforeFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()

        val uri = Uri.parse("content://ru.orangesoftware.financisto.test/failing_io_receipt.jpg")
        Shadows.shadowOf(context.contentResolver).registerInputStream(
            uri,
            partialThenFailStream { IOException("Simulated copy IO failure") },
        )

        val stagedName = mediaPort.stageFromUri(uri.toString())
        assertNull(stagedName)

        val afterFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals(beforeFiles, afterFiles)
    }

    @Test
    fun stageFromUriDeletesPartialFileAndRethrowsCancellationException() = runTest {
        val picturesDir = requireNotNull(PicturesUtil.pictureFile("probe.jpg", false).parentFile)
        val beforeFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()

        val uri = Uri.parse("content://ru.orangesoftware.financisto.test/cancelled_receipt.jpg")
        Shadows.shadowOf(context.contentResolver).registerInputStream(
            uri,
            partialThenFailStream { CancellationException("Simulated copy cancellation") },
        )

        try {
            mediaPort.stageFromUri(uri.toString())
            fail("Expected CancellationException to be rethrown")
        } catch (_: CancellationException) {
            // Expected cancellation propagation.
        }

        val afterFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals(beforeFiles, afterFiles)
    }

    @Test
    fun stageFromUriDeletesFileWhenJobCancelledDuringBlockingReadWithoutThrowing() = runTest {
        val picturesDir = requireNotNull(PicturesUtil.pictureFile("probe.jpg", false).parentFile)
        val beforeFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()

        val uri = Uri.parse("content://ru.orangesoftware.financisto.test/job_cancelled_receipt.jpg")
        lateinit var stagingJob: Job
        val cancellingStream = object : InputStream() {
            private var stage = 0

            override fun read(): Int {
                return when (stage++) {
                    0 -> 0xFF
                    1 -> {
                        stagingJob.cancel(CancellationException("Caller cancelled during blocking read"))
                        -1
                    }
                    else -> -1
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                return when (stage++) {
                    0 -> {
                        b[off] = 0xFF.toByte()
                        1
                    }
                    1 -> {
                        stagingJob.cancel(CancellationException("Caller cancelled during blocking read"))
                        -1
                    }
                    else -> -1
                }
            }
        }
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, cancellingStream)

        var caughtCancellation = false
        stagingJob = launch(start = CoroutineStart.LAZY) {
            try {
                mediaPort.stageFromUri(uri.toString())
                fail("Expected CancellationException when job is cancelled during blocking read")
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }
        stagingJob.start()
        stagingJob.join()

        assertTrue(caughtCancellation)
        val afterFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals(beforeFiles, afterFiles)
    }

    @Test
    fun stageFromUriCopiesBytesAndDeleteStagedFileCleansUp() = runTest {
        val expectedBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte())
        val uri = Uri.parse("content://ru.orangesoftware.financisto.test/valid_receipt.jpg")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(expectedBytes))

        val stagedName = mediaPort.stageFromUri(uri.toString())
        assertNotNull(stagedName)
        val storedFile = PicturesUtil.pictureFile(requireNotNull(stagedName), false)
        assertTrue(storedFile.exists())
        assertArrayEquals(expectedBytes, storedFile.readBytes())
        assertEquals(storedFile.absolutePath, mediaPort.resolveFilePath(stagedName))

        mediaPort.deleteStagedFile(stagedName)
        assertFalse(storedFile.exists())
        assertNull(mediaPort.resolveFilePath(stagedName))
    }

    @Test
    fun stageFromUriRejectsFailingContentUriWithoutFallingBackToLocalPath() = runTest {
        val localSecret = File.createTempFile("local_secret", ".jpg", context.cacheDir).apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        }
        val picturesDir = requireNotNull(PicturesUtil.pictureFile("probe.jpg", false).parentFile)
        val beforeFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        try {
            val failingResolver = mock(ContentResolver::class.java)
            `when`(failingResolver.openInputStream(any())).thenThrow(FileNotFoundException("Missing provider entry"))
            val wrappedContext = object : ContextWrapper(context) {
                override fun getContentResolver(): ContentResolver = failingResolver
            }
            val guardedPort = FinancistoReceiptMediaPort(wrappedContext, UnconfinedTestDispatcher())

            val failingContentUri = Uri.parse(
                "content://ru.orangesoftware.financisto.test${localSecret.absolutePath}"
            )
            assertNull(guardedPort.stageFromUri(failingContentUri.toString()))
            assertNull(mediaPort.stageFromUri(failingContentUri.toString()))
            val afterContentFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
            assertEquals(beforeFiles, afterContentFiles)

            // Explicit file: URI remains supported when ContentResolver cannot open it directly.
            val stagedFileName = guardedPort.stageFromUri(Uri.fromFile(localSecret).toString())
            assertNotNull(stagedFileName)
            val stagedFile = PicturesUtil.pictureFile(requireNotNull(stagedFileName), false)
            assertArrayEquals(localSecret.readBytes(), stagedFile.readBytes())
            guardedPort.deleteStagedFile(stagedFileName)
        } finally {
            localSecret.delete()
        }
    }

    @Test
    fun stageFromUriDeletesStagedFileWhenCancelledAfterIoReturnsBeforeCallerResumes() = runTest {
        val picturesDir = requireNotNull(PicturesUtil.pictureFile("probe.jpg", false).parentFile)
        val beforeFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()

        val callerDispatcher = ManualQueueDispatcher()
        val ioDispatcher = ManualQueueDispatcher()
        val separatedMediaPort = FinancistoReceiptMediaPort(context, ioDispatcher)

        val uri = Uri.parse("content://ru.orangesoftware.financisto.test/post_io_cancel_receipt.jpg")
        Shadows.shadowOf(context.contentResolver).registerInputStream(
            uri,
            ByteArrayInputStream(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())),
        )

        var caughtCancellation = false
        val stagingJob = launch(callerDispatcher) {
            try {
                separatedMediaPort.stageFromUri(uri.toString())
                fail("Expected CancellationException when cancelled before resumption")
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        // Start caller so it dispatches work to ioDispatcher.
        callerDispatcher.runAll()
        // Complete the entire IO copy and return from copyReceiptFromUri while caller has not resumed yet.
        ioDispatcher.runAll()

        val afterIoFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals(beforeFiles.size + 1, afterIoFiles.size)

        // Cancel caller before callerDispatcher resumes withContext result.
        stagingJob.cancel(CancellationException("Cancelled before caller resumption"))
        callerDispatcher.runAll()
        ioDispatcher.runAll()
        callerDispatcher.runAll()

        assertTrue(caughtCancellation)
        val afterCancelFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals(beforeFiles, afterCancelFiles)
    }

    @Test
    fun stageFromUriCleansUpPartialFileAndPropagatesUnexpectedRuntimeException() = runTest {
        val picturesDir = requireNotNull(PicturesUtil.pictureFile("probe.jpg", false).parentFile)
        val beforeFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()

        val uri = Uri.parse("content://ru.orangesoftware.financisto.test/unexpected_npe_receipt.jpg")
        Shadows.shadowOf(context.contentResolver).registerInputStream(
            uri,
            partialThenFailStream { NullPointerException("Unexpected runtime bug") },
        )

        try {
            mediaPort.stageFromUri(uri.toString())
            fail("Expected unexpected NullPointerException to propagate")
        } catch (expected: NullPointerException) {
            assertEquals("Unexpected runtime bug", expected.message)
        }

        val afterFiles = picturesDir.listFiles()?.map { it.name }.orEmpty().toSet()
        assertEquals(beforeFiles, afterFiles)
    }

    private fun partialThenFailStream(failureFactory: () -> Throwable): InputStream {
        return object : InputStream() {
            private var deliveredHeader = false
            override fun read(): Int {
                if (!deliveredHeader) {
                    deliveredHeader = true
                    return 0xFF
                }
                throw failureFactory()
            }
        }
    }

    private class ManualQueueDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
            }
        }
    }
}
