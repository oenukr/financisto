// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.transaction

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.robolectric.Shadows
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.utils.PicturesUtil
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

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
}
