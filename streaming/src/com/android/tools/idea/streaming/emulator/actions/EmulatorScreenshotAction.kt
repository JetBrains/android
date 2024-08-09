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
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.io.writeImage
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorView
import com.android.tools.idea.streaming.emulator.FutureStreamObserver
import com.android.tools.idea.ui.screenshot.FramingOption
import com.android.tools.idea.ui.screenshot.ScreenshotDecorator
import com.android.tools.idea.ui.screenshot.ScreenshotImage
import com.android.tools.idea.ui.screenshot.ScreenshotSupplier
import com.android.tools.idea.ui.screenshot.ScreenshotViewer
import com.google.common.base.Throwables
import com.google.common.util.concurrent.UncheckedExecutionException
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.ExecutionException
import javax.imageio.IIOException
import javax.imageio.ImageIO

/**
 * Takes a screenshot of the Emulator display, saves it to a file, and opens it in editor.
 */
class EmulatorScreenshotAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.getData(CommonDataKeys.PROJECT) ?: return
    val emulatorView = getEmulatorView(event) ?: return
    emulatorView.emulator.getScreenshot(pngFormat(), ScreenshotReceiver(emulatorView, project))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private class ScreenshotReceiver(val emulatorView: EmulatorView, val project: Project) : EmptyStreamObserver<Image>() {

    override fun onNext(message: Image) {
      executeOnPooledThread {
        showScreenshotViewer(message)
      }
    }

    private fun showScreenshotViewer(screenshot: Image) {
      try {
        val imageBytes = screenshot.image
        val image = ImageIO.read(imageBytes.newInput()) ?: throw IIOException("Corrupted screenshot image")

        val screenshotDecorator = MyScreenshotDecorator(emulatorView)
        val emulatorController = emulatorView.emulator
        val framingOptions = if (emulatorController.getSkin() == null) listOf() else listOf(avdFrame)
        val screenshotImage = ScreenshotImage(image, screenshot.format.rotation.rotationValue, emulatorController.emulatorConfig.deviceType)
        val decoration = ScreenshotViewer.getDefaultDecoration(screenshotImage, screenshotDecorator, framingOptions.firstOrNull(), project)
        val processedImage = screenshotDecorator.decorate(screenshotImage, decoration)
        val file = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
        processedImage.writeImage("PNG", file)
        val backingFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: throw IOException("Unable to save screenshot")
        val screenshotSupplier = MyScreenshotSupplier(emulatorController)

        ApplicationManager.getApplication().invokeLater {
          val viewer = ScreenshotViewer(project, screenshotImage, backingFile, screenshotSupplier, screenshotDecorator, framingOptions, 0,
                                        EnumSet.noneOf(ScreenshotViewer.Option::class.java))
          viewer.show()
        }
      }
      catch (e: Exception) {
        thisLogger().error("Error while displaying screenshot viewer", e)
      }
    }
  }

  private class MyScreenshotSupplier(val emulatorController: EmulatorController) : ScreenshotSupplier {

    init {
      Disposer.register(emulatorController, this)
    }

    override fun captureScreenshot(): ScreenshotImage {
      val receiver = FutureStreamObserver<Image>()
      emulatorController.getScreenshot(pngFormat(), receiver)

      try {
        val screenshot = receiver.futureResult.get()
        val image = ImageIO.read(screenshot.image.newInput()) ?: throw RuntimeException("Corrupted screenshot image")
        return ScreenshotImage(image, screenshot.format.rotation.rotationValue, emulatorController.emulatorConfig.deviceType)
      }
      catch (e: InterruptedException) {
        throw ProcessCanceledException()
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }
      catch (e: ExecutionException) {
        Throwables.throwIfUnchecked(e.cause!!)
        throw UncheckedExecutionException(e.cause!!)
      }
      catch (e: UncheckedExecutionException) {
        Throwables.throwIfUnchecked(e.cause!!)
        throw e
      }
    }

    override fun dispose() {
    }
  }

  private class MyScreenshotDecorator(val emulatorView: EmulatorView) : ScreenshotDecorator {

    override fun decorate(screenshotImage: ScreenshotImage, framingOption: FramingOption?, backgroundColor: Color?): BufferedImage {
      val image = screenshotImage.image
      val w = image.width
      val h = image.height
      val skinDefinition = emulatorView.emulator.getSkin(emulatorView.currentPosture?.posture)
      val skin = skinDefinition?.createScaledLayout(w, h, screenshotImage.screenshotRotationQuadrants)
      if (framingOption == null || skin == null) {
        @Suppress("UndesirableClassUsage")
        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        graphics.drawImage(image, null, 0, 0)
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OUT)
        val displayRectangle = Rectangle(0, 0, w, h)
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
      graphics.drawImage(image, null, displayRectangle.x, displayRectangle.y)
      skin.drawFrameAndMask(graphics, displayRectangle)
      graphics.dispose()
      return result
    }

    override val canClipToDisplayShape: Boolean
      get() = true
  }
}

private val avdFrame = object : FramingOption {
  override val displayName = "Show Device Frame"
}

private fun pngFormat() = ImageFormat.newBuilder().setFormat(ImageFormat.ImgFormat.PNG).build()
