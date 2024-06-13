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
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import icons.StudioIcons
import java.awt.image.BufferedImage
import java.io.IOException

/**
 * Captures a screenshot of the device display.
 */
class ScreenshotAction : DumbAwareAction(
  message("screenshot.action.title"),
  message("screenshot.action.description"),
  StudioIcons.Common.SCREENSHOT,
) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
      var backingFile: VirtualFile? = null

      override fun run(indicator: ProgressIndicator) {
        super.run(indicator)
        val screenshot = screenshot ?: return
        try {
          val screenshotDecorator = screenshotOptions.screenshotDecorator
          val framingOptions = screenshotOptions.getFramingOptions(screenshot)
          val decoration = ScreenshotViewer.getDefaultDecoration(screenshot, screenshotDecorator, framingOptions.firstOrNull(), project)
          val processedImage = screenshotDecorator.decorate(screenshot, decoration)
          indicator.checkCanceled()
          val file = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
          processedImage.writeImage("PNG", file)
          indicator.checkCanceled()
          val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?:
              throw IOException(message("screenshot.error.save"))
          while (virtualFile.length == 0L) {
            // It's not clear why the file may have zero length after the first refresh, but it was empirically observed.
            virtualFile.refresh(false, false)
          }
          backingFile = virtualFile
          indicator.checkCanceled()
        }
        catch (e: IOException) {
          indicator.checkCanceled()
          thisLogger().warn("Error while saving screenshot file", e)
          error = message("screenshot.error.generic", e)
        }
      }

      override fun onSuccess() {
        val error = error
        if (error != null) {
          Messages.showErrorDialog(project, error, message("screenshot.action.title"))
          return
        }

        showScreenshotViewer(screenshot!!, backingFile!!)
      }

      override fun onFinished() {
        disposable?.let {
          Disposer.dispose(it)
        }
      }

      private fun showScreenshotViewer(screenshot: ScreenshotImage, backingFile: VirtualFile) {
        val screenshotPostprocessor = screenshotOptions.screenshotDecorator
        val framingOptions = screenshotOptions.getFramingOptions(screenshot)
        try {
          val defaultFrame =
              if (framingOptions.isNotEmpty()) screenshotOptions.getDefaultFramingOption(framingOptions, screenshot) else 0
          val viewer = ScreenshotViewer(project,
                                        screenshot,
                                        backingFile,
                                        screenshotSupplier,
                                        screenshotPostprocessor,
                                        framingOptions,
                                        defaultFrame,
                                        screenshotOptions.screenshotViewerOptions)
          viewer.show()
          Disposer.register(viewer.disposable, screenshotSupplier)
          disposable = null
        }
        catch (e: Exception) {
          thisLogger().warn("Error while displaying screenshot viewer", e)
          Messages.showErrorDialog(project, message("screenshot.error.generic", e), message("screenshot.action.title"))
        }
      }
    }.queue()
  }

  companion object {
    val SCREENSHOT_OPTIONS_KEY = DataKey.create<ScreenshotOptions>("ScreenshotOptions")
  }

  interface ScreenshotOptions {
    val serialNumber: String
    val screenshotViewerOptions: Set<ScreenshotViewer.Option>
    val screenshotDecorator: ScreenshotDecorator

    fun createScreenshotImage(image: BufferedImage, displayInfo: String, deviceType: DeviceType): ScreenshotImage

    /** Returns the list of available framing options for the given image. */
    fun getFramingOptions(screenshotImage: ScreenshotImage): List<FramingOption>

    /**
     * Returns the index of the default framing option for the given image.
     * The default framing option is ignored if [getFramingOptions] returned an empty list. */
    fun getDefaultFramingOption(framingOptions: List<FramingOption>, screenshotImage: ScreenshotImage): Int
  }
}
