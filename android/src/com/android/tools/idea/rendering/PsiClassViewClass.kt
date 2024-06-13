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
package com.android.tools.idea.rendering

import com.android.tools.module.ViewClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiModifier

/** [ViewClass] implementation based on intellij [PsiClass]. */
class PsiClassViewClass(private val psiClass: PsiClass) : ViewClass {
  override val superClass: ViewClass? = psiClass.superClass?.let { PsiClassViewClass(it) }
  override val qualifiedName: String? = psiClass.qualifiedName
  override val isAbstract: Boolean
    get() = psiClass.isInterface || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
}