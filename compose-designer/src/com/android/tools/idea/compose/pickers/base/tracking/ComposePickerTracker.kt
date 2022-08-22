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
package com.android.tools.idea.compose.pickers.base.tracking

import com.android.annotations.concurrency.Slow
import com.android.sdklib.devices.Device
import com.google.wireless.android.sdk.stats.EditorPickerEvent

/**
 * Interface used to log usage data for Compose Pickers, typically invoked from the Editor Gutter.
 */
internal interface ComposePickerTracker {
  fun pickerShown()
  fun pickerClosed()

  /**
   * Register a modification to the [name] parameter of the Preview annotation.
   *
   * [value] is one of the tracking relevant options that best represents the value assigned to the
   * parameter.
   *
   * [device] should be the currently active [Device] in the preview when the change was made (right
   * before the change is applied).
   */
  fun registerModification(
    name: String,
    value: EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue,
    device: Device?
  )

  /**
   * Potentially slow, since some modification data will have to be converted to Studio Event
   * objects (eg: the name of the modified field)
   */
  @Slow fun logUsageData()
}
