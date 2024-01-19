/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.persistence

import com.android.ide.common.util.enumValueOfOrNull
import com.android.tools.idea.insights.AppInsightsState
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.DeviceType
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.Filters
import com.android.tools.idea.insights.MultiSelection
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.PlayTrack
import com.android.tools.idea.insights.SignalType
import com.android.tools.idea.insights.TimeIntervalFilter
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.VisibilityType
import com.android.tools.idea.insights.WithCount
import kotlin.IllegalArgumentException

/** Persistent filter settings of a single tab. */
data class InsightsFilterSettings(
  /** The selected connection. */
  var connection: ConnectionSetting? = null,
  /** The selected [TimeIntervalFilter.name] */
  var timeIntervalDays: String? = null,
  /** The selected [VisibilityType.name] */
  var visibilityType: String? = null,
  /** The selected [SignalType.name] */
  var signal: String? = null,
  /** List of selected [FailureType.name] */
  var failureTypes: List<String>? = null,
  /** List of selected versions */
  var versions: List<VersionSetting>? = null,
  /** List of selected devices. */
  var devices: List<DeviceSetting>? = null,
  /** List of selected OSes. */
  var operatingSystems: List<OperatingSystemSetting>? = null,
) {
  /**
   * Overwrite the [filters] with the stored settings field by field when they are non-null.
   *
   * Since the persisted settings only stores the previously selected filter values and doesn't care
   * about counts, restored filters are created with 0 as count.
   */
  fun overwriteFilters(filters: Filters): Filters {
    var result = filters
    operatingSystems?.let { setting ->
      val items = setting.map { WithCount(0, it.toOperatingSystemInfo()) }
      result = result.copy(operatingSystems = MultiSelection(items.toSet(), items))
    }
    versions?.let { setting ->
      val items = setting.map { WithCount(0, it.toVersion()) }
      result = result.copy(versions = MultiSelection(items.toSet(), items))
    }
    devices?.let { setting ->
      val items = setting.map { WithCount(0, it.toDevice()) }
      result = result.copy(devices = MultiSelection(items.toSet(), items))
    }

    val types = failureTypes?.mapNotNull { enumValueOfOrNull<FailureType>(it) }?.toSet()
    if (!types.isNullOrEmpty()) {
      result =
        result.copy(failureTypeToggles = filters.failureTypeToggles.selectMatching { it in types })
    }

    signal
      ?.let { enumValueOfOrNull<SignalType>(it) }
      ?.let { signal -> result = result.copy(signal = filters.signal.select(signal)) }
    visibilityType
      ?.let { enumValueOfOrNull<VisibilityType>(it) }
      ?.let { visibility ->
        result = result.copy(visibilityType = filters.visibilityType.select(visibility))
      }
    timeIntervalDays
      ?.let { enumValueOfOrNull<TimeIntervalFilter>(it) }
      ?.let { interval ->
        result = result.copy(timeInterval = filters.timeInterval.select(interval))
      }
    return result
  }
}

/** Uniquely identifies a Vitals or Crashlytics connection. */
data class ConnectionSetting(
  var appId: String = "",
  var projectId: String? = null,
  var projectNumber: String? = null,
  var mobileSdkAppId: String? = null,
) {
  fun equalsConnection(connection: Connection) =
    appId == connection.appId &&
      projectId == connection.projectId &&
      projectNumber == connection.projectNumber &&
      mobileSdkAppId == connection.mobileSdkAppId
}

data class DeviceSetting(
  var manufacturer: String = "",
  var model: String = "",
  var displayName: String = "",
  var deviceType: String = "",
) {
  fun toDevice() = Device(manufacturer, model, displayName, DeviceType(deviceType))
}

data class OperatingSystemSetting(var displayVersion: String = "", var displayName: String = "") {
  fun toOperatingSystemInfo() = OperatingSystemInfo(displayVersion, displayName)
}

data class VersionSetting(
  var buildVersion: String = "",
  var displayVersion: String = "",
  var displayName: String = "",
  var tracks: List<String> = mutableListOf(),
) {
  fun toVersion() =
    Version(
      buildVersion,
      displayVersion,
      displayName,
      tracks
        .mapNotNull {
          try {
            PlayTrack.valueOf(it)
          } catch (e: IllegalArgumentException) {
            null
          }
        }
        .toSet(),
    )
}

internal fun Connection.toSetting() =
  ConnectionSetting(appId, projectId, projectNumber, mobileSdkAppId)

internal fun Device.toSetting() = DeviceSetting(manufacturer, model, displayName, deviceType.name)

internal fun OperatingSystemInfo.toSetting() = OperatingSystemSetting(displayVersion, displayName)

internal fun Version.toSetting() =
  VersionSetting(buildVersion, displayVersion, displayName, tracks.map { it.name })

internal fun AppInsightsState.toFilterSettings() =
  InsightsFilterSettings(
    connections.selected?.toSetting(),
    filters.timeInterval.selected?.name,
    filters.visibilityType.selected?.name,
    filters.signal.selected?.name,
    filters.failureTypeToggles.mapNotAllSelected { it.name },
    filters.versions.mapNotAllSelected { it.value.toSetting() },
    filters.devices.mapNotAllSelected { it.value.toSetting() },
    filters.operatingSystems.mapNotAllSelected { it.value.toSetting() },
  )

private fun <T, R> MultiSelection<T>.mapNotAllSelected(transform: (T) -> R) =
  takeUnless { it.allSelected() }?.selected?.map(transform)
