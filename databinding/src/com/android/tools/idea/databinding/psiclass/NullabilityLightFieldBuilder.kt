/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.psiclass

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiType
import com.intellij.psi.impl.light.LightFieldBuilder
import com.intellij.psi.impl.light.LightModifierList

/**
 * [LightModifierList] with the ability to additionally prepend the correct nullability annotation
 * to the field.
 */
private class ModifierListWithNullabilityAnnotation(
  private val wrapped: PsiModifierList,
  private val isNonNull: Boolean)
  : LightModifierList(wrapped.manager) {

  init {
    copyModifiers(wrapped)
  }

  override fun getAnnotations(): Array<PsiAnnotation> {
    // The exact nullability annotation we use doesn't matter too much. We just want the IDE code
    // completion popup to recognize it.
    val nullabilityManager = NullableNotNullManager.getInstance(project)
    val annotationText = if (isNonNull) nullabilityManager.defaultNotNull else nullabilityManager.defaultNullable
    val annotation = PsiElementFactory.getInstance(project).createAnnotationFromText("@$annotationText", wrapped.context)
    return arrayOf(annotation)
  }
}

/**
 * A [LightFieldBuilder] with easy nullability support.
 */
class NullabilityLightFieldBuilder(manager: PsiManager, name: String, type: PsiType, isNonNull: Boolean, vararg modifiers: String)
  : LightFieldBuilder(manager, name, type) {

  init {
    setModifiers(*modifiers)
    setModifierList(ModifierListWithNullabilityAnnotation(super.getModifierList(), isNonNull))
  }
}