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
import com.android.tools.idea.dagger.index.getIndexKeys
import com.android.tools.idea.dagger.unboxed
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import kotlin.reflect.KClass
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

typealias DaggerRelatedElement = Pair<DaggerElement, String>

/**
 * Wrapper around a PsiElement that represents an item in the Dagger graph, along with associated
 * data.
 */
sealed class DaggerElement {

  abstract val psiElement: PsiElement

  /** Looks up related Dagger elements. */
  abstract fun getRelatedDaggerElements(): List<DaggerRelatedElement>

  /**
   * Looks up related Dagger elements using [DaggerIndex]. Derived classes should use this to
   * implement the part of [getRelatedDaggerElements] that finds items stored in the index.
   */
  internal inline fun <reified T : DaggerElement> getRelatedDaggerElementsFromIndex(
    indexKeys: List<String>
  ): List<T> = getRelatedDaggerElementsFromIndex(setOf(T::class), indexKeys).map { it as T }

  /**
   * Looks up related Dagger elements using [DaggerIndex]. Derived classes should use this to
   * implement the part of [getRelatedDaggerElements] that finds items stored in the index.
   */
  internal fun getRelatedDaggerElementsFromIndex(
    relatedItemTypes: Set<KClass<out DaggerElement>>,
    indexKeys: List<String>,
  ): List<DaggerElement> {
    val project = psiElement.project
    val scope = project.projectScope()

    return indexKeys
      // Look up the keys in the index
      .flatMap { DaggerIndex.getValues(it, scope) }
      // Remove types we aren't interested in before resolving
      .filter { indexValue ->
        val daggerElementJavaType = indexValue.dataType.daggerElementType.java
        relatedItemTypes.any { type -> type.java.isAssignableFrom(daggerElementJavaType) }
      }
      // Ensure there are no duplicate index values (which can happen if two different keys have
      // identical values)
      .distinct()
      // Resolve index values
      .flatMap { it.resolveToDaggerElements(project, scope) }
      // Ensure there are no duplicate resolved values
      .distinct()
      // Filter out any candidates that are not applicable.
      .filter(this::filterResolveCandidate)
  }

  /**
   * Given a candidate related element that's been resolved from the index, decide if it is actually
   * applicable. This includes comparing [PsiType]s, although the exact comparison depends on the
   * relationship between this [DaggerElement] and the candidate.
   */
  abstract fun filterResolveCandidate(resolveCandidate: DaggerElement): Boolean

  /**
   * Gets the index keys associated with the given [PsiType], using the project and project scope
   * from the current [DaggerElement]'s [PsiElement].
   */
  protected fun PsiType.getIndexKeys() =
    getIndexKeys(this, psiElement.project, psiElement.project.projectScope())
}

fun interface DaggerElementIdentifier<T : PsiElement> {
  /**
   * Returns a [DaggerElement] representing the given [PsiElement], iff the element is somehow used
   * in the Dagger graph.
   */
  fun getDaggerElement(psiElement: T): DaggerElement?
}

