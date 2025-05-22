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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightParameter

/**
 * A [LightParameter] with nullability support.
 */
class NullabilityLightParameterBuilder(name: String, type: PsiType, declartionScope: PsiElement, language: Language, isNonNull: Boolean)
  : LightParameter(name, type, declartionScope, language) {
  init {
    setModifierList(ModifierListWithNullabilityAnnotation(super.getModifierList(), isNonNull))
  }
}
