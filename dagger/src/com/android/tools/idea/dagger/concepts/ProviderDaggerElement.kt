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
import com.android.tools.idea.kotlin.psiType
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

internal sealed class ProviderDaggerElementBase : DaggerElement() {

  protected abstract fun getIndexKeys(): List<String>

  /** Gets info for any @Qualifier annotations on this element. */
  protected val qualifierInfo by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) { psiElement.getQualifierInfo() }

  abstract fun canProvideFor(consumer: ConsumerDaggerElementBase): Boolean

  override fun doGetRelatedDaggerElements(): List<DaggerRelatedElement> =
    getRelatedDaggerElementsFromIndex<ConsumerDaggerElementBase>(getIndexKeys()).map {
      DaggerRelatedElement(
        it,
        it.relatedElementGrouping,
        it.getRelationDescriptionKey(),
        it.psiElement.getCustomRelatedElementDisplayName(),
      )
    }

  /**
   * Gets a custom name for use when displaying a related consumer in navigation text. This
   * generally returns the name of a higher-level element than the consumer itself.
   */
  private fun PsiElement.getCustomRelatedElementDisplayName() =
    when (this) {
      is PsiField,
      is PsiMethod -> parentOfType<PsiClass>()?.name
      is PsiParameter -> parentOfType<PsiMethod>()?.name
      is KtProperty,
      is KtFunction -> parentOfType<KtClass>()?.name
      is KtParameter -> parentOfType<KtFunction>()?.name
      else -> null
    }

  /**
   * When displaying the relationship between this provider and a consumer, the description of the
   * relationship has to change when the consumer is a top-level component or entry point.
   */
  private fun ConsumerDaggerElementBase.getRelationDescriptionKey(): String =
    when (this) {
      is ComponentProvisionMethodDaggerElement,
      is EntryPointMethodDaggerElement -> "navigate.to.component.exposes"
      else -> "navigate.to.consumer"
    }

  override fun filterResolveCandidate(resolveCandidate: DaggerElement): Boolean =
    resolveCandidate is ConsumerDaggerElementBase && canProvideFor(resolveCandidate)

  companion object {
    /**
     * Returns whether the given type from a consumer can be provided by the second type.
     *
     * In the simple case, a Consumer consumes the exact type that a Provider provides. But a
     * Consumer can also ask for variations of the type, such as wrapping a type `Foo` as
     * `dagger.Lazy<Foo>`. This method indicates whether the current type is able to be returned by
     * a Provider defined with the specified type.
     */
    @JvmStatic
    protected fun PsiType.matchesProvidedType(providedType: PsiType) =
      (this.unboxed == providedType || this.typeInsideDaggerWrapper() == providedType)
  }
}

internal data class ProviderDaggerElement(
  override val psiElement: PsiElement,
  private val providedPsiType: PsiType,
) : ProviderDaggerElementBase() {

  internal constructor(psiElement: KtClassOrObject) : this(psiElement, psiElement.classToPsiType())

  internal constructor(psiElement: KtFunction) : this(psiElement, psiElement.getReturnedPsiType())

  internal constructor(
    psiElement: KtParameter
  ) : this(psiElement, requireNotNull(psiElement.psiType))

  internal constructor(psiElement: PsiClass) : this(psiElement, psiElement.classToPsiType())

  internal constructor(psiElement: PsiMethod) : this(psiElement, psiElement.getReturnedPsiType())

  internal constructor(psiElement: PsiParameter) : this(psiElement, psiElement.type)

  override val metricsElementType = DaggerEditorEvent.ElementType.PROVIDER

  override fun getIndexKeys(): List<String> {
    val project = psiElement.project
    val scope = project.projectScope()
    return providedPsiType.getIndexKeys() + extraIndexKeysForProvider(project, scope)
  }

  override fun canProvideFor(consumer: ConsumerDaggerElementBase) =
    consumer.consumedType.matchesProvidedType(providedPsiType) &&
      qualifierInfo == consumer.qualifierInfo
}
