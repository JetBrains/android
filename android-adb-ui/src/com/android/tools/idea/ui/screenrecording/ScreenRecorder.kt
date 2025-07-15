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

import com.android.tools.idea.ui.AndroidAdbUiBundle.message
import com.android.tools.idea.ui.save.PostSaveAction
import com.android.tools.idea.ui.save.SaveConfigurationResolver
import com.intellij.ide.actions.RevealFileAction.openFile
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
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
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.time.Instant
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

  private val settings = DeviceScreenRecordingSettings.getInstance()
  private val dialogTitle = message("screenrecord.dialog.title", deviceName)
  private lateinit var recordingTimestamp: Instant

  suspend fun recordScreen(timeLimitSec: Int) {
    require(timeLimitSec > 0)

    recordingTimestamp = Instant.now()
    val recordingHandle = recordingProvider.startRecording()
    val dialog: ScreenRecorderDialog

    withContext(Dispatchers.EDT) {
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
        thisLogger().warn(message("screenrecord.error.cancelling"), e)
      }
      throw e
    }
    catch (e: Throwable) {
      closeDialog(dialog)
      thisLogger().warn("Screen recording failed", e)
      val message = when (val cause = e.message) {
        null -> message("screenrecord.error")
        else -> message("screenrecord.error.with.cause", cause)
      }
      showErrorDialog(message)
      return
    }

    pullRecording()
  }

  private suspend fun pullRecording() {
    if (!recordingProvider.doesRecordingExist()) {
      // TODO(aalbert): See if we can get the error for the non-emulator impl.
      showErrorDialog(message("screenrecord.error"))
      return
    }

    val saveConfigResolver = project.service<SaveConfigurationResolver>()
    val saveConfig = settings.saveConfig
    val expandedFilename =
      saveConfigResolver.expandFilenamePattern(saveConfig.saveLocation, saveConfig.filenameTemplate, recordingProvider.fileExtension,
                                               recordingTimestamp, settings.recordingCount + 1)
    val recordingFile = Paths.get(expandedFilename)

    try {
      recordingProvider.pullRecording(recordingFile)
      settings.recordingCount++
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      val message = message("screenrecord.error.save", recordingFile)
      thisLogger().warn(message, e)
      showErrorDialog(message)
      return
    }

    when (settings.saveConfig.postSaveAction) {
      PostSaveAction.NONE -> {}
      PostSaveAction.SHOW_IN_FOLDER -> openFile(recordingFile)
      PostSaveAction.OPEN -> openSavedFile(recordingFile)
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
      Messages.showErrorDialog(errorMessage, message("screenrecord.error.popup.title"))
    }
  }

  private suspend fun getTargetFile(extension: String): Path? {
    val properties = PropertiesComponent.getInstance(project)
    val descriptor = FileSaverDescriptor(message("screenrecord.action.save.as"), "", extension)
    return withContext(Dispatchers.EDT) {
      val saveFileDialog: FileSaverDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
      val lastPath = properties.getValue(SAVE_PATH_KEY)
      val baseDir = if (lastPath != null) LocalFileSystem.getInstance().findFileByPath(lastPath) else VfsUtil.getUserHomeDir()
      saveFileDialog.save(baseDir, getDefaultFileName(extension))?.file?.toPath()?.also {
        properties.setValue(SAVE_PATH_KEY, it.parent.toString())
      }
    }
  }

  private fun getDefaultFileName(extension: String): String {
    val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)
    val timestampSuffix = timestampFormat.format(Date())
    val filename = "Screen_recording_$timestampSuffix"
    // Add extension to filename on Mac only see: b/38447816.
    return if (SystemInfo.isMac) "$filename.$extension" else filename
  }

  /** Tries to open the given file in the associated application. */
  private suspend fun openSavedFile(file: Path) {
    withContext(Dispatchers.IO) {
      val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: return@withContext
      NativeFileType.openAssociatedApplication(virtualFile)
    }
  }
}