class DaggerElementIdentifiers(
  val ktClassIdentifiers: List<DaggerElementIdentifier<KtClassOrObject>> = emptyList(),
  val ktConstructorIdentifiers: List<DaggerElementIdentifier<KtConstructor<*>>> = emptyList(),
  val ktFunctionIdentifiers: List<DaggerElementIdentifier<KtFunction>> = emptyList(),
  val ktParameterIdentifiers: List<DaggerElementIdentifier<KtParameter>> = emptyList(),
  val ktPropertyIdentifiers: List<DaggerElementIdentifier<KtProperty>> = emptyList(),
  val psiClassIdentifiers: List<DaggerElementIdentifier<PsiClass>> = emptyList(),
  val psiFieldIdentifiers: List<DaggerElementIdentifier<PsiField>> = emptyList(),
  val psiMethodIdentifiers: List<DaggerElementIdentifier<PsiMethod>> = emptyList(),
  val psiParameterIdentifiers: List<DaggerElementIdentifier<PsiParameter>> = emptyList(),
) {
  companion object {
    internal inline fun of(vararg identifiers: DaggerElementIdentifiers) = of(identifiers.toList())

    internal fun of(identifiers: List<DaggerElementIdentifiers>) =
      DaggerElementIdentifiers(
        identifiers.flatMap(DaggerElementIdentifiers::ktClassIdentifiers),
        identifiers.flatMap(DaggerElementIdentifiers::ktConstructorIdentifiers),
        identifiers.flatMap(DaggerElementIdentifiers::ktFunctionIdentifiers),
        identifiers.flatMap(DaggerElementIdentifiers::ktParameterIdentifiers),
        identifiers.flatMap(DaggerElementIdentifiers::ktPropertyIdentifiers),
        identifiers.flatMap(DaggerElementIdentifiers::psiClassIdentifiers),
        identifiers.flatMap(DaggerElementIdentifiers::psiFieldIdentifiers),
        identifiers.flatMap(DaggerElementIdentifiers::psiMethodIdentifiers),
        identifiers.flatMap(DaggerElementIdentifiers::psiParameterIdentifiers),
      )
  }

  fun getDaggerElement(psiElement: PsiElement): DaggerElement? {
    return when (psiElement) {
      is KtClassOrObject -> ktClassIdentifiers.getFirstDaggerElement(psiElement)
      is KtFunction ->
        (psiElement as? KtConstructor<*>)?.let {
          ktConstructorIdentifiers.getFirstDaggerElement(psiElement)
        }
          ?: ktFunctionIdentifiers.getFirstDaggerElement(psiElement)
      is KtParameter -> ktParameterIdentifiers.getFirstDaggerElement(psiElement)
      is KtProperty -> ktPropertyIdentifiers.getFirstDaggerElement(psiElement)
      is PsiClass -> psiClassIdentifiers.getFirstDaggerElement(psiElement)
      is PsiField -> psiFieldIdentifiers.getFirstDaggerElement(psiElement)
      is PsiMethod -> psiMethodIdentifiers.getFirstDaggerElement(psiElement)
      is PsiParameter -> psiParameterIdentifiers.getFirstDaggerElement(psiElement)
      else -> null
    }
  }

  private fun <T : PsiElement> List<DaggerElementIdentifier<T>>.getFirstDaggerElement(
    psiElement: T
  ): DaggerElement? = this.firstNotNullOfOrNull { it.getDaggerElement(psiElement) }
}

/**
 * Returns a [DaggerElement] representing the given [PsiElement], iff the element is somehow used in
 * the Dagger graph.
 */
fun PsiElement.getDaggerElement(): DaggerElement? =
  AllConcepts.daggerElementIdentifiers.getDaggerElement(this)

/**
 * Gets a function's return type as a [PsiType]. For a constructor, this is defined as the [PsiType]
 * of the class being constructed.
 */
internal fun KtFunction.getReturnedPsiType(): PsiType =
  (if (this is KtConstructor<*>) containingClass()?.toPsiType() else psiType)!!.unboxed

/**
 * Gets a function's return type as a [PsiType]. For a constructor, this is defined as the [PsiType]
 * of the class being constructed.
 */
internal fun PsiMethod.getReturnedPsiType(): PsiType =
  (if (isConstructor) AndroidPsiUtils.toPsiType(containingClass!!) else returnType)!!.unboxed

/** Returns the [PsiType] representing this class. */
internal fun KtClassOrObject.classToPsiType(): PsiType = toPsiType()!!.unboxed

/** Returns the [PsiType] representing this class. */
internal fun PsiClass.classToPsiType(): PsiType = AndroidPsiUtils.toPsiType(this)!!.unboxed

/** Given a [KtClass] or [PsiClass] as `this`, returns the [PsiType] representing the class. */
internal fun PsiElement.classToPsiType(): PsiType =
  when (this) {
    is KtClassOrObject -> classToPsiType()
    is PsiClass -> classToPsiType()
    else -> throw IllegalArgumentException("Unsupported type ${this::class}")
  }
