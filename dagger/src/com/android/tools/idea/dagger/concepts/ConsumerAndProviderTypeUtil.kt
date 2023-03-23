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

import com.android.tools.idea.dagger.index.getAliasSimpleNames
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.search.GlobalSearchScope

private const val LAZY = "dagger.Lazy"
private const val PROVIDER = "javax.inject.Provider"

private val wrappingDaggerTypeFqNames = listOf(LAZY, PROVIDER)
private val wrappingDaggerTypeSimpleNames =
  wrappingDaggerTypeFqNames.map { it.substringAfterLast(".") }

/**
 * Gets index keys to use when looking up related [DaggerElement]s from a [ProviderDaggerElement].
 *
 * Some index keys need to be added when looking up related elements for all providers. For example,
 * if a provider provides type `Foo`, then a consumer can request a `Lazy<Foo>`. That consumer would
 * be stored in the index under "Lazy", so we always need to add that value to the keys being looked
 * up.
 */
internal fun extraIndexKeysForProvider(project: Project, scope: GlobalSearchScope) =
  wrappingDaggerTypeSimpleNames.flatMap { listOf(it) + getAliasSimpleNames(it, project, scope) }

/**
 * Dagger allows consumers to wrap a requested type with Lazy<>, Provider<>, or Provider<Lazy<>>.
 * This method removes any of those wrappers, returning the inner type.
 */
internal fun PsiType.withoutWrappingDaggerType(): PsiType {
  if (
    this is PsiClassReferenceType &&
      rawType().canonicalText in wrappingDaggerTypeFqNames &&
      parameters.isNotEmpty()
  ) {
    return parameters[0].withoutWrappingDaggerType()
  }

  return this
}
