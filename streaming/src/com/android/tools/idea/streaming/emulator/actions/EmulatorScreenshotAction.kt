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
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.FutureStreamObserver
import com.android.tools.idea.streaming.emulator.SkinDefinition
import com.android.tools.idea.ui.screenshot.FramingOption
import com.android.tools.idea.ui.screenshot.ScreenshotImage
import com.android.tools.idea.ui.screenshot.ScreenshotPostprocessor
import com.android.tools.idea.ui.screenshot.ScreenshotSupplier
import com.android.tools.idea.ui.screenshot.ScreenshotViewer
import com.google.common.base.Throwables
import com.google.common.util.concurrent.UncheckedExecutionException
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import java.util.concurrent.ExecutionException
import javax.imageio.IIOException
import javax.imageio.ImageIO

/**
 * Takes a screenshot of the Emulator display, saves it to a file, and opens it in editor.
 */
class EmulatorScreenshotAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.getRequiredData(CommonDataKeys.PROJECT)
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    emulatorController.getScreenshot(pngFormat(), ScreenshotReceiver(emulatorController, project))
  }

  private class ScreenshotReceiver(val emulatorController: EmulatorController, val project: Project) : EmptyStreamObserver<Image>() {

    override fun onNext(response: Image) {
      executeOnPooledThread {
        showScreenshotViewer(response, emulatorController, project)
      }
    }

    private fun showScreenshotViewer(screenshot: Image, emulatorController: EmulatorController, project: Project) {
      try {
        val imageBytes = screenshot.image
        val image = ImageIO.read(imageBytes.newInput()) ?: throw IIOException("Corrupted screenshot image")

        val backingFile = FileUtil.createTempFile("screenshot", SdkConstants.DOT_PNG).toPath()
        Files.newOutputStream(backingFile).use {
          imageBytes.writeTo(it)
        }

        val screenshotImage = ScreenshotImage(image, screenshot.format.rotation.rotationValue)
        val screenshotSupplier = MyScreenshotSupplier(emulatorController)
        val screenshotFramer = emulatorController.skinDefinition?.let { MyScreenshotPostprocessor(it) }

        ApplicationManager.getApplication().invokeLater {
          showScreenshotViewer(project, screenshotImage, backingFile, screenshotSupplier, screenshotFramer)
        }
      }
      catch (e: Exception) {
        thisLogger().error("Error while displaying screenshot viewer: ", e)
      }
    }

    private fun showScreenshotViewer(project: Project, screenshotImage: ScreenshotImage, backingFile: Path,
                                     screenshotSupplier: ScreenshotSupplier, screenshotPostprocessor: ScreenshotPostprocessor?) {
      val viewer = object : ScreenshotViewer(project, screenshotImage, backingFile, screenshotSupplier, screenshotPostprocessor,
                                             listOf(avdFrame), 0, EnumSet.noneOf(Option::class.java)) {
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
    }
  }

  private class MyScreenshotSupplier(val emulatorController: EmulatorController) : ScreenshotSupplier {

    override fun captureScreenshot(): ScreenshotImage {
      val receiver = FutureStreamObserver<Image>()
      emulatorController.getScreenshot(pngFormat(), receiver)

      try {
        val screenshot = receiver.futureResult.get()
        val image = ImageIO.read(screenshot.image.newInput()) ?: throw RuntimeException("Corrupted screenshot image")
        return ScreenshotImage(image, screenshot.format.rotation.rotationValue)
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
  }

  private class MyScreenshotPostprocessor(val skinDefinition: SkinDefinition) : ScreenshotPostprocessor {

    override fun addFrame(screenshotImage: ScreenshotImage, framingOption: FramingOption?, backgroundColor: Color?): BufferedImage {
      val image = screenshotImage.image
      val w = image.width
      val h = image.height
      val skin = skinDefinition.createScaledLayout(w, h, screenshotImage.screenshotRotationQuadrants)
      if (framingOption == null) {
        @Suppress("UndesirableClassUsage")
        val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        graphics.drawImage(image, null, 0, 0)
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.DST_OUT)
        val displayRectangle = Rectangle(0, 0, w, h)
        skin.drawFrameAndMask(graphics, displayRectangle)
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
