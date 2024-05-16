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
package com.android.tools.idea.vitals.datamodel

import com.android.tools.idea.insights.Device
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.IssueId
import com.android.tools.idea.insights.OperatingSystemInfo
import com.android.tools.idea.insights.Version
import com.android.tools.idea.insights.VisibilityType
import com.intellij.openapi.diagnostic.Logger

/**
 * `apiLevel`: Matches error issues that occurred in the requested Android versions (specified as
 * the numeric API level) only. Example: `apiLevel = 28 OR apiLevel = 29`.
 */
private const val API_LEVEL = "apiLevel"

/**
 * `versionCode`: Matches error issues that occurred in the requested app version codes only.
 * Example: `versionCode = 123 OR versionCode = 456`.
 */
private const val VERSION_CODE = "versionCode"

/**
 * `deviceBrand`: Matches error issues that occurred in the requested device brands. Example:
 * `deviceBrand = "Google".
 */
private const val DEVICE_BRAND = "deviceBrand"

/**
 * `deviceModel`: Matches error issues that occurred in the requested devices. Example: `deviceModel
 * = "walleye" OR deviceModel = "marlin"`.
 */
private const val DEVICE_MODEL = "deviceModel"

/**
 * `errorIssueType`: Matches error issues of the requested types only. Valid candidates: `CRASH`,
 * `ANR`. Example: `errorIssueType = CRASH OR errorIssueType = ANR`.
 */
private const val ERROR_ISSUE_TYPE = "errorIssueType"

/**
 * `appProcessState`: Matches error issues on the process state of an app, indicating whether an app
 * runs in the foreground (user-visible) or background. Valid candidates: `FOREGROUND`,
 * `BACKGROUND`. Example: `appProcessState = FOREGROUND`.
 */
private const val APP_PROCESS_STATE = "appProcessState"

/**
 * `isUserPerceived`: Matches error issues that are user-perceived. It is not accompanied by any
 * operators. Example: `isUserPerceived`.
 */
private const val IS_USER_PERCEIVED = "isUserPerceived"

/**
 * `errorIssueId`: Matches error reports belonging to the requested error issue ids only. Example:
 * `errorIssueId = 1234 OR errorIssueId = 4567`.
 */
private const val ERROR_ISSUE_ID = "errorIssueId"

private data class Filter(val qualifier: String, val value: String) {
  override fun toString(): String {
    return if (value.isEmpty()) qualifier else "$qualifier = $value"
  }
}

class FilterBuilder {
  private val rawFilters = mutableSetOf<Filter>()

  /**
   * Can filter by failure types.
   *
   * The reason we have multiple failure types filters (see [addFailureTypes]) is we have different
   * filter qualifiers on the server side for different API calls.
   */
  fun addReportTypes(failureTypes: Collection<FailureType>) {
    failureTypes.mapNotNull {
      when (it) {
        FailureType.ANR -> rawFilters.add(Filter(DimensionType.REPORT_TYPE.value, "ANR"))
        FailureType.FATAL,
        FailureType.NON_FATAL -> rawFilters.add(Filter(DimensionType.REPORT_TYPE.value, "CRASH"))
        else -> {
          LOG.warn("Unrecognized report type: $it.")
          null
        }
      }
    }
  }

  /**
   * Can filter by failure types.
   *
   * The reason we have multiple failure types filters (see [addReportTypes]) is we have different
   * filter qualifiers on the server side for different API calls.
   */
  fun addFailureTypes(failureTypes: Collection<FailureType>) {
    failureTypes.mapNotNull {
      when (it) {
        FailureType.ANR -> rawFilters.add(Filter(ERROR_ISSUE_TYPE, "ANR"))
        FailureType.FATAL,
        FailureType.NON_FATAL -> rawFilters.add(Filter(ERROR_ISSUE_TYPE, "CRASH"))
        else -> {
          LOG.warn("Unrecognized failure type: $it.")
          null
        }
      }
    }
  }

  /** Filter by visibility types. */
  fun addVisibilityType(visibilityType: VisibilityType) {
    when (visibilityType) {
      VisibilityType.USER_PERCEIVED -> rawFilters.add(Filter(IS_USER_PERCEIVED, ""))
      VisibilityType.ALL -> Unit
    }
  }

  /** Filter by device model name (e.g. samsung/hlte). */
  fun addDevices(devices: Collection<Device>) {
    devices
      .filterNot { it == Device.ALL }
      .onEach { rawFilters.add(Filter(DEVICE_MODEL, "${it.manufacturer}/${it.model}")) }
  }

  fun addOperatingSystems(operatingSystems: Collection<OperatingSystemInfo>) {
    operatingSystems
      .filterNot { it == OperatingSystemInfo.ALL }
      .onEach { rawFilters.add(Filter(API_LEVEL, it.displayVersion)) }
  }

  fun addVersions(versions: Collection<Version>) {
    versions
      .filterNot { it == Version.ALL }
      .onEach { rawFilters.add(Filter(VERSION_CODE, it.buildVersion)) }
  }

  /**
   * Can filter by issue id.
   *
   * The reason we have multiple issue id filters (see [addIssue]) is we have different filter
   * qualifiers on the server side for different API calls.
   */
  fun addErrorIssue(issueId: IssueId) {
    rawFilters.add(Filter(ERROR_ISSUE_ID, issueId.value))
  }

  /**
   * Can filter by issue id.
   *
   * The reason we have multiple issue id filters (see [addErrorIssue]) is we have different filter
   * qualifiers on the server side for different API calls.
   */
  fun addIssue(issueId: IssueId) {
    rawFilters.add(Filter(DimensionType.ISSUE_ID.value, issueId.value))
  }

  fun build(): String {
    return rawFilters
      .groupBy { it.qualifier }
      .map { grouped ->
        grouped.value
          .sortedBy { it.value }
          .joinToString(separator = " OR ", prefix = "(", postfix = ")") { it.toString() }
      }
      .sorted()
      .joinToString(separator = " AND ") { it }
  }

  companion object {
    private val LOG = Logger.getInstance(FilterBuilder::class.java)
  }
}
