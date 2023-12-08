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
package com.android.tools.idea.lang.typedef

import com.android.tools.compose.isDeprecated
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiNamedElement
import javax.swing.Icon
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * Represents an Android typedef (i.e. an annotation that is itself annotated
 * with @IntDef/@LongDef/@StringDef), including the possible values.
 *
 * Example TypeDef:
 * ```kotlin
 *   @IntDef(NORTH, EAST, SOUTH, WEST)
 *   annotation class Direction {
 *     companion object {
 *       const val NORTH = 0
 *       const val EAST = 1
 *       const val SOUTH = 2
 *       const val WEST = 3
 *     }
 *   }
 * ```
 */
internal data class TypeDef(
  val annotation: PsiElement,
  val values: List<PsiElement>,
  val type: Type
) {
  /** Different variants of Android typedefs. */

  /** The type of a typedef - one of `@IntDef`, `@LongDef`, or `@StringDef` */
  internal enum class Type(
    val annotationName: String,
    val javaTypeName: String,
    val kotlinTypeName: String
  ) {
    INT("IntDef", "int", "Int"),
    LONG("LongDef", "long", "Long"),
    STRING("StringDef", "String", "String");

    val annotationFqName = "$ANNOTATION_PREFIX${annotationName}"
  }

  private val valuesSet = values.toSet()

  /** Returns the name of the typedef itself. e.g. `"Direction"` from the example above. */
  private fun getName(): String? =
    when (annotation) {
      is KtClass -> annotation.name
      is PsiClass -> annotation.name
      else -> null
    }

  /**
   * Returns a decorated version of the given [LookupElement], or [delegate] if it does not
   * correspond to a value of this [TypeDef] or required information is missing.
   */
  fun maybeDecorateAndPrioritize(delegate: LookupElement): LookupElement {
    val completionElement = delegate.psiElement?.navigationElement
    if (completionElement !is PsiNamedElement || completionElement !in valuesSet) return delegate
    val fqName = completionElement.kotlinFqName?.asString() ?: return delegate
    val annotationName = getName() ?: return delegate
    val lookupStrings = completionElement.getLookupStrings() ?: return delegate
    val icon = completionElement.getIcon(/* flags= */ 0)
    val isStrikeout =
      when (completionElement) {
        is KtElement -> completionElement.isDeprecated()
        is PsiDocCommentOwner -> completionElement.isDeprecated
        else -> false
      }
    val element =
      TypeDefLookupElementDecorator(
        delegate,
        fqName,
        annotationName,
        lookupStrings,
        icon,
        isStrikeout
      )
    return PrioritizedLookupElement.withPriority(element, HIGH_PRIORITY)
  }

  private class TypeDefLookupElementDecorator(
    delegate: LookupElement,
    private val fqName: String,
    private val annotationName: String,
    private val lookupStrings: List<String>,
    private val icon: Icon,
    private val isStrikeout: Boolean,
  ) : LookupElementDecorator<LookupElement>(delegate) {
    override fun getLookupString() = lookupStrings.first()

    override fun getAllLookupStrings() = lookupStrings.toSet()

    override fun renderElement(presentation: LookupElementPresentation) {
      presentation.icon = icon
      presentation.itemText = lookupStrings.first()
      presentation.isStrikeout = isStrikeout
      presentation.isItemTextBold = true
      val nameWithClasses = lookupStrings.asReversed().joinToString(".")
      fqName.substringBeforeLast(nameWithClasses).trimEnd('.').let {
        presentation.tailText = " ($it)"
      }
      presentation.typeText = "@$annotationName"
    }
  }

  /**
   * Returns all the ways we might start to type this element, i.e. all the parts of its
   * Class/Object-qualified name.
   */
  private fun PsiNamedElement.getLookupStrings(): List<String>? =
    when (this) {
      is KtProperty -> getLookupStrings()
      is PsiField -> getLookupStrings()
      else -> null
    }

  private fun KtProperty.getLookupStrings(): List<String> = buildList {
    name?.let { add(it) } ?: return@buildList
    var cur: KtClassOrObject? = containingClassOrObject
    while (cur != null) {
      cur.name?.let { add(it) } ?: return@buildList
      cur = cur.containingClassOrObject
    }
  }

  private fun PsiField.getLookupStrings(): List<String> = buildList {
    add(name)
    var cur: PsiClass? = containingClass
    while (cur != null) {
      cur.name?.let { add(it) } ?: return@buildList
      cur = cur.containingClass
    }
  }

  companion object {
    private const val ANNOTATION_PREFIX = "androidx.annotation."
    // Historically used value for high priority completion item.
    internal const val HIGH_PRIORITY = 100.0
    /** The [Set] of fully-qualified typedef annotations supported. */
    val ANNOTATION_FQ_NAMES = Type.values().associateBy(TypeDef.Type::annotationFqName)
  }
}
