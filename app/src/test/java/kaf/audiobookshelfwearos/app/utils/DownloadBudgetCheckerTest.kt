package kaf.audiobookshelfwearos.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadBudgetCheckerTest {

    @Test
    fun `does not exceed when well under both limits`() {
        assertFalse(
            DownloadBudgetChecker.wouldExceedLimit(
                currentCount = 1,
                currentTotalBytes = 1_000L,
                newItemBytes = 1_000L,
                maxDownloads = 5,
                maxTotalBytes = 1_000_000L
            )
        )
    }

    @Test
    fun `exactly at both limits after adding is not exceeding`() {
        assertFalse(
            DownloadBudgetChecker.wouldExceedLimit(
                currentCount = 4,
                currentTotalBytes = 900L,
                newItemBytes = 100L,
                maxDownloads = 5,
                maxTotalBytes = 1_000L
            )
        )
    }

    @Test
    fun `exceeds when the new count would go over maxDownloads`() {
        assertTrue(
            DownloadBudgetChecker.wouldExceedLimit(
                currentCount = 5,
                currentTotalBytes = 0L,
                newItemBytes = 0L,
                maxDownloads = 5,
                maxTotalBytes = Long.MAX_VALUE
            )
        )
    }

    @Test
    fun `exceeds when the new total bytes would go over maxTotalBytes`() {
        assertTrue(
            DownloadBudgetChecker.wouldExceedLimit(
                currentCount = 0,
                currentTotalBytes = 900L,
                newItemBytes = 200L,
                maxDownloads = Int.MAX_VALUE,
                maxTotalBytes = 1_000L
            )
        )
    }
}
