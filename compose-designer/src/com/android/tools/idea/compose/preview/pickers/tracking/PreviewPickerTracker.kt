/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.tracking

import com.android.annotations.concurrency.Slow
import com.android.sdklib.devices.Device

/**
 * Interface used to log usage data of the @Preview picker.
 */
internal interface PreviewPickerTracker {
  fun pickerShown()
  fun pickerClosed()

  /**
   * Register a modification to the [name] parameter of the Preview annotation.
   *
   * [value] is one of the tracking relevant options that best represents the value assigned to the parameter.
   *
   * [device] should be the currently active [Device] in the preview when the change was made (right before the change is applied).
   */
  fun registerModification(name: String, value: PickerTrackableValue, device: Device?)

  /**
   * Potentially slow, since some modification data will have to be converted to Studio Event objects (eg: the name of the modified field)
   */
  @Slow
  fun logUsageData()
}

/**
 * Placeholder implementation until all relevant changes are ready
 */
internal object NoOpTracker : PreviewPickerTracker { // TODO(b/205184728): Update as necessary changes get ready (refactoring, studio event classes, etc.)
  override fun pickerShown() {} // Do nothing

  override fun pickerClosed() {} // Do nothing

  override fun registerModification(name: String, value: PickerTrackableValue, device: Device?) {} // Do nothing

  override fun logUsageData() {} // Do nothing
}


// TODO(b/205184728): Replace once the studio_stats object has been updated with our tracking classes, those will have their own detailed
//  javadoc
internal enum class PickerTrackableValue {
  /**  Based on proto enums requirement, for unexpected/erroneous input */
  UNKNOWN,
  DELETED,
  UNSUPPORTED_OR_OPEN_ENDED,
  /** For any non-standard/canonical device selected, eg: Any device manager device */
  DEVICE_NOT_REF,
  DEVICE_REF_PHONE,
  DEVICE_REF_FOLDABLE,
  DEVICE_REF_TABLET,
  DEVICE_REF_DESKTOP,
  UNIT_PIXELS,
  UNIT_DP,
  ORIENTATION_PORTRAIT,
  ORIENTATION_LANDSCAPE,
  DENSITY_LOW,
  DENSITY_MEDIUM,
  DENSITY_HIGH,
  DENSITY_X_HIGH,
  DENSITY_XX_HIGH,
  DENSITY_XXX_HIGH,
  UI_MODE_NOT_NIGHT,
  UI_MODE_NIGHT,
}