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

@file:JvmName("ProguardR8PsiImplUtil")

package com.android.tools.idea.lang.proguardR8.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet
import com.intellij.psi.search.GlobalSearchScope


private class ProguardR8JavaClassReferenceProvider(val scope: GlobalSearchScope) : JavaClassReferenceProvider() {

  override fun getScope(project: Project): GlobalSearchScope = scope

  override fun getReferencesByString(str: String, position: PsiElement, offsetInPosition: Int): Array<PsiReference> {
    return if (StringUtil.isEmpty(str)) {
      PsiReference.EMPTY_ARRAY
    }
    else {
      object : JavaClassReferenceSet(str, position, offsetInPosition, true, this) {
        // Allows inner classes be separated by a dollar sign "$", e.g.java.lang.Thread$State
        // We can't just use ALLOW_DOLLAR_NAMES flag because to make JavaClassReferenceSet work in the way we want;
        // language of PsiElement that we parse should be instanceof XMLLanguage.
        override fun isAllowDollarInNames() = true

      }.allReferences as Array<PsiReference>
    }
  }
}

fun getReferences(className: ProguardR8QualifiedName): Array<PsiReference> {
  val provider = ProguardR8JavaClassReferenceProvider(className.resolveScope)

  return provider.getReferencesByElement(className)
}
