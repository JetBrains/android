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
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.psiType
import com.android.tools.idea.kotlin.toPsiType
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import kotlin.reflect.KClass
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/**
 * Wrapper around a PsiElement that represents an item in the Dagger graph, along with associated
 * data.
 */
sealed class DaggerElement {

  abstract val psiElement: PsiElement

  abstract val metricsElementType: DaggerEditorEvent.ElementType

  /** Looks up related Dagger elements. */
  fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    if (StudioFlags.DAGGER_CACHE_RELATED_ELEMENTS.get()) {
      return CachedValuesManager.getCachedValue(psiElement) {
        CachedValueProvider.Result(
          doGetRelatedDaggerElements(),
          PsiModificationTracker.MODIFICATION_COUNT,
        )
      }
    }
    return doGetRelatedDaggerElements()
  }

  /** Looks up related Dagger elements. */
  protected abstract fun doGetRelatedDaggerElements(): List<DaggerRelatedElement>

  /**
   * Looks up related Dagger elements using [DaggerIndex]. Derived classes should use this to
   * implement the part of [doGetRelatedDaggerElements] that finds items stored in the index.
   */
  internal inline fun <reified T : DaggerElement> getRelatedDaggerElementsFromIndex(
    indexKeys: List<String>
  ): List<T> = getRelatedDaggerElementsFromIndex(setOf(T::class), indexKeys).map { it as T }

  /**
   * Looks up related Dagger elements using [DaggerIndex]. Derived classes should use this to
   * implement the part of [doGetRelatedDaggerElements] that finds items stored in the index.
   */
  internal fun getRelatedDaggerElementsFromIndex(
    relatedItemTypes: Set<KClass<out DaggerElement>>,
    indexKeys: List<String>,
  ): List<DaggerElement> {
    val project = psiElement.project
    val scope = project.allScope()

    return indexKeys
      .asSequence()
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
      .toList()
  }

  /**
   * Given a candidate related element that's been resolved from the index, decide if it is actually
   * applicable. This includes comparing [PsiType]s, although the exact comparison depends on the
   * relationship between this [DaggerElement] and the candidate.
   */
  protected abstract fun filterResolveCandidate(resolveCandidate: DaggerElement): Boolean

  /**
   * Gets the index keys associated with the given [PsiType], using the project and project scope
   * from the current [DaggerElement]'s [PsiElement].
   */
  protected fun PsiType.getIndexKeys() =
    getIndexKeys(this, psiElement.project, psiElement.project.allScope())
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
    return when (val origin = psiElement.kotlinOriginOrSelf) {
      is KtClassOrObject -> ktClassIdentifiers.getFirstDaggerElement(origin)
      is KtFunction -> {
        (origin as? KtConstructor<*>)?.let { ktConstructorIdentifiers.getFirstDaggerElement(it) }
          ?: ktFunctionIdentifiers.getFirstDaggerElement(origin)
      }
      is KtParameter -> ktParameterIdentifiers.getFirstDaggerElement(origin)
      is KtProperty -> ktPropertyIdentifiers.getFirstDaggerElement(origin)
      is PsiClass -> psiClassIdentifiers.getFirstDaggerElement(origin)
      is PsiField -> psiFieldIdentifiers.getFirstDaggerElement(origin)
      is PsiMethod -> psiMethodIdentifiers.getFirstDaggerElement(origin)
      is PsiParameter -> psiParameterIdentifiers.getFirstDaggerElement(origin)
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

/** Given a [PsiElement], returns its origin [KtElement] if it has one. */
private val PsiElement.kotlinOrigin: KtElement?
  get() =
    when (this) {
      is KtElement -> this
      is KtLightElement<*, *> -> kotlinOrigin
      else -> null
    }

/** Given a [PsiElement], returns its origin [KtElement], or itself if it is not from Kotlin. */
internal val PsiElement.kotlinOriginOrSelf: PsiElement
  get() = kotlinOrigin ?: this

/**
 * A [DaggerElement] that is related to some other source [DaggerElement]. For example, if the
 * source is a Consumer of the type `Foo`, this related element may refer to a Provider of that type
 * `Foo`.
 */
data class DaggerRelatedElement(
  /** Related [DaggerElement]. */
  val relatedElement: DaggerElement,
  /**
   * Display string for the type of related element. Multiple elements with the same group name can
   * be displayed together.
   */
  val groupName: String,
  /**
   * Longer-form description of the relationship between a source element and this element. This is
   * a key and should correspond to a value in DaggerBundle.properties. The value should have {0}
   * and {1} placeholders, to be filled in by the "from" element and "to" element display names
   * respectively.
   */
  @PropertyKey(resourceBundle = DaggerBundle.BUNDLE_NAME) val relationDescriptionKey: String,
  /**
   * A custom display name to use when filling in the "to" element placeholder in
   * [relationDescriptionKey]. If this is null, a standard presentation of the underlying
   * [PsiElement] is used.
   */
  val customDisplayName: String? = null,
)
