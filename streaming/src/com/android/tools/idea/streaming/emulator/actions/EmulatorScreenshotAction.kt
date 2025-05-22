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
import com.android.tools.adtui.device.SkinDefinition
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.getScreenshot
import com.android.tools.idea.ui.DISPLAY_ID_KEY
import com.android.tools.idea.ui.DISPLAY_INFO_PROVIDER_KEY
import com.android.tools.idea.ui.screenshot.DialogLocationArbiter
import com.android.tools.idea.ui.screenshot.FramingOption
import com.android.tools.idea.ui.screenshot.ScreenshotDecorator
import com.android.tools.idea.ui.screenshot.ScreenshotImage
import com.android.tools.idea.ui.screenshot.ScreenshotProvider
import com.android.tools.idea.ui.screenshot.ScreenshotViewer
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
import com.intellij.platform.ide.progress.withModalProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
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

    if (displayIds.isEmpty()) { // Should not happen but check for safety.
      return
    }
    val scope = emulatorController.createCoroutineScope()
    val waitingForScreenshot = CompletableDeferred<Unit>()
    scope.launch {
      withModalProgress(project, "Obtaining screenshot from device\u2026") {
        waitingForScreenshot.await()
      }
    }

    val dialogLocationArbiter = if (displayIds.size > 1) DialogLocationArbiter() else null
    var errorCount = 0
    for (displayId in displayIds) {
      scope.launch {
        try {
          val screenshotProto = emulatorController.getScreenshot(createScreenshotRequest(displayId))
          val format = screenshotProto.format
          val skin = displayInfoProvider.getSkin(displayId)
          val imageBytes = screenshotProto.image
          val image = ImageIO.read(imageBytes.newInput()) ?: throw IIOException("Corrupted screenshot image")

          val emulatorConfig = emulatorController.emulatorConfig
          val screenshotImage = ScreenshotImage(image, format.rotation.rotationValue,
                                                emulatorConfig.deviceType, emulatorConfig.avdName, displayId)
          val screenshotDecorator = EmulatorScreenshotDecorator(displayId, skin)
          val framingOptions = if (displayId == PRIMARY_DISPLAY_ID && skin != null) listOf(AvdFrame()) else listOf()
          val decoration = ScreenshotViewer.getDefaultDecoration(screenshotImage, screenshotDecorator, framingOptions.firstOrNull())
          val processedImage = screenshotDecorator.decorate(screenshotImage, decoration)
          val file = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
          processedImage.writeImage("PNG", file)
          val backingFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?:
              throw IOException("Unable to save screenshot")
          val screenshotProvider = EmulatorScreenshotProvider(emulatorController, displayId)
          ApplicationManager.getApplication().invokeLater {
            val viewer = ScreenshotViewer(project, screenshotImage, backingFile, screenshotProvider, screenshotDecorator,
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
        finally {
          waitingForScreenshot.complete(Unit)
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class AvdFrame : FramingOption {
    override val displayName = "Show Device Frame"
  }

  private class EmulatorScreenshotProvider(private val emulator: EmulatorController, private val displayId: Int) : ScreenshotProvider {

    init {
      Disposer.register(emulator, this)
    }

    override suspend fun captureScreenshot(): ScreenshotImage {
      try {
        val screenshot = emulator.getScreenshot(createScreenshotRequest(displayId))
        val emulatorConfig = emulator.emulatorConfig
        val deviceName = emulatorConfig.avdName
        val image = ImageIO.read(screenshot.image.newInput()) ?: throw RuntimeException("Corrupted screenshot image")
        return ScreenshotImage(image, screenshot.format.rotation.rotationValue, emulatorConfig.deviceType, deviceName, displayId)
      }
      catch (e: Throwable) {
        throwIfUnchecked(e)
        throw RuntimeException(e)
      }
    }

    override fun dispose() {
    }
  }

  private class EmulatorScreenshotDecorator(private val displayId: Int, private val skinDefinition: SkinDefinition?) : ScreenshotDecorator {

    override val canClipToDisplayShape: Boolean
      get() = true

    override fun decorate(screenshotImage: ScreenshotImage, framingOption: FramingOption?, backgroundColor: Color?): BufferedImage {
      if (displayId != PRIMARY_DISPLAY_ID) {
        return screenshotImage.image // Decorations are applied only to the primary display.
      }
      val image = screenshotImage.image
      val w = image.width
      val h = image.height
      val skin = skinDefinition?.createScaledLayout(w, h, screenshotImage.screenshotOrientationQuadrants)
      val arcWidth = skin?.displayCornerSize?.width ?: 0
      val arcHeight = skin?.displayCornerSize?.height ?: 0
      if (framingOption == null || skin == null) {
        @Suppress("UndesirableClassUsage")
        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        val displayRectangle = Rectangle(0, 0, w, h)
        graphics.drawImageWithRoundedCorners(image, displayRectangle, arcWidth, arcHeight)
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OUT)
        skin?.drawFrameAndMask(graphics, displayRectangle)
        if (backgroundColor != null) {
          graphics.color = backgroundColor
          graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OVER)
          graphics.fillRect(0, 0, image.width, image.height)
        }
        graphics.dispose()
        return result
      }

      val frameRectangle = skin.frameRectangle
      @Suppress("UndesirableClassUsage")
      val result = BufferedImage(frameRectangle.width, frameRectangle.height, BufferedImage.TYPE_INT_ARGB)
      val graphics = result.createGraphics()
      val displayRectangle = Rectangle(-frameRectangle.x, -frameRectangle.y, w, h)
      graphics.drawImageWithRoundedCorners(image, displayRectangle, arcWidth, arcHeight)

      skin.drawFrameAndMask(graphics, displayRectangle)
      graphics.dispose()
      return result
    }

    private fun Graphics2D.drawImageWithRoundedCorners(image: BufferedImage, displayRectangle: Rectangle, arcWidth: Int, arcHeight: Int) {
      if (arcWidth > 0 && arcHeight > 0) {
        clip = Area(RoundRectangle2D.Double(displayRectangle.x.toDouble(), displayRectangle.y.toDouble(),
                                            displayRectangle.width.toDouble(), displayRectangle.height.toDouble(),
                                            arcWidth.toDouble(), arcHeight.toDouble()))
      }
      drawImage(image, null, displayRectangle.x, displayRectangle.y)
      clip = null
    }
  }
}

private fun createScreenshotRequest(displayId: Int) =
    ImageFormat.newBuilder().setFormat(ImageFormat.ImgFormat.PNG).setDisplay(displayId).build()
