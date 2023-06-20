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

import com.android.tools.idea.compose.annotator.check.common.ExpectedValueType
import com.android.tools.idea.compose.annotator.check.common.ParameterRule
import com.android.tools.idea.compose.pickers.preview.property.DimUnit
import com.android.tools.idea.compose.preview.Preview.DeviceSpec
import com.android.tools.idea.compose.preview.util.device.convertToDeviceSpecDimension
import com.android.tools.idea.kotlin.enumValueOfOrNull
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger

/**
 * [ParameterRule] to check Dimension parameters in the DeviceSpec.
 *
 * Dimension parameters are formed by a float value with a unit suffix. I.e: `10.2dp`
 *
 * Dimension parameters should share the same unit. The common unit is defined by the unit of the
 * first valid parameter checked or [DeviceSpec.DEFAULT_UNIT] if there are no valid dimension
 * parameters.
 *
 * There are different fixes that can be applied to a Dimension value. See
 * [DimensionParameterCheckResult].
 */
internal class DimensionParameterRule(override val name: String, private val defaultNumber: Int) :
  ParameterRule() {
  private val log = Logger.getInstance(this.javaClass)

  override val defaultValue: String = defaultNumber.toString() + DeviceSpec.DEFAULT_UNIT

  override val expectedType: ExpectedValueType = ExpectedFloatWithUnit

  override fun checkValue(value: String, dataProvider: DataProvider): Boolean {
    val deviceSpecState = DeviceSpecCheckStateKey.getData(dataProvider)
    val currentUnit = enumValueOfOrNull<DimUnit>(value.takeLast(2))

    if (currentUnit == null) {
      // Bad/missing unit, check if the value is at least a float number
      if (isBadNumber(value)) {
        // No unit and bad number value, entire statement is wrong
        deviceSpecState?.setCheckResult(name, DimensionParameterCheckResult.BadStatement)
        return false
      } else {
        // Only the unit is missing
        deviceSpecState?.setCheckResult(name, DimensionParameterCheckResult.MissingUnit)
        return false
      }
    }

    if (isBadNumber(value.dropLast(2))) {
      // Unit exist, but has an incorrect Number value (not a Float/Integer or more than one
      // decimal)
      deviceSpecState?.setCheckResult(name, DimensionParameterCheckResult.BadNumber)
      return false
    } else {
      val expectedUnit = deviceSpecState?.commonUnit
      return if (expectedUnit != null && currentUnit != expectedUnit) {
        // Wrong unit, doesn't match the expected/common unit
        deviceSpecState.setCheckResult(name, DimensionParameterCheckResult.WrongUnit)
        false
      } else {
        // Everything is ok, update the common unit if it hasn't been set
        if (deviceSpecState != null && deviceSpecState.commonUnit == null) {
          deviceSpecState.commonUnit = currentUnit
        }
        deviceSpecState?.setCheckResult(name, DimensionParameterCheckResult.Ok)
        true
      }
    }
  }

  override fun attemptFix(value: String, dataProvider: DataProvider): String? {
    val deviceSpecState =
      DeviceSpecCheckStateKey.getData(dataProvider)
        ?: run {
          log.error("Expected a ${DeviceSpecCheckState::class.simpleName} object")
          return null
        }
    val parameterState =
      deviceSpecState.getCheckResult(name) as? DimensionParameterCheckResult
        ?: run {
          log.error("Expected ${DimensionParameterCheckResult::class.simpleName} for $name")
          return null
        }
    val commonUnit = deviceSpecState.commonUnit ?: DeviceSpec.DEFAULT_UNIT
    return when (parameterState) {
      DimensionParameterCheckResult.WrongUnit -> value.dropLast(2) + commonUnit.name
      DimensionParameterCheckResult.MissingUnit -> value + commonUnit.name
      DimensionParameterCheckResult.BadNumber ->
        fixNumberOrDefault(value.dropLast(2)).toString() + commonUnit.name
      DimensionParameterCheckResult.BadStatement -> defaultNumber.toString() + commonUnit.name
      DimensionParameterCheckResult.Ok -> {
        log.warn("Call to fix value, but nothing to fix")
        // Return the original value
        value
      }
    }
  }

  private fun fixNumberOrDefault(valueToFix: String): Number {
    var floatNumber: Float? = null
    var lengthToFix = valueToFix.length
    while (floatNumber == null && lengthToFix > 0) {
      floatNumber = valueToFix.subSequence(0, lengthToFix--).toString().toFloatOrNull()
    }
    if (floatNumber == null) {
      // Couldn't fix from input, use the default number
      return defaultNumber
    }

    // round to the expected DeviceSpec format
    return convertToDeviceSpecDimension(floatNumber)
  }

  private fun isBadNumber(numberString: String): Boolean {
    val decimalIndex = numberString.indexOfLast { it == '.' }
    // Is a bad number if the string can't be parsed to a float, or if there's more than one decimal
    return numberString.toFloatOrNull() == null ||
      (decimalIndex >= 0 && numberString.length - decimalIndex > 2)
  }
}
