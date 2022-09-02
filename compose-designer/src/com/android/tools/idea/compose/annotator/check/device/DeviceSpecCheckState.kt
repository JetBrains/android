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
package com.android.tools.idea.compose.annotator.check.device

import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.intellij.openapi.actionSystem.DataKey

internal val DeviceSpecCheckStateKey =
  DataKey.create<DeviceSpecCheckState>("preview.check.device.spec.fix.state")

/**
 * Class to keep track of the check result of DeviceSpec parameters.
 *
 * This should be used to simplify the fix step of a
 * [com.android.tools.idea.compose.annotator.check.common.ParameterRule].
 *
 * @see DimensionParameterRule
 */
internal class DeviceSpecCheckState {
  private val parametersFixState = mutableMapOf<String, ParameterCheckResult>()

  /**
   * The unit that all dimension parameters are expected to have. Should be set from the first
   * parameter that has no issues.
   *
   * @see DimensionParameterRule
   */
  var commonUnit: DimUnit? = null

  /** Returns the last [ParameterCheckResult] associated to [parameterName], if any. */
  fun getCheckResult(parameterName: String): ParameterCheckResult? {
    return parametersFixState[parameterName]
  }

  /** Save the result of a value check for [parameterName]. */
  fun setCheckResult(parameterName: String, checkResult: ParameterCheckResult) {
    parametersFixState[parameterName] = checkResult
  }
}

/**
 * Arbitrary Interface that may be implemented to store any data.
 *
 * @see DimensionParameterCheckResult
 */
internal interface ParameterCheckResult

/** Possible resulting states when checking the value of a Dimension parameter for DeviceSpec. */
internal enum class DimensionParameterCheckResult : ParameterCheckResult {
  /** Nothing wrong found with the parameter. */
  Ok,

  /** The entire value/statement of the parameter is incorrect. */
  BadStatement,

  /** Only the number part of the value has an issue. */
  BadNumber,

  /** The number part of the value is correct, but there's no suffix for the unit. */
  MissingUnit,

  /**
   * The number part is correct, and there is a suffix for the unit, however, it does not match the
   * unit of other parameters.
   */
  WrongUnit
}
