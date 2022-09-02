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
 * Base interface that describes an expectation of value for a ParameterRule.
 *
 * These are meant to be use by an Inspection to provide readable feedback when we identify that the
 * value of a parameter is incorrect.
 */
internal sealed interface ExpectedValueType

/**
 * For open-ended values. [valueTypeName] should be a short name for a easily recognizable type of
 * value. Eg: Integer, String, Float, etc.
 */
internal class OpenEndedValueType(val valueTypeName: String) : ExpectedValueType

/**
 * For parameters that have a bound choice of values. The list of [acceptableValues] should be the
 * list of values that the parameter may take. Eg: For Boolean: "true", "false".
 */
internal class MultipleChoiceValueType(val acceptableValues: List<String>) : ExpectedValueType
