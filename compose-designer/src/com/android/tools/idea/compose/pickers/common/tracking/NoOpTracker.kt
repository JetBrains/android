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
package com.android.tools.idea.compose.pickers.common.tracking

import com.android.sdklib.devices.Device
import com.android.tools.idea.compose.pickers.base.tracking.ComposePickerTracker
import com.google.wireless.android.sdk.stats.EditorPickerEvent

/**
 * Implementation with no operations. For cases of pickers which tracking classes are not available
 * yet.
 */
internal object NoOpTracker : ComposePickerTracker {
  override fun pickerShown() {} // Do nothing

  override fun pickerClosed() {} // Do nothing

  override fun registerModification(
    name: String,
    value: EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue,
    device: Device?
  ) {} // Do nothing

  override fun logUsageData() {} // Do nothing
}
