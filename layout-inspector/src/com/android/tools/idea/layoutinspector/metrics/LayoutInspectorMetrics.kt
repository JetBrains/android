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
package com.android.tools.idea.layoutinspector.metrics

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorTransportError

object LayoutInspectorMetrics {
  fun logEvent(eventType: DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType) {
    val builder = AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT
      dynamicLayoutInspectorEventBuilder.apply {
        type = eventType
      }
    }

    UsageTracker.log(builder)
  }

  fun logTransportError(transportErrorType: DynamicLayoutInspectorTransportError.Type) {
    val transportErrorInfo = DynamicLayoutInspectorTransportError.newBuilder().setType(transportErrorType).build()

    val androidStudioEvent = AndroidStudioEvent.newBuilder().apply {
      kind = AndroidStudioEvent.EventKind.DYNAMIC_LAYOUT_INSPECTOR_EVENT
      dynamicLayoutInspectorEvent = DynamicLayoutInspectorEvent.newBuilder().apply {
        type = DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.TRANSPORT_ERROR
        transportError = transportErrorInfo
      }.build()
    }

    UsageTracker.log(androidStudioEvent)
  }
}