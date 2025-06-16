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
import com.android.tools.idea.ui.DISPLAY_ID_KEY
import com.android.tools.idea.ui.DISPLAY_INFO_PROVIDER_KEY
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
    event.presentation.isEnabled = event.getData(SCREENSHOT_PARAMETERS_KEY) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val screenshotParameters = event.getData(SCREENSHOT_PARAMETERS_KEY) ?: return
    val displayId = event.getData(DISPLAY_ID_KEY) ?: 0
    val displayInfoProvider = event.getData(DISPLAY_INFO_PROVIDER_KEY)
    val serialNumber = screenshotParameters.serialNumber

    val deviceName = screenshotParameters.deviceName
    val screenshotProvider =
        ShellCommandScreenshotProvider(project, serialNumber, screenshotParameters.deviceType, deviceName, displayId, displayInfoProvider)
    var disposable: Disposable? = screenshotProvider

    object : ScreenshotTask(project, screenshotProvider) {
      var backingFile: VirtualFile? = null

      override fun run(indicator: ProgressIndicator) {
        super.run(indicator)
        val screenshot = screenshot ?: return
        try {
          val screenshotDecorator = screenshotParameters.screenshotDecorator
          val framingOptions = screenshotParameters.getFramingOptions(screenshot)
          val decoration = ScreenshotViewer.getDefaultDecoration(screenshot, screenshotDecorator, framingOptions.firstOrNull())
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
        val screenshotPostprocessor = screenshotParameters.screenshotDecorator
        val framingOptions = screenshotParameters.getFramingOptions(screenshot)
        try {
          val defaultFrame = if (framingOptions.isNotEmpty()) screenshotParameters.getDefaultFramingOption() else 0
          val allowImageRotation = displayInfoProvider == null && screenshotParameters.deviceType == DeviceType.HANDHELD
          val viewer = ScreenshotViewer(project,
                                        screenshot,
                                        backingFile,
                                        screenshotProvider,
                                        screenshotPostprocessor,
                                        framingOptions,
                                        defaultFrame,
                                        allowImageRotation)
          viewer.show()
          Disposer.register(viewer.disposable, screenshotProvider)
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
    val SCREENSHOT_PARAMETERS_KEY = DataKey.create<ScreenshotParameters>("ScreenshotParameters")
  }
}
