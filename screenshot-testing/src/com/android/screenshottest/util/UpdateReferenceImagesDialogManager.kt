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
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.VisibleForTesting

@Service(Service.Level.PROJECT)
class UpdateReferenceImagesDialogManager(private val project: Project) : Disposable {
    private var activeDialog: UpdateReferenceImagesDialog? = null

    @VisibleForTesting
    var dialogFactory: (Project) -> UpdateReferenceImagesDialog = { UpdateReferenceImagesDialog(it) }

    /**
     * Checks if a dialog is already open for the project.
     * If open, it brings it to the front and returns null.
     * If not open, it creates a new one, registers it, and returns it.
     */
    @Synchronized
    fun showOrGetDialog(): UpdateReferenceImagesDialog? {
        val existingDialog = activeDialog
        if (existingDialog != null && existingDialog.isVisible) {
            existingDialog.toFront()
            // Log that the user tried to open the dialog but it was already open
            UsageTracker.log(
              AndroidStudioEvent.newBuilder().apply {
                kind = AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW
                screenshotTestComposePreviewEvent = ScreenshotTestComposePreviewEvent.newBuilder().apply {
                  type = ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_DIALOG_ALREADY_OPEN
                }.build()
              }.withProjectId(project)
            )
            return null
        }

        // Clean up dead reference if any
        if (existingDialog != null && !existingDialog.isVisible) {
            activeDialog = null
        }

        val newDialog = dialogFactory(project)
        activeDialog = newDialog

        // Log the SCREENSHOT_DIALOG_OPEN event
        UsageTracker.log(
          AndroidStudioEvent.newBuilder().apply {
            kind = AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW
            screenshotTestComposePreviewEvent = ScreenshotTestComposePreviewEvent.newBuilder().apply {
              type = ScreenshotTestComposePreviewEvent.Type.SCREENSHOT_DIALOG_OPEN
            }.build()
          }.withProjectId(project)
        )

        Disposer.register(newDialog.disposable) {
            synchronized(this) {
                if (activeDialog == newDialog) {
                    activeDialog = null
                }
            }
        }
        return newDialog
    }

    override fun dispose() {
        activeDialog = null
    }

    companion object {
        fun getInstance(project: Project): UpdateReferenceImagesDialogManager {
            return project.getService(UpdateReferenceImagesDialogManager::class.java)
        }
    }
}
