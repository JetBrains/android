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

internal class DeviceNameValidator(avdManager: AvdManager, val currentName: String? = null) :
  (String) -> String? {
  private val currentDisplayNames = avdManager.allAvds.map { it.displayName }.toSet()

  override fun invoke(name: String): String? =
    when {
      name == currentName -> null
      name in currentDisplayNames -> "An AVD with this name already exists."
      !AvdNames.isValid(name) ->
        "The AVD name can contain only the characters " + AvdNames.humanReadableAllowedCharacters()
      else -> null
    }
}
