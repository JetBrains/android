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
package com.android.tools.idea.studiobot

import com.intellij.openapi.extensions.ExtensionPointName

/** Flags that are relevant to features using Studio Bot APIs */
interface StudioBotExternalFlags {
  /**
   * For the 'Explain Build Error' action in Android Studio; controls whether context should be
   * fetched and attached for Java/Kotlin compiler errors.
   */
  fun isCompilerErrorContextEnabled(): Boolean

  companion object {
    val EP_NAME =
      ExtensionPointName.create<StudioBotExternalFlags>(
        "com.android.tools.idea.ml.studioBotExternalFlags"
      )

    fun isCompilerErrorContextEnabled(): Boolean =
      EP_NAME.extensionList.firstOrNull()?.isCompilerErrorContextEnabled() ?: false
  }
}
