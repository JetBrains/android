/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.SdkConstants
import com.android.SdkConstants.PRIMARY_DISPLAY_ID
import com.android.emulator.control.ImageFormat
import com.android.io.writeImage
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.ImageUtils.ellipticalClip
import com.android.tools.adtui.device.SkinDefinition
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.getScreenshot
import com.android.tools.idea.ui.DISPLAY_ID_KEY
import com.android.tools.idea.ui.DISPLAY_INFO_PROVIDER_KEY
import com.android.tools.idea.ui.DisplayInfoProvider
import com.android.tools.idea.ui.screenshot.DialogLocationArbiter
import com.android.tools.idea.ui.screenshot.FramingOption
import com.android.tools.idea.ui.screenshot.ScreenshotDecorator
import com.android.tools.idea.ui.screenshot.ScreenshotImage
import com.android.tools.idea.ui.screenshot.ScreenshotProvider
import com.android.tools.idea.ui.screenshot.ScreenshotViewer
import com.android.tools.idea.ui.screenshot.getScreenshotScale
import com.google.common.base.Throwables.throwIfUnchecked
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.IIOException
import javax.imageio.ImageIO

/** Takes a screenshot of the Emulator display, saves it to a file, and opens it in editor. */
class EmulatorScreenshotAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.project ?: return
    val emulatorController = getEmulatorController(event) ?: return
    val displayInfoProvider = event.getData(DISPLAY_INFO_PROVIDER_KEY) ?: return
    val displayIds = when {
      !StudioFlags.MULTI_DISPLAY_SCREENSHOTS.get() || event.place == "EmulatorView" -> intArrayOf(event.getData(DISPLAY_ID_KEY) ?: 0)
      else -> displayInfoProvider.getIdsOfAllDisplays()
    }

    val scope = emulatorController.createCoroutineScope()
    val dialogLocationArbiter = if (displayIds.size > 1) DialogLocationArbiter() else null
    var errorCount = 0

    for (displayId in displayIds) {
      scope.launch {
        // Use a modal progress dialog only for the first display.
        withProgress(project, "Obtaining screenshot from device\u2026", displayId == displayIds[0]) {
          try {
            val screenshotProto = emulatorController.getScreenshot(createScreenshotRequest(displayId))
            val format = screenshotProto.format
            val skin = displayInfoProvider.getSkin(displayId)
            val imageBytes = screenshotProto.image
            val image = ImageIO.read(imageBytes.newInput()) ?: throw IIOException("Corrupted screenshot image")
            val emulatorConfig = emulatorController.emulatorConfig
            val displaySize = displayInfoProvider.getDisplaySize(displayId)
            val screenshotImage = ScreenshotImage(image, format.rotation.rotationValue,
                                                  emulatorConfig.deviceType, emulatorConfig.avdName, displayId, displaySize)
            val screenshotDecorator = EmulatorScreenshotDecorator(skin)
            val framingOptions = if (displayId == PRIMARY_DISPLAY_ID && skin != null) listOf(AvdFrame()) else listOf()
            val decoration = ScreenshotViewer.getDefaultDecoration(screenshotImage, screenshotDecorator, framingOptions.firstOrNull())
            val processedImage = ImageUtils.scale(screenshotDecorator.decorate(screenshotImage, decoration), getScreenshotScale())
            val file = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
            processedImage.writeImage("PNG", file)
            val backingFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?:
                throw IOException("Unable to save screenshot")
            val screenshotProvider = EmulatorScreenshotProvider(emulatorController, displayId, displayInfoProvider)
            ApplicationManager.getApplication().invokeLater {
              val viewer = ScreenshotViewer(project, screenshotImage, processedImage, backingFile, screenshotProvider, screenshotDecorator,
                                            framingOptions, 0, false, dialogLocationArbiter)
              viewer.show()
            }
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: Throwable) {
            val message = "Error obtaining screenshot"
            thisLogger().error(message, e)
            if (++errorCount == 1) { // Show error dialog no more than once.
              ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(project, message, "Take Screenshot")
              }
            }
          }
        }
      }
    }
  }

  private suspend fun <T> withProgress(project: Project, title: String, modal: Boolean, action: suspend CoroutineScope.() -> T): T {
    return when {
      modal -> withModalProgress(project, title, action)
      else -> withBackgroundProgress(project, title, action)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class AvdFrame : FramingOption {
    override val displayName = "Show Device Frame"
  }

  private class EmulatorScreenshotProvider(
    private val emulator: EmulatorController,
    private val displayId: Int,
    private val displayInfoProvider: DisplayInfoProvider,
  ) : ScreenshotProvider {

    init {
      Disposer.register(emulator, this)
    }

    override suspend fun captureScreenshot(): ScreenshotImage {
      try {
        val screenshot = emulator.getScreenshot(createScreenshotRequest(displayId))
        val emulatorConfig = emulator.emulatorConfig
        val deviceName = emulatorConfig.avdName
        val image = ImageIO.read(screenshot.image.newInput()) ?: throw RuntimeException("Corrupted screenshot image")
        return ScreenshotImage(image, screenshot.format.rotation.rotationValue, emulatorConfig.deviceType, deviceName, displayId,
                               displayInfoProvider.getDisplaySize(displayId))
      }
      catch (e: Throwable) {
        throwIfUnchecked(e)
        throw RuntimeException(e)
      }
    }

    override fun dispose() {
    }
  }

  private class EmulatorScreenshotDecorator(private val skinDefinition: SkinDefinition?) : ScreenshotDecorator {

    override val canClipToDisplayShape: Boolean
      get() = skinDefinition != null

    override fun decorate(screenshotImage: ScreenshotImage, framingOption: FramingOption?, backgroundColor: Color?): BufferedImage {
      // Decorations are applied only to the primary display.
      if (skinDefinition == null || screenshotImage.displayId != PRIMARY_DISPLAY_ID) {
        return if (screenshotImage.isRoundDisplay) ellipticalClip(screenshotImage.image, backgroundColor) else screenshotImage.image
      }
      return screenshotImage.decorate(framingOption != null, skinDefinition, backgroundColor)
    }
  }
}

private fun createScreenshotRequest(displayId: Int) =
    ImageFormat.newBuilder().setFormat(ImageFormat.ImgFormat.PNG).setDisplay(displayId).build()
