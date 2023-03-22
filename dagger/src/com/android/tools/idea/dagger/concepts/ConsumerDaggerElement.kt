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

import com.android.tools.idea.dagger.localization.DaggerBundle
import com.android.tools.idea.dagger.unboxed
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType

internal abstract class ConsumerDaggerElementBase : DaggerElement() {

  /** Returns a string indicating the group shown in related items for this element. */
  abstract val relatedElementGrouping: String

  /** Type being consumer, as specified in code. */
  protected abstract val rawType: PsiType

  /** Type being consumed, without any wrapper like `dagger.Lazy<>`. */
  val consumedType: PsiType
    get() = rawType.removeWrappingDaggerType().unboxed

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> =
    getRelatedDaggerElementsFromIndex<ProviderDaggerElement>(consumedType.getIndexKeys()).map {
      DaggerRelatedElement(it, DaggerBundle.message("providers"))
    }

  override fun filterResolveCandidate(resolveCandidate: DaggerElement) =
    when (resolveCandidate) {
      is ProviderDaggerElement -> consumedType in resolveCandidate.providedPsiTypes
      else -> false
    }

  companion object {
    internal val wrappingDaggerTypes = setOf(DaggerAnnotations.LAZY, DaggerAnnotations.PROVIDER)

    /**
     * Dagger allows consumers to wrap a requested type with Lazy<>, Provider<>, or
     * Provider<Lazy<>>. This method removes any of those wrappers, returning the inner type.
     */
    internal fun PsiType.removeWrappingDaggerType(): PsiType {
      if (this is PsiClassReferenceType && rawType().canonicalText in wrappingDaggerTypes) {
        return parameters[0].removeWrappingDaggerType()
      }

      return this
    }
  }
}

internal data class ConsumerDaggerElement(
  override val psiElement: PsiElement,
  override val rawType: PsiType = psiElement.getPsiType().unboxed
) : ConsumerDaggerElementBase() {

  override val relatedElementGrouping: String = DaggerBundle.message("consumers")
}
