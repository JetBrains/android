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

import com.android.tools.idea.dagger.getQualifierInfo
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.android.tools.idea.kotlin.psiType
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.util.CachedValue
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

internal sealed class ConsumerDaggerElementBase : DaggerElement() {

  /** Returns a string indicating the group shown in related items for this element. */
  abstract val relatedElementGrouping: String

  /**
   * Returns a string indicated which resource to use when describing related items for this
   * element.
   */
  abstract val relationDescriptionKey: String

  /** Type being consumer, as specified in code. */
  protected abstract val rawType: PsiType

  /** Gets info for any @Qualifier annotations on this element. */
  internal val qualifierInfo by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) { psiElement.getQualifierInfo() }

  /** Type being consumed, without any wrapper like `dagger.Lazy<>`. */
  val consumedType: PsiType
    get() = rawType.withoutDaggerWrapper().unboxed

  override fun doGetRelatedDaggerElements(): List<DaggerRelatedElement> =
    getRelatedDaggerElementsFromIndex<ProviderDaggerElementBase>(consumedType.getIndexKeys()).map {
      DaggerRelatedElement(it, DaggerBundle.message("providers"), relationDescriptionKey)
    }

  override fun filterResolveCandidate(resolveCandidate: DaggerElement) =
    resolveCandidate is ProviderDaggerElementBase && resolveCandidate.canProvideFor(this)
}

internal data class ConsumerDaggerElement(
  override val psiElement: PsiElement,
  override val rawType: PsiType,
) : ConsumerDaggerElementBase() {

  internal constructor(psiElement: KtParameter) : this(psiElement, psiElement.psiType!!.unboxed)

  internal constructor(psiElement: KtProperty) : this(psiElement, psiElement.psiType!!.unboxed)

  internal constructor(psiElement: PsiField) : this(psiElement, psiElement.type.unboxed)

  internal constructor(psiElement: PsiParameter) : this(psiElement, psiElement.type.unboxed)

  override val metricsElementType = DaggerEditorEvent.ElementType.CONSUMER

  override val relatedElementsKey = RELATED_ELEMENTS_KEY

  override val relatedElementGrouping: String = DaggerBundle.message("consumers")
  override val relationDescriptionKey: String = "navigate.to.provider"

  companion object {
    private val RELATED_ELEMENTS_KEY =
      Key<CachedValue<List<DaggerRelatedElement>>>("ConsumerDaggerElement_RelatedElements")
  }
}
