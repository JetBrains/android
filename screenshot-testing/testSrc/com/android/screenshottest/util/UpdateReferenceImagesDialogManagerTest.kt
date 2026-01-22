/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.screenshottest.util

import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.tools.idea.metrics.MetricsTrackerRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class UpdateReferenceImagesDialogManagerTest {

    @get:Rule
    val projectRule = AndroidProjectRule.inMemory()

    @get:Rule
    val metricsTrackerRule = MetricsTrackerRule()

    @Test
    fun testOnlyOneDialogActiveAtATime() = runInEdtAndWait {
        val manager = UpdateReferenceImagesDialogManager.getInstance(projectRule.project)

        // Mock the dialog
        val mockDialog1 = mock(UpdateReferenceImagesDialog::class.java)
        val mockDialog2 = mock(UpdateReferenceImagesDialog::class.java)

        // Create disposables for the mocks to return, so Disposer.register works
        val disposable1 = Disposer.newDisposable("MockDialog1")
        val disposable2 = Disposer.newDisposable("MockDialog2")

        `when`(mockDialog1.disposable).thenReturn(disposable1)
        `when`(mockDialog2.disposable).thenReturn(disposable2)

        var factoryCallCount = 0
        manager.dialogFactory = {
            factoryCallCount++
            if (factoryCallCount == 1) mockDialog1 else mockDialog2
        }

        // 1. First call
        // The dialog is NOT visible initially (default behavior)
        `when`(mockDialog1.isVisible).thenReturn(false)

        val dialog1 = manager.showOrGetDialog()
        assertNotNull("First call should return a new dialog", dialog1)

        // 2. Second call while dialog1 is "visible"
        // Now we tell the mock it is visible
        `when`(mockDialog1.isVisible).thenReturn(true)

        val dialog2 = manager.showOrGetDialog()
        assertNull("Second call should return null", dialog2)
        verify(mockDialog1).toFront() // Verify it was brought to front

        // 3. Close the first dialog
        Disposer.dispose(disposable1)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        // 4. Third call after closing
        // The manager should have cleared the ref to dialog1
        // We expect dialog2 to be returned now
        `when`(mockDialog2.isVisible).thenReturn(false) // New dialog is not visible yet

        val dialog3 = manager.showOrGetDialog()
        assertNotNull("Should return a new dialog", dialog3)
        assertNotSame("Should be different instance", dialog1, dialog3)

        // Cleanup
        Disposer.dispose(disposable2)
    }

    @Test
    fun testManagerDisposalClearsDialog() = runInEdtAndWait {
        val manager = UpdateReferenceImagesDialogManager.getInstance(projectRule.project)

        val mockDialog = mock(UpdateReferenceImagesDialog::class.java)
        val disposable = Disposer.newDisposable("MockDialog")
        `when`(mockDialog.disposable).thenReturn(disposable)

        manager.dialogFactory = { mockDialog }

        val dialog = manager.showOrGetDialog()
        assertNotNull(dialog)

        // Dispose manager
        manager.dispose()

        // Check state by asking for dialog again
        val dialogAfterDispose = manager.showOrGetDialog()
        // It should create a new one (or return the factory result again)
        // Since our factory returns the same mock, it will return mockDialog again
        assertNotNull(dialogAfterDispose)

        Disposer.dispose(disposable)
    }

    @Test
    fun testDialogAnalytics() = runInEdtAndWait {
        val manager = UpdateReferenceImagesDialogManager.getInstance(projectRule.project)

        val mockDialog = mock(UpdateReferenceImagesDialog::class.java)
        val disposable = Disposer.newDisposable("MockDialog")
        `when`(mockDialog.disposable).thenReturn(disposable)

        manager.dialogFactory = { mockDialog }

        try {
            // 1. Open new dialog -> DIALOG_OPEN
            `when`(mockDialog.isVisible).thenReturn(false)
            manager.showOrGetDialog()

            var usages = metricsTrackerRule.testTracker.usages
            assertTrue(usages.isNotEmpty())
            assertEquals(AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW, usages.last().studioEvent.kind)
            assertEquals(ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_DIALOG_OPEN, usages.last().studioEvent.screenshotTestComposePreviewEvent.type)

            // 2. Open existing dialog -> DIALOG_ALREADY_OPEN
            `when`(mockDialog.isVisible).thenReturn(true)
            manager.showOrGetDialog()

            usages = metricsTrackerRule.testTracker.usages
            assertTrue(usages.isNotEmpty())
            assertEquals(ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_DIALOG_ALREADY_OPEN, usages.last().studioEvent.screenshotTestComposePreviewEvent.type)
        } finally {
            Disposer.dispose(disposable)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }
}
