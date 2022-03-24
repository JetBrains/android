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

/**
 * A [ParameterRule] defines the logic to verify a parameter value correctness.
 */
internal interface ParameterRule {
  /**
   * Name of the parameter
   */
  val name: String

  /**
   * Describes the expected type of value. Eg: Value should be an Integer
   *
   * @see ExpectedValueType
   */
  val expectedType: ExpectedValueType

  /**
   * The default value, used to provide a correction option.
   */
  val defaultValue: String

  /**
   * A lambda that evaluates the [String] value associated with this parameter. Returns false if the [String] does not represent a valid
   * value for this parameter.
   */
  val valueCheck: (String) -> Boolean

  companion object {
    fun create(
      name: String,
      expectedType: ExpectedValueType,
      defaultValue: String,
      valueCheck: (String) -> Boolean
    ): ParameterRule = object : ParameterRule {
      override val name: String = name
      override val defaultValue: String = defaultValue
      override val expectedType: ExpectedValueType = expectedType
      override val valueCheck: (String) -> Boolean = valueCheck
    }
  }
}