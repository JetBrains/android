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
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileTypes.NativeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException

private const val SAVE_PATH_KEY = "ScreenRecorder.SavePath"
/** Amount of time before reaching recording time limit when the recording is shown as stopping. */
private const val ADVANCE_NOTICE_MILLIS = 100

/**
 * Records the screen of a device.
 *
 * TODO(b/235094713): Add tests
 */
internal class ScreenRecorder(
  private val project: Project,
  private val recordingProvider: RecordingProvider,
  deviceName: String,
) {

  private val dialogTitle = AndroidAdbUiBundle.message("screenrecord.dialog.title", deviceName)

  suspend fun recordScreen(timeLimitSec: Int) {
    require(timeLimitSec > 0)

    val recordingHandle = recordingProvider.startRecording()
    val dialog: ScreenRecorderDialog

    withContext(uiThread) {
      dialog = ScreenRecorderDialog(dialogTitle, project, timeLimitSec * 1000 - ADVANCE_NOTICE_MILLIS, recordingProvider::stopRecording)
      Disposer.register(dialog.disposable) {
        if (dialog.exitCode == CANCEL_EXIT_CODE) {
          recordingHandle.cancel()
        }
      }
      dialog.show()
    }

    try {
      recordingHandle.await()
      closeDialog(dialog)
    }
    catch (e: CancellationException) {
      closeDialog(dialog)
      try {
        recordingProvider.cancelRecording()
      }
      catch (e: Throwable) {
        thisLogger().warn(AndroidAdbUiBundle.message("screenrecord.error.cancelling"), e)
      }
      throw e
    }
    catch (e: Throwable) {
      closeDialog(dialog)
      thisLogger().warn("Screen recording failed", e)
      val message = when (val cause = e.message) {
        null -> AndroidAdbUiBundle.message("screenrecord.error")
        else -> AndroidAdbUiBundle.message("screenrecord.error.with.cause", cause)
      }
      showErrorDialog(message)
      return
    }

    pullRecording()
  }

  private suspend fun pullRecording() {
    if (!recordingProvider.doesRecordingExist()) {
      // TODO(aalbert): See if we can get the error for the non-emulator impl.
      showErrorDialog(AndroidAdbUiBundle.message("screenrecord.error"))
      return
    }

    val fileWrapper = withContext(uiThread) {
      getTargetFile(recordingProvider.fileExtension)
    } ?: return

    try {
      recordingProvider.pullRecording(fileWrapper.file.toPath())
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      val message = AndroidAdbUiBundle.message("screenrecord.error.save", fileWrapper.file)
      thisLogger().warn(message, e)
      showErrorDialog(message)
      return
    }

    withContext(uiThread) {
      handleSavedRecording(fileWrapper)
    }
  }

  private fun closeDialog(dialog: DialogWrapper) {
    UIUtil.invokeLaterIfNeeded {
      if (!dialog.isDisposed) {
        dialog.close(DialogWrapper.CLOSE_EXIT_CODE)
      }
    }
  }

  private fun showErrorDialog(errorMessage: String) {
    UIUtil.invokeLaterIfNeeded {
      Messages.showErrorDialog(errorMessage, AndroidAdbUiBundle.message("screenrecord.error.popup.title"))
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
