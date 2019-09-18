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
package com.android.tools.idea.lang.databinding.reference

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTypesUtil

/**
 * Reference that refers to a [PsiClass]
 */
internal class PsiClassReference(element: PsiElement, resolveTo: PsiClass, override val isStatic: Boolean)
  : DbExprReference(element, resolveTo) {
  override val resolvedType: PsiModelClass
    get() = PsiModelClass(PsiTypesUtil.getClassType(resolve() as PsiClass), DataBindingMode.fromPsiElement(element))
}
