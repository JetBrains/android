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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.intellij.openapi.diagnostic.Logger
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Attachment information for the current session. */
class AttachStatistics(
  private val clientType: ClientType,
  private val multipleProjectsOpen: () -> Boolean,
  private val isAutoConnectEnabled: () -> Boolean,
  private val isEmbeddedLayoutInspector: () -> Boolean,
  private val clock: Clock = Clock.systemUTC(),
) {
  private var success = false
  private var error = false
  private var debugging = false
  private var pausedDuringAttach = false
  private var errorCode = AttachErrorCode.UNKNOWN_ERROR_CODE
  private var composeErrorCode = AttachErrorCode.UNKNOWN_ERROR_CODE

  private var attachStartTimeMs: Instant? = null
  private var attachEndTimeMs: Instant? = null

  companion object {
    private val logger = Logger.getInstance(AttachStatistics::class.java)
  }

  fun start() {
    attachStartTimeMs = clock.instant()

    success = false
    error = false
    errorCode = AttachErrorCode.UNKNOWN_ERROR_CODE
    composeErrorCode = AttachErrorCode.UNKNOWN_ERROR_CODE
    currentProgress = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
  }

  fun save(dataSupplier: () -> DynamicLayoutInspectorAttachToProcess.Builder) {
    if (attachEndTimeMs == null) {
      // If the attach is not successful and there is no error (the attach is cancelled), the end
      // time is not updated.
      attachEndTimeMs = clock.instant()
    }

    dataSupplier().let {
      it.clientType = clientType
      it.success = success && !error
      it.errorInfoBuilder.let { error ->
        error.attachErrorCode = errorCode
        error.attachErrorState = currentProgress
      }
      it.composeErrorCode = composeErrorCode
      it.multipleProjectsOpen = multipleProjectsOpen.invoke()
      it.debuggerAttached = debugging
      it.debuggerPausedDuringAttach = pausedDuringAttach
      it.autoConnectEnabled = isAutoConnectEnabled()
      it.isEmbeddedLayoutInspector = isEmbeddedLayoutInspector()
      val durationMs =
        if (attachStartTimeMs != null) {
          Duration.between(attachStartTimeMs, attachEndTimeMs).toMillis()
        } else {
          0
        }
      it.attachDurationMs = durationMs
    }
  }

  /**
   * The current progress from the launch monitor.
   *
   * TODO: Consider renaming the proto field.
   */
  var currentProgress = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE

  fun attachSuccess() {
    success = true
    attachEndTimeMs = clock.instant()
  }

  fun attachError(errorCode: AttachErrorCode) {
    assertErrorNotGeneric(errorCode)
    error = true
    this.errorCode = errorCode
    attachEndTimeMs = clock.instant()
  }

  fun composeAttachError(errorCode: AttachErrorCode) {
    assertErrorNotGeneric(errorCode)
    composeErrorCode = errorCode
    attachEndTimeMs = clock.instant()
  }

  fun debuggerInUse(isPaused: Boolean) {
    debugging = true
    pausedDuringAttach = pausedDuringAttach || isPaused
  }

  private fun assertErrorNotGeneric(errorCode: AttachErrorCode) {
    if (!StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_THROW_UNEXPECTED_ERROR.get()) {
      return
    }

    if (errorCode == AttachErrorCode.UNKNOWN_ERROR_CODE) {
      logger.error("Logging UNKNOWN_ERROR_CODE. This error should never be logged.")
    } else if (errorCode == AttachErrorCode.UNEXPECTED_ERROR) {
      logger.error("Logging UNEXPECTED_ERROR. This error should be classified.")
    }
  }
}
