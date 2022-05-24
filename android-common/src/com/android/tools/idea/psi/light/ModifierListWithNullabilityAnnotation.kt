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

import com.android.tools.idea.psi.createNullabilityAnnotation
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiModifierList
import com.intellij.psi.impl.light.LightModifierList

/**
 * [LightModifierList] with the ability to additionally prepend the correct nullability annotation
 * to the list of modifiers.
 */
class ModifierListWithNullabilityAnnotation(
  private val wrapped: PsiModifierList,
  private val isNonNull: Boolean)
  : LightModifierList(wrapped.manager) {

  init {
    copyModifiers(wrapped)
  }

  override fun getAnnotations(): Array<PsiAnnotation> {
    return arrayOf(project.createNullabilityAnnotation(isNonNull, wrapped.context))
  }
}
