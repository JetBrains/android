/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.serverflags

import com.android.utils.associateNotNull

/** Parses the system property `studio.server.flags.enabled.override` */
interface OverridePropertyParser {
  /** Returns a map of the overridden flag names and their value index. */
  fun parseProperty(property: String): Map<String, Int>
}

class OverridePropertyParserImpl(private val supportMultiValueFlags: Boolean) :
  OverridePropertyParser {
  override fun parseProperty(property: String) =
    property.split(',').associateNotNull { param ->
      if (supportMultiValueFlags) {
        parseMultiValueOverrideParam(param) ?: parseSingleValueOverrideParam(param)
      } else {
        parseSingleValueOverrideParam(param)
      }
    }

  private fun parseMultiValueOverrideParam(param: String): Pair<String, Int>? {
    if (param.isEmpty()) return null

    val name = param.substringBeforeLast('/', "")
    val indexString = param.substringAfterLast('/', "")
    if (name.isEmpty() || indexString.isEmpty()) {
      return null
    }
    val index = indexString.toIntOrNull() ?: return null
    return name to index
  }

  private fun parseSingleValueOverrideParam(param: String): Pair<String, Int>? {
    if (param.isEmpty()) return null
    return param to 0
  }
}
