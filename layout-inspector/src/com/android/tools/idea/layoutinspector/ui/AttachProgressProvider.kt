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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo

/** Class used to map attach to process progress to an ui-friendly string */
class AttachProgressProvider(private val updateAttachProgress: (String) -> Unit) :
  InspectorModel.AttachStageListener {
  override fun update(state: DynamicLayoutInspectorErrorInfo.AttachErrorState) {
    val text =
      when (state) {
        DynamicLayoutInspectorErrorInfo.AttachErrorState.UNKNOWN_ATTACH_ERROR_STATE ->
          "Unknown state"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.NOT_STARTED -> "Starting"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING -> "Adb ping success"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.ATTACH_SUCCESS -> "Attach success"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.START_REQUEST_SENT -> "Start request sent"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.START_RECEIVED -> "Start request received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.STARTED -> "Started"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.ROOTS_EVENT_SENT -> "Roots sent"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.ROOTS_EVENT_RECEIVED -> "Roots received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.VIEW_INVALIDATION_CALLBACK ->
          "Capture started"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.SCREENSHOT_CAPTURED ->
          "Screenshot captured"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.VIEW_HIERARCHY_CAPTURED ->
          "Hierarchy captured"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.RESPONSE_SENT -> "Response sent"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LAYOUT_EVENT_RECEIVED ->
          "View information received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.COMPOSE_REQUEST_SENT ->
          "Compose information request"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.COMPOSE_RESPONSE_RECEIVED ->
          "Compose information received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_WINDOW_LIST_REQUESTED ->
          "Legacy window list requested"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_WINDOW_LIST_RECEIVED ->
          "Legacy window list received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_HIERARCHY_REQUESTED ->
          "Legacy hierarchy requested"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_HIERARCHY_RECEIVED ->
          "Legacy hierarchy received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_SCREENSHOT_REQUESTED ->
          "Legacy screenshot requested"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.LEGACY_SCREENSHOT_RECEIVED ->
          "Legacy screenshot received"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.PARSED_COMPONENT_TREE ->
          "Compose tree parsed"
        DynamicLayoutInspectorErrorInfo.AttachErrorState.MODEL_UPDATED -> "Update complete"
      }

    if (text.isNotEmpty()) {
      updateAttachProgress(text)
    }
  }
}
