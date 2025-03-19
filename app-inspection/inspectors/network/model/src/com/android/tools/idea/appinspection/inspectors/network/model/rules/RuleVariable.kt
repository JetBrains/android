/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

/**
 * Describes a rule variable
 *
 * Text like `${HOST}` will be replaced by value of a [RuleVariable] with the name `HOST`
 */
data class RuleVariable(var name: String = "", var value: String = "") {
  fun applyTo(string: String) = string.replace("\${${name}}", value)
}

fun List<RuleVariable>.applyTo(string: String?): String? {
  var result = string ?: return null
  forEach { result = it.applyTo(result) }
  return result
}
