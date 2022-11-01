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
package com.android.tools.idea.ui.screenrecording

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.intellij.CommonBundle
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileTypes.NativeFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val SAVE_PATH_KEY = "ScreenRecorder.SavePath"

/**
 * Records the screen of a device.
 *
 * Mostly based on com.android.tools.idea.ddms.actions.ScreenRecorderTask but changed to use coroutines and AdbLib.
 *
 * TODO(b/235094713): Add tests
 */
internal class ScreenRecorder(
  private val project: Project,
  private val recordingProvider: RecordingProvider,
  private val clock: Clock = Clock.systemDefaultZone(),
) {
  suspend fun recordScreen(timeLimitSec: Int) {
    require(timeLimitSec > 0)
    recordingProvider.startRecording()

    val start = clock.millis()

    val stoppingLatch = CountDownLatch(1)
    val dialog = ScreenRecorderDialog(AndroidAdbUiBundle.message("screenrecord.action.title")) { stoppingLatch.countDown() }
    val dialogWrapper: DialogWrapper
    withContext(uiThread) {
      dialogWrapper = dialog.createWrapper(project)
      dialogWrapper.show()
    }

    try {
      withContext(Dispatchers.IO) {
        while (!stoppingLatch.await(millisUntilNextSecondTick(start), MILLISECONDS) && clock.millis() - start < timeLimitSec * 1000) {
          withContext(uiThread) {
            dialog.recordingTimeMillis = clock.millis() - start
          }
        }
      }
      withContext(uiThread) {
        dialog.recordingLabelText = AndroidAdbUiBundle.message("screenrecord.action.stopping")
      }
      // TODO: Call recordingProvider.stopRecording() unconditionally when b/256957515 is fixed.
      if (clock.millis() - start < timeLimitSec * 1000) {
        recordingProvider.stopRecording()
      }
      else {
        delay(1000)
      }
    }
    catch (e: InterruptedException) {
      recordingProvider.stopRecording()
      throw ProcessCanceledException()
    }
    finally {
      withContext(uiThread) {
        dialogWrapper.close(DialogWrapper.CLOSE_EXIT_CODE)
      }
    }

    pullRecording()
  }

  private suspend fun pullRecording() {
    if (!recordingProvider.doesRecordingExist()) {
      // TODO(aalbert): See if we can get the error for the non-emulator impl.
      withContext(uiThread) {
        Messages.showErrorDialog(
          AndroidAdbUiBundle.message("screenrecord.error"),
          AndroidAdbUiBundle.message("screenrecord.error.popup.title"))
      }
      return
    }

    val fileWrapper: VirtualFileWrapper?
    withContext(uiThread) {
      fileWrapper = getTargetFile(recordingProvider.fileExtension)
    }
    if (fileWrapper == null) {
      return
    }

    recordingProvider.pullRecording(fileWrapper.file.toPath())

    withContext(uiThread) {
      handleSavedRecording(fileWrapper)
    }
  }

  private fun getTargetFile(extension: String): VirtualFileWrapper? {
    val properties = PropertiesComponent.getInstance(project)
    val descriptor = FileSaverDescriptor(AndroidAdbUiBundle.message("screenrecord.action.save.as"), "", extension)
    val saveFileDialog: FileSaverDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
    val lastPath = properties.getValue(SAVE_PATH_KEY)
    val baseDir = if (lastPath != null) LocalFileSystem.getInstance().findFileByPath(lastPath) else VfsUtil.getUserHomeDir()
    val saveFileWrapper = saveFileDialog.save(baseDir, getDefaultFileName(extension))
    if (saveFileWrapper != null) {
      val saveFile = saveFileWrapper.file
      properties.setValue(SAVE_PATH_KEY, saveFile.path)
    }
    return saveFileWrapper
  }

  private fun millisUntilNextSecondTick(start: Long): Long {
    return 1000 - (clock.millis() - start) % 1000
  }

  @UiThread
  private fun handleSavedRecording(fileWrapper: VirtualFileWrapper) {
    val path = fileWrapper.file.absolutePath
    val message = AndroidAdbUiBundle.message("screenrecord.action.view.recording", path)
    val cancel = CommonBundle.getOkButtonText()
    val icon = Messages.getInformationIcon()
    if (RevealFileAction.isSupported()) {
      val no = AndroidAdbUiBundle.message("screenrecord.action.show.in", RevealFileAction.getFileManagerName())
      val exitCode: Int = Messages.showYesNoCancelDialog(
        project,
        message,
        AndroidAdbUiBundle.message("screenrecord.action.title"),
        AndroidAdbUiBundle.message("screenrecord.action.open"),
        no,
        cancel,
        icon)
      if (exitCode == Messages.YES) {
        openSavedFile(fileWrapper)
      }
      else if (exitCode == Messages.NO) {
        RevealFileAction.openFile(File(path))
      }
    }
    else if (Messages.showOkCancelDialog(
        project,
        message,
        AndroidAdbUiBundle.message("screenrecord.action.title"),
        AndroidAdbUiBundle.message("screenrecord.action.open.file"),
        cancel,
        icon) == Messages.OK) {
      openSavedFile(fileWrapper)
    }
  }
}

private fun getDefaultFileName(extension: String): String {
  val now = Calendar.getInstance()
  val fileName = "device-%tF-%tH%tM%tS"
  // Add extension to filename on Mac only see: b/38447816.
  return String.format(Locale.US, if (SystemInfo.isMac) "$fileName.$extension" else fileName, now, now, now, now)
}

// Tries to open the file at myLocalPath
private fun openSavedFile(fileWrapper: VirtualFileWrapper) {
  val file = fileWrapper.virtualFile
  if (file != null) {
    NativeFileType.openAssociatedApplication(file)
  }
}
