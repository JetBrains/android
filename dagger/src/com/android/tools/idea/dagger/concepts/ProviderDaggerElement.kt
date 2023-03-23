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
package com.android.tools.idea.dagger.concepts

import com.android.tools.idea.dagger.unboxed
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtFunction

internal data class ProviderDaggerElement(
  override val psiElement: PsiElement,
  private val providedPsiType: PsiType
) : DaggerElement() {

  internal constructor(psiElement: KtFunction) : this(psiElement, psiElement.getReturnedPsiType())
  internal constructor(psiElement: PsiMethod) : this(psiElement, psiElement.getReturnedPsiType())

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    // Since Dagger allows types to be wrapped with Lazy<> and Provider<> and since our index stores
    // generics by the outermost type's simple name only, we have to search for "Lazy" and
    // "Provider" for all types.
    val project = psiElement.project
    val scope = project.projectScope()
    val indexKeys = providedPsiType.getIndexKeys() + extraIndexKeysForProvider(project, scope)

    return getRelatedDaggerElementsFromIndex<ConsumerDaggerElementBase>(indexKeys).map {
      DaggerRelatedElement(it, it.relatedElementGrouping)
    }
  }

  override fun filterResolveCandidate(resolveCandidate: DaggerElement): Boolean =
    resolveCandidate is ConsumerDaggerElementBase && canProvideType(resolveCandidate.consumedType)

  fun canProvideType(psiType: PsiType): Boolean {
    return psiType.unboxed == providedPsiType ||
      psiType.withoutWrappingDaggerType() == providedPsiType
  }
}
