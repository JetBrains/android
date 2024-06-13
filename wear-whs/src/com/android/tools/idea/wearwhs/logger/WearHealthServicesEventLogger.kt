/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.logger

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.WearHealthServicesEvent

class WearHealthServicesEventLogger(
  private val logFunction: (AndroidStudioEvent.Builder) -> Unit = { event -> UsageTracker.log(event) }) {

  fun logApplyChangesSuccess() {
    logFunction(newEventOfKind(WearHealthServicesEvent.EventKind.APPLY_CHANGES_SUCCESS))
  }

  fun logApplyChangesFailure() {
    logFunction(newEventOfKind(WearHealthServicesEvent.EventKind.APPLY_CHANGES_FAILURE))
  }

  fun logBindEmulator() {
    logFunction(newEventOfKind(WearHealthServicesEvent.EventKind.EMULATOR_BOUND))
  }

  fun logConnectionError() {
    logFunction(newEventOfKind(WearHealthServicesEvent.EventKind.CONNECTION_ERROR))
  }

  private fun newEventOfKind(kind: WearHealthServicesEvent.EventKind): AndroidStudioEvent.Builder =
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.WEAR_HEALTH_SERVICES_TOOL_WINDOW_EVENT)
      .setWearHealthServicesEvent(
        WearHealthServicesEvent.newBuilder().setKind(kind)
      )
}