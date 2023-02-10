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
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.Date
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
  deviceName: String,
  private val clock: Clock = Clock.systemDefaultZone(),
) {

  private val dialogTitle = AndroidAdbUiBundle.message("screenrecord.dialog.title", deviceName)

  suspend fun recordScreen(timeLimitSec: Int) {
    require(timeLimitSec > 0)
    recordingProvider.startRecording()

    val start = clock.millis()

    val stoppingLatch = CountDownLatch(1)
    val dialog = ScreenRecorderDialog(dialogTitle) { stoppingLatch.countDown() }
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

  private fun millisUntilNextSecondTick(start: Long): Long {
    return 1000 - (clock.millis() - start) % 1000
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

    val fileWrapper = withContext(uiThread) {
      getTargetFile(recordingProvider.fileExtension)
    } ?: return

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
      properties.setValue(SAVE_PATH_KEY, saveFileWrapper.file.toPath().parent.toString())
    }
    return saveFileWrapper
  }

  private fun getDefaultFileName(extension: String): String {
    val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)
    val timestampSuffix = timestampFormat.format(Date())
    val filename = "Screen_recording_$timestampSuffix"
    // Add extension to filename on Mac only see: b/38447816.
    return if (SystemInfo.isMac) "$filename.$extension" else filename
  }

  @UiThread
  private fun handleSavedRecording(fileWrapper: VirtualFileWrapper) {
    val message = AndroidAdbUiBundle.message("screenrecord.action.view.recording", fileWrapper.file)
    val cancel = CommonBundle.getOkButtonText()
    val icon = Messages.getInformationIcon()
    if (RevealFileAction.isSupported()) {
      val no = AndroidAdbUiBundle.message("screenrecord.action.show.in", RevealFileAction.getFileManagerName())
      val exitCode: Int = Messages.showYesNoCancelDialog(
          project,
          message,
          dialogTitle,
          AndroidAdbUiBundle.message("screenrecord.action.open"),
          no,
          cancel,
          icon)
      when (exitCode) {
        Messages.YES -> openSavedFile(fileWrapper)
        Messages.NO -> RevealFileAction.openFile(fileWrapper.file.toPath())
        else -> {}
      }
    }
    else if (Messages.showOkCancelDialog(
        project,
        message,
        dialogTitle,
        AndroidAdbUiBundle.message("screenrecord.action.open.file"),
        cancel,
        icon) == Messages.OK) {
      openSavedFile(fileWrapper)
    }
  }

  // Tries to open the file at myLocalPath
  private fun openSavedFile(fileWrapper: VirtualFileWrapper) {
    val file = fileWrapper.virtualFile
    if (file != null) {
      NativeFileType.openAssociatedApplication(file)
    }
  }
}
