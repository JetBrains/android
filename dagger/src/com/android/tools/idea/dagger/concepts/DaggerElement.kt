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

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.dagger.index.DaggerIndex
import com.android.tools.idea.dagger.unboxed
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/** Wrapper around a PsiElement that represents an item in the Dagger graph, along with associated data. */
data class DaggerElement internal constructor(val psiElement: PsiElement, val daggerType: Type, val psiType: PsiType) {
  internal constructor(psiElement: PsiElement, type: Type) : this(psiElement, type, psiElement.getPsiType())

  enum class Type {
    PROVIDER,
    CONSUMER
  }

  /** Look up related Dagger items using [DaggerIndex]. */
  fun getRelatedDaggerItems(relatedItemTypes: Set<Type>, scope: GlobalSearchScope): List<DaggerElement> {
    return DaggerIndex
      // Get index keys
      .getIndexKeys(psiType.canonicalText, psiElement.project, scope)
      // Look up the keys in the index
      .flatMap { DaggerIndex.getValues(it, scope) }
      // Remove types we aren't interested in before resolving
      .filter { it.dataType.daggerElementType in relatedItemTypes }
      // Ensure there are no duplicate index values (which can happen if two different keys have identical values)
      .distinct()
      // Resolve index values
      .flatMap { it.resolveToDaggerElements(psiType, psiElement.project, scope) }
      // Ensure there are no duplicate resolved values
      .distinct()
  }
}

fun interface DaggerElementIdentifier<T : PsiElement> {
  /** Returns a [DaggerElement] representing the given [PsiElement], iff the element is somehow used in the Dagger graph. */
  fun getDaggerElement(psiElement: T): DaggerElement?
}

class DaggerElementIdentifiers(
  val ktConstructorIdentifiers: List<DaggerElementIdentifier<KtConstructor<*>>> = emptyList(),
  val ktFunctionIdentifiers: List<DaggerElementIdentifier<KtFunction>> = emptyList(),
  val ktParameterIdentifiers: List<DaggerElementIdentifier<KtParameter>> = emptyList(),
  val ktPropertyIdentifiers: List<DaggerElementIdentifier<KtProperty>> = emptyList(),
  val psiFieldIdentifiers: List<DaggerElementIdentifier<PsiField>> = emptyList(),
  val psiMethodIdentifiers: List<DaggerElementIdentifier<PsiMethod>> = emptyList(),
  val psiParameterIdentifiers: List<DaggerElementIdentifier<PsiParameter>> = emptyList(),
) {
  companion object {
    internal inline fun of(vararg identifiers: DaggerElementIdentifiers) = of(identifiers.toList())

    internal fun of(identifiers: List<DaggerElementIdentifiers>) = DaggerElementIdentifiers(
      identifiers.flatMap(DaggerElementIdentifiers::ktConstructorIdentifiers),
      identifiers.flatMap(DaggerElementIdentifiers::ktFunctionIdentifiers),
      identifiers.flatMap(DaggerElementIdentifiers::ktParameterIdentifiers),
      identifiers.flatMap(DaggerElementIdentifiers::ktPropertyIdentifiers),
      identifiers.flatMap(DaggerElementIdentifiers::psiFieldIdentifiers),
      identifiers.flatMap(DaggerElementIdentifiers::psiMethodIdentifiers),
      identifiers.flatMap(DaggerElementIdentifiers::psiParameterIdentifiers),
    )
  }

  fun getDaggerElement(psiElement: PsiElement): DaggerElement? {
    return when (psiElement) {
      is KtFunction ->
        (psiElement as? KtConstructor<*>)?.let { ktConstructorIdentifiers.getFirstDaggerElement(psiElement) }
        ?: ktFunctionIdentifiers.getFirstDaggerElement(psiElement)

      is KtParameter -> ktParameterIdentifiers.getFirstDaggerElement(psiElement)
      is KtProperty -> ktPropertyIdentifiers.getFirstDaggerElement(psiElement)
      is PsiField -> psiFieldIdentifiers.getFirstDaggerElement(psiElement)
      is PsiMethod -> psiMethodIdentifiers.getFirstDaggerElement(psiElement)
      is PsiParameter -> psiParameterIdentifiers.getFirstDaggerElement(psiElement)
      else -> null
    }
  }

  private fun <T : PsiElement> List<DaggerElementIdentifier<T>>.getFirstDaggerElement(psiElement: T): DaggerElement? =
    this.firstNotNullOfOrNull { it.getDaggerElement(psiElement) }
}

/** Returns a [DaggerElement] representing the given [PsiElement], iff the element is somehow used in the Dagger graph. */
fun PsiElement.getDaggerElement(): DaggerElement? =
  AllConcepts.daggerElementIdentifiers.getDaggerElement(this)

/**
 * Every Dagger element deals with a specific JVM type by providing it, consuming it, etc. This utility finds the appropriate type for a
 * [PsiElement] based upon what type of Java or Kotlin element it actually is.
 */
internal fun PsiElement.getPsiType(): PsiType =
  when (this) {
    is KtClass -> toPsiType()
    is KtFunction ->
      if (this is KtConstructor<*>) containingClass()?.toPsiType()
      else psiType

    is KtParameter -> psiType
    is KtProperty -> psiType
    is PsiClass -> AndroidPsiUtils.toPsiType(this)
    is PsiField -> type
    is PsiMethod ->
      if (isConstructor) containingClass!!.getPsiType()
      else returnType

    is PsiParameter -> type
    else -> throw IllegalArgumentException("Unknown element type ${this::class.java}")
  }!!.unboxed
