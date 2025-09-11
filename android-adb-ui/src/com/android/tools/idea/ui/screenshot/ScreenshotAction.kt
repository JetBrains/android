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

import com.android.SdkConstants.DOT_PNG
import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.io.writeImage
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.android.tools.idea.ui.DISPLAY_ID_KEY
import com.android.tools.idea.ui.DISPLAY_INFO_PROVIDER_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages.showErrorDialog
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.ExceptionUtil.getMessage
import icons.StudioIcons
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Captures a screenshot of a device display. */
class ScreenshotAction : DumbAwareAction(
  message("screenshot.action.title"),
  message("screenshot.action.description"),
  StudioIcons.Common.SCREENSHOT,
) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = event.getData(ScreenshotParameters.DATA_KEY) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val screenshotParameters = event.getData(ScreenshotParameters.DATA_KEY) ?: return
    val displayId = event.getData(DISPLAY_ID_KEY) ?: PRIMARY_DISPLAY_ID
    val displayInfoProvider = event.getData(DISPLAY_INFO_PROVIDER_KEY)
    val serialNumber = screenshotParameters.serialNumber

    val deviceName = screenshotParameters.deviceName
    val screenshotProvider =
        ShellCommandScreenshotProvider(project, serialNumber, screenshotParameters.deviceType, deviceName, displayId, displayInfoProvider)
    val scope = AdbLibService.getInstance(project).session.scope.createChildScope(true)

    scope.launch {
      withModalProgress(project, message("screenshot.task.step.obtain")) {
        try {
          val screenshotImage = screenshotProvider.captureScreenshot()
          val screenshotDecorator = screenshotParameters.screenshotDecorator
          val framingOptions = screenshotParameters.getFramingOptions(screenshotImage)
          val decoration = ScreenshotViewer.getDefaultDecoration(screenshotImage, screenshotDecorator, framingOptions.firstOrNull())
          val decoratedImage = when (decoration) {
            ScreenshotDecorationOption.RECTANGULAR -> screenshotImage.image
            else -> screenshotDecorator.decorate(screenshotImage, decoration)
          }
          val processedImage = ImageUtils.scale(decoratedImage, getScreenshotScale())
          val file = FileUtil.createTempFile("screenshot", DOT_PNG).toPath()
          processedImage.writeImage("PNG", file)
          val backingFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?:
              throw IOException(message("screenshot.error.save"))
          while (backingFile.length == 0L) {
            // It's not clear why the file may have zero length after the first refresh, but it was empirically observed.
            backingFile.refresh(false, false)
          }
          val defaultFrame = if (framingOptions.isNotEmpty()) screenshotParameters.getDefaultFramingOption() else 0
          val allowImageRotation = displayInfoProvider == null && screenshotParameters.deviceType == DeviceType.HANDHELD

          ApplicationManager.getApplication().invokeLater {
            val viewer = ScreenshotViewer(project, screenshotImage, processedImage, backingFile, screenshotProvider, screenshotDecorator,
                                          framingOptions, defaultFrame, allowImageRotation)
            Disposer.register(viewer.disposable, screenshotProvider)
            viewer.show()
          }
        }
        catch (e: Throwable) {
          Disposer.dispose(screenshotProvider)
          if (e is CancellationException) {
            throw e
          }
          val cause = getMessage(e) ?: e::javaClass.name
          val message = message("screenshot.error.generic", cause)
          thisLogger().error(message, e)
          ApplicationManager.getApplication().invokeLater {
            showErrorDialog(project, message, message("screenshot.action.title"))
          }
        }
      }
    }
  }
}
