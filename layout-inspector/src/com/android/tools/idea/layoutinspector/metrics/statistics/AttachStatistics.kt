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

import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState

/**
 * Attachment information for the current session.
 */
class AttachStatistics(
  private val clientType: ClientType,
  private val multipleProjectsOpen: () -> Boolean
) {
  private var success = false
  private var error = false
  private var errorState = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
  private var errorCode = AttachErrorCode.UNKNOWN_ERROR_CODE
  private var composeErrorCode = AttachErrorCode.UNKNOWN_ERROR_CODE

  fun start() {
    success = false
    error = false
    errorState = AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
    errorCode = AttachErrorCode.UNKNOWN_ERROR_CODE
    composeErrorCode = AttachErrorCode.UNKNOWN_ERROR_CODE
  }

  fun save(dataSupplier: () -> DynamicLayoutInspectorAttachToProcess.Builder) {
    dataSupplier().let {
      it.clientType = clientType
      it.success = success && !error
      it.errorInfoBuilder.let { error ->
        error.attachErrorCode = errorCode
        error.attachErrorState = errorState
      }
      it.composeErrorCode = composeErrorCode
      it.multipleProjectsOpen = multipleProjectsOpen.invoke()
    }
  }

  fun attachSuccess() {
    success = true
  }

  fun attachError(errorState: AttachErrorState?, errorCode: AttachErrorCode) {
    error = true
    this.errorState = errorState ?: AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE
    this.errorCode = errorCode
  }

  fun composeAttachError(errorCode: AttachErrorCode) {
    composeErrorCode = errorCode
  }
}
