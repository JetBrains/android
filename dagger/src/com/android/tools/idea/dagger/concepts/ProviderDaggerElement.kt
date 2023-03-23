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

import com.android.tools.idea.dagger.concepts.ConsumerDaggerElementBase.Companion.removeWrappingDaggerType
import com.android.tools.idea.dagger.index.getAliasSimpleNames
import com.android.tools.idea.dagger.unboxed
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.util.projectScope

internal data class ProviderDaggerElement(override val psiElement: PsiElement) : DaggerElement() {

  override val daggerType = Type.PROVIDER

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    // Since Dagger allows types to be wrapped with Lazy<> and Provider<> and since our index stores
    // generics by the outermost type's simple name only, we have to search for "Lazy" and
    // "Provider" for all types.
    val project = psiElement.project
    val scope = project.projectScope()
    val indexKeys =
      elementPsiType.getIndexKeys() +
        ConsumerDaggerElementBase.wrappingDaggerTypes.flatMap {
          val simpleName = it.substringAfterLast(".")
          listOf(simpleName) + getAliasSimpleNames(simpleName, project, scope)
        }

    return getRelatedDaggerElementsFromIndex(setOf(Type.CONSUMER), indexKeys).map {
      DaggerRelatedElement(it, (it as ConsumerDaggerElementBase).relatedElementGrouping)
    }
  }

  override fun filterResolveCandidate(resolveCandidate: DaggerElement): Boolean =
    // A consumer may request a wrapped type like `Lazy<Foo>`, but this provider would just be
    // returning the `Foo`.
    elementPsiType == resolveCandidate.psiElement.getPsiType().removeWrappingDaggerType().unboxed
}
