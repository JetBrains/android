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
package com.android.tools.idea.emulator.actions

import com.android.annotations.concurrency.Slow
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.emulator.EmptyStreamObserver
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.io.IdeFileUtils
import com.android.tools.idea.protobuf.ByteString
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.SystemProperties
import org.jetbrains.kotlin.idea.util.application.invokeLater
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Takes a screenshot of the Emulator display, saves it to a file, and opens it in an editor.
 */
class EmulatorScreenshotAction : AbstractEmulatorAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project: Project = event.getRequiredData(CommonDataKeys.PROJECT)
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    val imageFormat = ImageFormat.newBuilder()
      .setFormat(ImageFormat.ImgFormat.PNG)
      .build()
    emulatorController.getScreenshot(imageFormat, ScreenshotReceiver(project))
  }

  private class ScreenshotReceiver(val project: Project) : EmptyStreamObserver<Image>() {

    override fun onNext(response: Image) {
      val timestamp = Date()
      executeOnPooledThread {
        createAndOpenScreenshotFile(response.image, timestamp, project)
      }
    }
  }

  companion object {
    @JvmStatic
    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    @Slow
    @JvmStatic
    private fun createAndOpenScreenshotFile(imageContents: ByteString, timestamp: Date, project: Project) {
      val timestampSuffix = TIMESTAMP_FORMAT.format(timestamp)
      val dir = IdeFileUtils.getDesktopDirectory() ?: Paths.get(SystemProperties.getUserHome())

      for (attempt in 0..100) {
        val uniquenessSuffix = if (attempt == 0) "" else "_${attempt}"
        val filename = "Screenshot_${timestampSuffix}${uniquenessSuffix}.png"
        val file = dir.resolve(filename)
        try {
          Files.newOutputStream(file, CREATE_NEW).use {
            imageContents.writeTo(it)
          }

          val virtualFile = VfsUtil.findFile(file, true) ?: return

          invokeLater {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
          }
          return
        }
        catch (e: FileAlreadyExistsException) {
          continue
        }
        catch (e: IOException) {
          thisLogger().error("Unable to create screenshot file $file", e)
          return
        }
      }
      thisLogger().error("Unable to create screenshot file - no suitable name") // Reaching this line is extremely unlikely.
    }
  }
}