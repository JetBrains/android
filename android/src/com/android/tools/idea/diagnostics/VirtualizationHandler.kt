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
package com.android.tools.idea.diagnostics

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.VirtualizationEvent

interface VirtualizationHandler {
  fun onVirtualizationDetected(vm: VirtualizationEvent.VmType, container: VirtualizationEvent.ContainerType)
}

class LoggingVirtualizationHandler : VirtualizationHandler {
  override fun onVirtualizationDetected(vm: VirtualizationEvent.VmType, container: VirtualizationEvent.ContainerType) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.VIRTUALIZATION_EVENT)
        .setVirtualizationEvent(
          VirtualizationEvent.newBuilder()
            .setVm(vm)
            .setContainer(container)
            .build()
        )
    )
  }
}