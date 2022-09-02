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
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import com.intellij.openapi.diagnostic.thisLogger

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
  fun registerModification(name: String, value: PreviewPickerValue, device: Device?)

  /**
   * Potentially slow, since some modification data will have to be converted to Studio Event
   * objects (eg: the name of the modified field)
   */
  @Slow fun logUsageData()
}

/**
 * Implementation with no operations. For cases of pickers which tracking classes are not available
 * yet.
 */
internal object NoOpTracker : ComposePickerTracker {
  override fun pickerShown() {} // Do nothing

  override fun pickerClosed() {} // Do nothing

  override fun registerModification(
    name: String,
    value: PreviewPickerValue,
    device: Device?
  ) {} // Do nothing

  override fun logUsageData() {} // Do nothing
}

/**
 * Base class to log usage for Compose pickers.
 *
 * Registers field modifications for a session when using the picker (a session starts when the user
 * opens the picker and ends when it's closed).
 *
 * So a session may contain several modifications.
 *
 * The implementation tries to guarantee that the data is only logged once per session.
 */
internal abstract class BaseComposePickerTracker : ComposePickerTracker {
  private val log = thisLogger()
  private val modifications = mutableListOf<PickerModification>()

  // These are used as safety measure to avoid submitting useless/repeated log data
  private var allowSubmittingTrackingInfo = false
  private var isPickerVisible = false

  final override fun pickerShown() {
    isPickerVisible = true
  }

  final override fun pickerClosed() {
    if (isPickerVisible) {
      allowSubmittingTrackingInfo = true
      isPickerVisible = false
    }
  }

  final override fun registerModification(
    name: String,
    value: PreviewPickerValue,
    device: Device?
  ) {
    if (!isPickerVisible) {
      log.warn("Attempted to register a modification when the picker is not visible")
      return
    }
    modifications.add(PickerModification(name, value, device))
  }

  /** Submit all registered modifications. */
  @Slow
  final override fun logUsageData() {
    if (!allowSubmittingTrackingInfo) {
      log.warn("Attempted to submit usage data in an invalid state")
      return
    }
    val actions = convertModificationsToTrackerActions(modifications)
    doLogUsageData(actions)
    modifications.clear() // Safety measure, to avoid repeated data
    allowSubmittingTrackingInfo = false
  }

  /** Send tracking data. */
  protected abstract fun doLogUsageData(actions: List<EditorPickerAction>)

  /** Converts the basic data format: [PickerModification] to the data class used for tracking. */
  protected abstract fun convertModificationsToTrackerActions(
    modifications: List<PickerModification>
  ): List<EditorPickerAction>

  /** Holds basic data that will later be processed for tracking. */
  protected data class PickerModification(
    val propertyName: String,
    val assignedValue: PreviewPickerValue,
    val deviceBeforeModification: Device?
  )
}
