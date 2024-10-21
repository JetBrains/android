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
package com.android.tools.idea.avd

import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.AvdNames

internal class DeviceNameValidator(
  private val currentDisplayNames: Set<String>,
  private val currentName: String? = null,
) {
  private val endingNumberRegex = Regex("""\((\d+)\)$""")

  fun uniquify(name: String): String {
    val matchResult = endingNumberRegex.find(name)
    var suffix = matchResult?.groupValues?.get(1)?.toInt() ?: 1
    val baseName =
      if (matchResult == null) name + " " else name.substring(0, matchResult.range.start)
    var candidate = name
    while (candidate in currentDisplayNames) {
      candidate = "$baseName(${++suffix})"
    }
    return candidate
  }

  fun validate(name: String): String? =
    when {
      name == currentName -> null
      name.isBlank() -> "The name cannot be blank."
      !AvdNames.isValid(name) ->
        "The AVD name can contain only the characters " + AvdNames.humanReadableAllowedCharacters()
      name.trim() in currentDisplayNames -> "An AVD with this name already exists."
      else -> null
    }

  companion object {
    fun createForAvdManager(avdManager: AvdManager, currentName: String? = null) =
      DeviceNameValidator(avdManager.allAvds.map { it.displayName }.toSet(), currentName)
  }
}
