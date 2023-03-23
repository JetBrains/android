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
import com.android.tools.idea.dagger.unboxed
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtFunction

internal abstract class ProviderDaggerElementBase : DaggerElement() {

  protected abstract fun getIndexKeys(): List<String>

  /** Gets info for any @Qualifier annotations on this element. */
  protected val qualifierInfo by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) { psiElement.getQualifierInfo() }

  abstract fun canProvideFor(consumer: ConsumerDaggerElementBase): Boolean

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> =
    getRelatedDaggerElementsFromIndex<ConsumerDaggerElementBase>(getIndexKeys()).map {
      DaggerRelatedElement(it, it.relatedElementGrouping)
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
  private val providedPsiType: PsiType
) : ProviderDaggerElementBase() {

  internal constructor(psiElement: KtFunction) : this(psiElement, psiElement.getReturnedPsiType())
  internal constructor(psiElement: PsiMethod) : this(psiElement, psiElement.getReturnedPsiType())

  override fun getIndexKeys(): List<String> {
    val project = psiElement.project
    val scope = project.projectScope()
    return providedPsiType.getIndexKeys() + extraIndexKeysForProvider(project, scope)
  }

  override fun canProvideFor(consumer: ConsumerDaggerElementBase) =
    consumer.consumedType.matchesProvidedType(providedPsiType) &&
      qualifierInfo == consumer.qualifierInfo
}
