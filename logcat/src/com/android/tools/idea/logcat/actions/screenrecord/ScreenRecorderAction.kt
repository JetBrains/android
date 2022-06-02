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
package com.android.tools.idea.logcat.actions.screenrecord

import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsText
import com.android.annotations.concurrency.UiThread
import com.android.prefs.AndroidLocationsException
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import icons.StudioIcons
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private const val REMOTE_PATH = "/sdcard/screen-recording-%d.mp4"
private val WM_SIZE_OUTPUT_REGEX = Regex("(?<width>\\d+)x(?<height>\\d+)")
private const val EMU_TMP_FILENAME = "tmp.webm"
private val COMMAND_TIMEOUT = Duration.ofSeconds(2)

/**
 * A [DumbAwareAction] that records the screen.
 *
 * Based on com.android.tools.idea.ddms.actions.ScreenRecorderAction but uses AdbLib instead of DDMLIB.
 *
 * TODO(b/235094713): Add more tests. Existing tests are just for completeness with tests in DDMS
 */
internal class ScreenRecorderAction(
  private val disposableParent: Disposable,
  private val project: Project,
  private val adbLibSession: AdbLibSession = AdbLibService.getSession(project),
  private val clock: Clock = Clock.systemDefaultZone(),
  coroutineContext: CoroutineContext = EmptyCoroutineContext
) : DumbAwareAction(
  LogcatBundle.message("screenrecord.action.title"),
  LogcatBundle.message("screenrecord.action.description"), StudioIcons.Logcat.Toolbar.VIDEO_CAPTURE
) {
  private val logger = thisLogger()
  private val exceptionHandler = coroutineExceptionHandler()
  private val screenRecordingSupportedCache = ScreenRecordingSupportedCache.getInstance(project)
  private val coroutineScope = AndroidCoroutineScope(disposableParent, coroutineContext)

  /**
   * Devices that are currently recording.
   */
  private val recordingInProgress = mutableSetOf<String>()

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val serialNumber = event.getData(SERIAL_NUMBER_KEY)
    val sdk = event.getData(SDK_KEY) ?: 0

    if (serialNumber == null || sdk < 19) {
      presentation.isEnabled = false
      return
    }
    presentation.isEnabled = screenRecordingSupportedCache.isScreenRecordingSupported(serialNumber, sdk)

  }

  override fun actionPerformed(event: AnActionEvent) {
    val serialNumber = event.getData(SERIAL_NUMBER_KEY) ?: return
    val dialog = ScreenRecorderOptionsDialog(project, serialNumber.isEmulator())
    if (!dialog.showAndGet()) {
      return
    }

    startRecordingAsync(dialog.useEmulatorRecording, serialNumber, event.getData(AVD_NAME_KEY))
  }

  private suspend fun execute(serialNumber: String, command: String) =
    adbLibSession.deviceServices.shellAsText(DeviceSelector.fromSerialNumber(serialNumber), command, commandTimeout = COMMAND_TIMEOUT)

  @UiThread
  private fun startRecordingAsync(useEmulatorRecording: Boolean, serialNumber: String, avdName: String?,) {
    val manager: AvdManager? = getVirtualDeviceManager()
    val emulatorRecordingFile =
      if (manager != null && useEmulatorRecording && avdName != null) getTemporaryVideoPathForVirtualDevice(avdName, manager) else null
    recordingInProgress.add(serialNumber)

    coroutineScope.launch(exceptionHandler) {
      val showTouchEnabled = isShowTouchEnabled(serialNumber)
      val size = getDeviceScreenSize(serialNumber)
      val options: ScreenRecorderOptions = ScreenRecorderPersistentOptions.getInstance().toScreenRecorderOptions(size)
      if (options.showTouches != showTouchEnabled) {
        setShowTouch(serialNumber, options.showTouches)
      }
      try {
        val recodingProvider = when (emulatorRecordingFile) {
          null -> ShellCommandRecordingProvider(
            disposableParent,
            serialNumber,
            REMOTE_PATH.format(clock.millis()),
            options,
            adbLibSession)
          else -> EmulatorConsoleRecordingProvider(
            serialNumber,
            emulatorRecordingFile,
            options,
            adbLibSession)
        }
        ScreenRecorder(project, recodingProvider).recordScreen()
      }
      finally {
        if (options.showTouches != showTouchEnabled) {
          setShowTouch(serialNumber, showTouchEnabled)
        }
        withContext(uiThread) {
          recordingInProgress.remove(serialNumber)
        }
      }
    }
  }

  private fun getVirtualDeviceManager(): AvdManager? {
    return try {
      AvdManager.getInstance(AndroidSdks.getInstance().tryToChooseSdkHandler(), LogWrapper(logger))
    }
    catch (exception: AndroidLocationsException) {
      logger.warn(exception)
      null
    }
  }

  private suspend fun getDeviceScreenSize(serialNumber: String): Dimension? {
    try {
      val out = execute(serialNumber, "wm size")
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

  private suspend fun setShowTouch(serialNumber: String, isEnabled: Boolean) {
    val value = if (isEnabled) 1 else 0
    try {
      execute(serialNumber, "settings put system show_touches $value")
    }
    catch (e: Exception) {
      logger.warn("Failed to set show taps to $isEnabled", e)
    }
  }

  private suspend fun isShowTouchEnabled(serialNumber: String): Boolean {
    val out = execute(serialNumber, "settings get system show_touches")
    return out.trim() == "1"
  }

  private fun coroutineExceptionHandler() = CoroutineExceptionHandler { _, throwable ->
    logger.warn("Failed to record screen", throwable)
    coroutineScope.launch(uiThread) {
      Messages.showErrorDialog(
        project,
        LogcatBundle.message("screenrecord.error.exception", throwable.toString()),
        LogcatBundle.message("screenrecord.action.title"))
    }
  }

  companion object {
    val SERIAL_NUMBER_KEY = DataKey.create<String>("ScreenRecorderDeviceSerialNumber")
    val AVD_NAME_KEY = DataKey.create<String>("ScreenRecorderDeviceAvdName")
    val SDK_KEY = DataKey.create<Int>("ScreenRecorderDeviceSdk")
  }
}

private fun getTemporaryVideoPathForVirtualDevice(avdName: String, manager: AvdManager): Path? {
  val virtualDevice: AvdInfo = manager.getAvd(avdName, true) ?: return null
  return virtualDevice.dataFolderPath.resolve(EMU_TMP_FILENAME)
}

private fun String.isEmulator() = startsWith("emulator-")
