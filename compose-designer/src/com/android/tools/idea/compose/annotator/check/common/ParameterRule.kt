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
package com.android.tools.idea.compose.annotator.check.common

import com.intellij.openapi.actionSystem.DataProvider

/** A [ParameterRule] defines the logic to verify a parameter value correctness. */
internal abstract class ParameterRule {
  /** Name of the parameter */
  abstract val name: String

  /**
   * Describes the expected type of value. Eg: Value should be an Integer
   *
   * @see ExpectedValueType
   */
  abstract val expectedType: ExpectedValueType

  /** The default value, used to provide a correction option when [attemptFix] returns null. */
  abstract val defaultValue: String

  /**
   * Returns whether the [value] associated to the parameter is valid.
   *
   * Use [dataProvider] to obtain additional information.
   */
  abstract fun checkValue(value: String, dataProvider: DataProvider): Boolean

  /**
   * Returns a valid String based off [value]. [value] is the original parameter input, and it's
   * guaranteed to have failed [checkValue].
   *
   * Returns null if it's not possible to fix the original [value].
   *
   * Use [dataProvider] to obtain additional information.
   */
  abstract fun attemptFix(value: String, dataProvider: DataProvider): String?

  /**
   * Returns a valid String for the given [value], either by the result of [attemptFix] or using
   * [defaultValue].
   */
  fun getFixedOrDefaultValue(value: String, dataProvider: DataProvider): String =
    attemptFix(value, dataProvider) ?: defaultValue

  companion object {

    /**
     * Basic implementation of [ParameterRule], does not attempt to fix any given value for [name].
     */
    fun simpleParameterRule(
      name: String,
      expectedType: ExpectedValueType,
      defaultValue: String,
      valueCheck: (String) -> Boolean
    ): ParameterRule =
      object : ParameterRule() {
        override val name: String = name
        override val defaultValue: String = defaultValue
        override val expectedType: ExpectedValueType = expectedType
        override fun checkValue(value: String, dataProvider: DataProvider): Boolean =
          valueCheck(value)
        override fun attemptFix(value: String, dataProvider: DataProvider): String? = null
      }
  }
}
