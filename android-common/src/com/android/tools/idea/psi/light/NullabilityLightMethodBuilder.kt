/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.psi.light

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightMethodBuilder

/**
 * A [LightMethodBuilder] that supports adding parameters with nullability support.
 *
 * This ensures that annotations show up correctly in the parameter info popup, and possibly other
 * locations.
 */
open class NullabilityLightMethodBuilder(
  manager: PsiManager,
  language: Language,
  name: String
) : LightMethodBuilder(manager, language, name) {

  constructor(manager: PsiManager, name: String) : this(manager, JavaLanguage.INSTANCE, name)

  fun addNullabilityParameter(name: String, type: PsiType, isNonNull: Boolean): NullabilityLightMethodBuilder {
    return addParameter(NullabilityLightParameterBuilder(name, type, this, language, isNonNull)) as NullabilityLightMethodBuilder
  }
}