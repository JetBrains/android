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
package com.android.tools.idea.lang.agsl

import com.android.tools.idea.flags.StudioFlags
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import org.jetbrains.annotations.NonNls

/**
 * Android Graphics Shading language.
 */
class AgslLanguage : Language(ID) {
  override fun getDisplayName(): String {
    return "AGSL (Android Graphics Shading Language)"
  }

  companion object {
    fun getInstance(): Language = if (StudioFlags.AGSL_LANGUAGE_SUPPORT.get()) PRIVATE_AGSL_LANGUAGE else PlainTextLanguage.INSTANCE

    /**
     * This should *only* be accessed from within existing AGSL token and PSI implementation; all feature code should be using [getInstance]
     * instead to make sure they're working with the feature flag being off.
     *
     * Once we make the AGSL support unconditional and remove the [StudioFlags], we should inline this field as getInstance()
     */
    @JvmStatic
    val PRIVATE_AGSL_LANGUAGE: AgslLanguage = AgslLanguage()

    @NonNls
    const val ID = "AGSL"
  }
}