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

import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.android.tools.idea.lang.databinding.psi.PsiDbRefExpr
import com.intellij.psi.PsiPackage

/**
 * Reference that refers to a [PsiPackage].
 *
 * The [textRange] of this reference should be the range of last id element.
 * Sample: PsiElement representing package "androidx.databinging"
 * <pre>
 * PsiElement text: androidx.databinging
 * PsiReferences:   [Ref1--]X[Ref2-----]
 * </pre>
 * where Ref1 would resolve to "androidx" and Ref2 to"androidx.databinging".
 */
internal class PsiPackageReference(element: PsiDbRefExpr, target: PsiPackage)
  : DbExprReference(element, target, element.lastChild.textRange.shiftLeft(element.textOffset)) {
  override val memberAccess = PsiModelClass.MemberAccess.ALL_MEMBERS
  override val resolvedType: PsiModelClass? = null
}
