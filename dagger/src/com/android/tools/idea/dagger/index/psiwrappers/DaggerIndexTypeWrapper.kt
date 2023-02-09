/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.dagger.index.psiwrappers

import com.intellij.psi.PsiTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference

/** A [DaggerIndexPsiWrapper] representing a type. */
interface DaggerIndexTypeWrapper : DaggerIndexPsiWrapper {
  /** Simple name of a type, without any package name. Eg: "Foo" */
  fun getSimpleName(): String
}

internal class KtTypeReferenceWrapper(
  private val ktTypeReference: KtTypeReference,
  private val importHelper: KotlinImportHelper
) : DaggerIndexTypeWrapper {
  override fun getSimpleName(): String {
    val typeReferenceText = ktTypeReference.text
    val simpleNameInCode = typeReferenceText.substringAfterLast(".")

    // If the type has any "." characters in it, then the only part that can be aliased is at the
    // beginning. Therefore, the alias isn't
    // part of the simple name, and we can just return here.
    if (typeReferenceText != simpleNameInCode) return simpleNameInCode

    // Otherwise, we know the type reference is just a simple name.
    // Look for any imports that might have an alias for this type.
    return importHelper.aliasMap[simpleNameInCode] ?: simpleNameInCode
  }
}

internal class PsiTypeElementWrapper(private val psiTypeElement: PsiTypeElement) :
  DaggerIndexTypeWrapper {
  override fun getSimpleName(): String = psiTypeElement.text.substringAfterLast(".")
}
