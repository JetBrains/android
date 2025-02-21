/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.annotations.concurrency.UiThread
import com.android.prefs.AndroidLocationsException
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.tools.idea.adblib.AdbLibApplicationService
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeAvdManagers
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import icons.StudioIcons
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.nio.file.Path
import java.time.Duration

/**
 * A [DumbAwareAction] that records the screen.
 *
 * Based on com.android.tools.idea.ddms.actions.ScreenRecorderAction but uses AdbLib instead of DDMLIB.
 *
 * TODO(b/235094713): Add more tests. Existing tests are just for completeness with tests in DDMS
 */
class ScreenRecorderAction : DumbAwareAction(
  AndroidAdbUiBundle.message("screenrecord.action.title"),
  AndroidAdbUiBundle.message("screenrecord.action.description"),
  StudioIcons.Common.VIDEO_CAPTURE
) {

  private val logger = thisLogger()

  /** Serial numbers of devices that are currently recording. */
  private val recordingInProgress = mutableSetOf<String>()

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(event: AnActionEvent) {
    val params = event.getData(SCREEN_RECORDER_PARAMETERS_KEY)
    val project = event.project
    event.presentation.isEnabled =
        params != null && project != null && isRecordingSupported(params, project) && !recordingInProgress.contains(params.serialNumber)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val params = event.getData(SCREEN_RECORDER_PARAMETERS_KEY) ?: return
    val project = event.project ?: return
    val isEmulator = params.serialNumber.isEmulator()
    val options = ScreenRecorderPersistentOptions.getInstance()
    val dialog = ScreenRecorderOptionsDialog(options, project, isEmulator, params.featureLevel)
    if (dialog.showAndGet()) {
      startRecordingAsync(options, params, isEmulator && options.useEmulatorRecording, project)
    }
  }

  private fun isRecordingSupported(params: Parameters, project: Project): Boolean {
    return params.featureLevel >= 19 &&
           ScreenRecordingSupportedCache.getInstance(project).isScreenRecordingSupported(params.serialNumber, params.featureLevel)
  }

  @UiThread
  private fun startRecordingAsync(
      options: ScreenRecorderPersistentOptions,
      params: Parameters,
      useEmulatorRecording: Boolean,
      project: Project,
  ) {
    val adbSession: AdbSession = AdbLibApplicationService.instance.session
    val manager: AvdManager? = getVirtualDeviceManager()
    val serialNumber = params.serialNumber
    val avdName = params.avdId
    val emulatorRecordingFile =
        if (manager != null && useEmulatorRecording && avdName != null) getTemporaryVideoPathForVirtualDevice(avdName, manager) else null
    recordingInProgress.add(serialNumber)

    val disposableParent = params.recordingLifetimeDisposable
    val coroutineScope = disposableParent.createCoroutineScope()
    val exceptionHandler = coroutineExceptionHandler(project, coroutineScope)
    coroutineScope.launch(exceptionHandler) {
      val showTouchEnabled = isShowTouchEnabled(adbSession, serialNumber)
      val size = getDeviceScreenSize(adbSession, serialNumber)
      val timeLimitSec = if (emulatorRecordingFile != null || params.featureLevel >= 34) MAX_RECORDING_DURATION_MINUTES * 60 else 0
      val recorderOptions = options.toScreenRecorderOptions(size, timeLimitSec)
      if (recorderOptions.showTouches != showTouchEnabled) {
        setShowTouch(adbSession, serialNumber, recorderOptions.showTouches)
      }
      try {
        val recodingProvider = when (emulatorRecordingFile) {
          null -> ShellCommandRecordingProvider(
            disposableParent,
            serialNumber,
            REMOTE_PATH.format(System.currentTimeMillis()),
            recorderOptions,
            adbSession)

          else -> EmulatorConsoleRecordingProvider(
            disposableParent,
            serialNumber,
            emulatorRecordingFile,
            recorderOptions,
            adbSession)
        }
        val timeLimit = if (timeLimitSec > 0) timeLimitSec else MAX_RECORDING_DURATION_MINUTES_LEGACY * 60
        val recorder = ScreenRecorder(project, recodingProvider, ScreenRecorderPersistentOptions.getInstance(), params.deviceName)
        recorder.recordScreen(timeLimit)
      }
      finally {
        if (recorderOptions.showTouches != showTouchEnabled) {
          setShowTouch(adbSession, serialNumber, showTouchEnabled)
        }
        withContext(Dispatchers.EDT) {
          recordingInProgress.remove(serialNumber)
          ActivityTracker.getInstance().inc()
        }
      }
    }
  }

  private fun getVirtualDeviceManager(): AvdManager? {
    return try {
      IdeAvdManagers.getAvdManager(AndroidSdks.getInstance().tryToChooseSdkHandler())
    }
    catch (exception: AndroidLocationsException) {
      logger.warn(exception)
      null
    }
  }

  private suspend fun getDeviceScreenSize(adbSession: AdbSession, serialNumber: String): Dimension? {
    try {
      //TODO: Check for `stderr` and `exitCode` to report errors
      val out = execute(adbSession, serialNumber, "wm size")
      val matchResult = WM_SIZE_OUTPUT_REGEX.find(out)
      if (matchResult == null) {
        logger.warn("Unexpected output from 'wm size': $out")
        return null
      }
      val width = matchResult.groups["width"]
      val height = matchResult.groups["height"]
      if (width == null || height == null) {
        logger.warn("Unexpected output from 'wm size': $out")
        return null
      }
      return Dimension(width.value.toInt(), height.value.toInt())
    }
    catch (e: Exception) {
      logger.warn("Failed to get device screen size.", e)
    }
    return null
  }

  private suspend fun execute(adbSession: AdbSession, serialNumber: String, command: String): String =
    //TODO: Check for `stderr` and `exitCode` to report errors
    adbSession.deviceServices.shellAsText(DeviceSelector.fromSerialNumber(serialNumber), command, commandTimeout = COMMAND_TIMEOUT).stdout

  private suspend fun setShowTouch(adbSession: AdbSession, serialNumber: String, isEnabled: Boolean) {
    val value = if (isEnabled) 1 else 0
    try {
      //TODO: Check for `stderr` and `exitCode` to report errors
      execute(adbSession, serialNumber, "settings put system show_touches $value")
    }
    catch (e: Exception) {
      logger.warn("Failed to set show taps to $isEnabled", e)
    }
  }

  private suspend fun isShowTouchEnabled(adbSession: AdbSession, serialNumber: String): Boolean {
    //TODO: Check for `stderr` and `exitCode` to report errors
    val out = execute(adbSession, serialNumber, "settings get system show_touches")
    return out.trim() == "1"
  }

  private fun coroutineExceptionHandler(project: Project, coroutineScope: CoroutineScope) = CoroutineExceptionHandler { _, throwable ->
    logger.warn("Failed to record screen", throwable)
    coroutineScope.launch(Dispatchers.EDT) {
      Messages.showErrorDialog(
        project,
        AndroidAdbUiBundle.message("screenrecord.error.exception", throwable.toString()),
        AndroidAdbUiBundle.message("screenrecord.action.title"))
    }
  }

  companion object {
    @JvmStatic
    val SCREEN_RECORDER_PARAMETERS_KEY = DataKey.create<Parameters>("ScreenRecorderParameters")

    const val MAX_RECORDING_DURATION_MINUTES = 30 // Emulator or Android 14+.
    const val MAX_RECORDING_DURATION_MINUTES_LEGACY = 3

    private const val REMOTE_PATH = "/sdcard/screen-recording-%d.mp4"
    private val WM_SIZE_OUTPUT_REGEX = Regex("(?<width>\\d+)x(?<height>\\d+)")
    private const val EMU_TMP_FILENAME = "tmp.webm"
    private val COMMAND_TIMEOUT = Duration.ofSeconds(2)

    private fun getTemporaryVideoPathForVirtualDevice(avdName: String, manager: AvdManager): Path? {
      val virtualDevice: AvdInfo = manager.getAvd(avdName, true) ?: return null
      return virtualDevice.dataFolderPath.resolve(EMU_TMP_FILENAME)
    }

    private fun String.isEmulator() = startsWith("emulator-")
  }

  data class Parameters(
    val deviceName: String,
    val serialNumber: String,
    val featureLevel: Int,
    val avdId: String?,
    val displayId: Int,
    val recordingLifetimeDisposable: Disposable,
  )
}
