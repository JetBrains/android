/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.ui.screenshot

import com.android.SdkConstants
import com.android.io.writeImage
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import icons.StudioIcons
import java.awt.image.BufferedImage

/**
 * Captures a screenshot of the device display.
 */
class ScreenshotAction : DumbAwareAction(
  AndroidAdbUiBundle.message("screenshot.action.title"),
  AndroidAdbUiBundle.message("screenshot.action.description"),
  StudioIcons.Common.SCREENSHOT,
) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = event.getData(SCREENSHOT_OPTIONS_KEY) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val screenshotOptions = event.getData(SCREENSHOT_OPTIONS_KEY) ?: return
    val serialNumber = screenshotOptions.serialNumber

    val screenshotSupplier = AdbScreenCapScreenshotSupplier(project, serialNumber, screenshotOptions)
    var disposable: Disposable? = screenshotSupplier
    object : ScreenshotTask(project, screenshotSupplier) {

      override fun onSuccess() {
        error?.let { msg ->
          Messages.showErrorDialog(project, msg, AndroidAdbUiBundle.message("screenshot.action.title"))
          return
        }

        try {
          val backingFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
          val screenshotImage = screenshot!!
          screenshotImage.image.writeImage(SdkConstants.EXT_PNG, backingFile)
          val screenshotPostprocessor = screenshotOptions.screenshotPostprocessor
          val framingOptions = screenshotOptions.getFramingOptions(screenshotImage)
          val defaultFrame =
              if (framingOptions.isNotEmpty()) screenshotOptions.getDefaultFramingOption(framingOptions, screenshotImage) else 0
          val viewer: ScreenshotViewer = object : ScreenshotViewer(
            project,
            screenshotImage,
            backingFile,
            screenshotSupplier,
            screenshotPostprocessor,
            framingOptions,
            defaultFrame,
            screenshotOptions.screenshotViewerOptions) {

            override fun doOKAction() {
              super.doOKAction()
              screenshot?.let {
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it)?.let { virtualFile ->
                  virtualFile.refresh(false, false)
                  FileEditorManager.getInstance(project).openFile(virtualFile, true)
                }
              }
            }
          }
          viewer.show()
          Disposer.register(viewer.disposable, screenshotSupplier)
          disposable = null
        }
        catch (e: Exception) {
          thisLogger().warn("Error while displaying screenshot viewer: ", e)
          Messages.showErrorDialog(
            project,
            AndroidAdbUiBundle.message("screenshot.error.generic", e),
            AndroidAdbUiBundle.message("screenshot.action.title"))
        }
      }

      override fun onFinished() {
        disposable?.let {
          Disposer.dispose(it)
        }
      }

    }.queue()
  }

  companion object {
    val SCREENSHOT_OPTIONS_KEY = DataKey.create<ScreenshotOptions>("ScreenshotOptions")
  }

  interface ScreenshotOptions {
    val serialNumber: String
    val apiLevel: Int
    val screenshotViewerOptions: Set<ScreenshotViewer.Option>
    val screenshotPostprocessor: ScreenshotPostprocessor

    fun createScreenshotImage(image: BufferedImage, displayInfo: String, isTv: Boolean): ScreenshotImage

    /** Returns the list of available framing options for the given image. */
    fun getFramingOptions(screenshotImage: ScreenshotImage): List<FramingOption>

    /**
     * Returns the index of the default framing option for the given image.
     * The default framing option is ignored if [getFramingOptions] returned an empty list. */
    fun getDefaultFramingOption(framingOptions: List<FramingOption>, screenshotImage: ScreenshotImage): Int
  }
}
