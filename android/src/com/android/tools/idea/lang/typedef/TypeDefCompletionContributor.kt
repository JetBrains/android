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

import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass

private const val VALUES_ATTR_NAME = "value"

/** Base class for language-specific [CompletionContributor]s that enhance completions for Android TypeDefs */
sealed class TypeDefCompletionContributor : CompletionContributor() {
  private val postDotElementPattern: ElementPattern<PsiElement> = PlatformPatterns.psiElement().afterLeaf(".")

  /** A pattern defining where the contributor should run. */
  internal abstract val elementPattern: ElementPattern<PsiElement>

  /** For a given [PsiElement], computes the [TypeDef], if any, that constrains it. */
  internal abstract fun computeConstrainingTypeDef(position: PsiElement): TypeDef?

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    // This method does two things:
    //   1. Redecorate and promote typedef value completions that already exist, and
    //   2. Add in typedef value completions for values that are missing

    // First, find the typedef, if it exists.
    val def = parameters.position.takeIf { elementPattern.accepts(it) }?.let { computeConstrainingTypeDef(it) }

    // If no typedef, just pass the existing results through.
    if (def == null) {
      result.runRemainingContributors(parameters, result::passResult)
      return
    }

    ProgressManager.checkCanceled()

    // Otherwise, redecorate and promote completions that represent the values of the typedef.
    val missingValues = def.values.toMutableSet()
    result.runRemainingContributors(parameters) { completion ->
      val lookupElement = completion.lookupElement
      val navElement = lookupElement.psiElement?.navigationElement
      missingValues.remove(navElement)
      val resultToPass = def.decorateAndPrioritize(lookupElement)?.let(completion::withLookupElement) ?: completion
      result.passResult(resultToPass)
    }

    // Do not add anything if we are after a dot in the completion as we don't want to pollute the
    // results with potentially irrelevant items.
    if (postDotElementPattern.accepts(parameters.position)) return

    // Add in the values we haven't seen.
    // TODO(b/274787926): Anything we add here will not complete properly (i.e. importing, qualifying).
    // TODO(b/274790750): Anything we add here will not rank correctly (it shows up last despite being
    //  Prioritized). Once we figure out how to correctly add completions we should add them first and
    //  then skip dupes when running remaining contributors.
    for (value in missingValues) {
      def.decorateAndPrioritize(LookupElementBuilder.create(value))?.let(result::addElement)
    }
  }

  /**
   * Returns the [TypeDef] for an argument with given [argName] or [argIndex] within this element.
   *
   * For example, if this element is a function whose third argument named "argThree" is a `@StringDef`,
   * then this method will return a [TypeDef] with type [TypeDef.Type.STRING] if called with [argIndex] 2
   * or [argName] "argThree".
   */
  internal fun PsiElement.getArgumentTypeDef(argName: String? = null, argIndex: Int = -1): TypeDef? =
    when (this) {
      is PsiMethod -> getArgumentTypeDef(argIndex)
      is KtFunction -> getArgumentTypeDef(argName, argIndex)
      else -> null
    }

  private fun PsiMethod.getArgumentTypeDef(argIndex: Int): TypeDef? {
    return (parameters.getOrNull(argIndex) as? PsiParameter)
      ?.takeIf { it.isPotentialTypeDefType() }
      ?.annotations?.firstNotNullOfOrNull { it.getReferredTypeDef() }
  }

  /**
   * Heuristically determines whether this type is worth investigating further for being a typedef.
   * May speed things up so that we don't bother trying to find a TypeDef for a parameter that doesn't
   * satisfy this predicate.
   *
   * If you had a `com.example.String`, we would return `true`, but going on to the next step we would
   * find that it was not a `@StringDef`.
   */
  private fun PsiParameter.isPotentialTypeDefType() =
    type == PsiTypes.intType() || type == PsiTypes.longType() || (type as? PsiClassType)?.name == "String"

  private fun KtFunction.getArgumentTypeDef(argName: String?, argIndex: Int): TypeDef? = when (argName) {
    null -> valueParameters.getOrNull(argIndex)
    else -> valueParameters.find { it.name == argName }
  }?.annotationEntries?.firstNotNullOfOrNull { it.getReferredTypeDef() }

  /**
   * Returns [TypeDef] for annotation class to which given [KtAnnotationEntry] resolves,
   * or `null` if there is no TypeDef annotation.
   */
  private fun KtAnnotationEntry.getReferredTypeDef(): TypeDef? {
    val annotationElement = calleeExpression?.constructorReferenceExpression?.mainReference?.resolve() ?: return null

    return CachedValuesManager.getCachedValue(annotationElement) {
      val source = annotationElement.let { if (it is KtPrimaryConstructor) it.containingClass() else it }
      CachedValueProvider.Result(source?.navigationElement?.toTypeDef(), annotationElement)
    }
  }

  /**
   * Returns [TypeDef] for annotation class to which given [PsiAnnotation] resolves,
   * or `null` if there is no TypeDef annotation.
   */
  private fun PsiAnnotation.getReferredTypeDef(): TypeDef? {
    val declaration = nameReferenceElement?.resolve()?.navigationElement
    val annotationElement = when {
      declaration is PsiClass && declaration.isAnnotationType -> declaration
      declaration is KtClass && declaration.isAnnotation() -> declaration
      else -> return null
    }

    return CachedValuesManager.getCachedValue(declaration) {
      CachedValueProvider.Result(annotationElement.toTypeDef(), declaration)
    }
  }

  /** Returns [TypeDef] represented by a given [PsiElement] if it is an annotated TypeDef annotation, otherwise `null`. */
  private fun PsiElement.toTypeDef(): TypeDef? = when (this) {
    is PsiClass -> toTypeDef()
    is KtClass -> toTypeDef()
    else -> null
  }

  private fun PsiClass.toTypeDef(): TypeDef? {
    val typeDefAnnotation = annotations.find { it.qualifiedName in TypeDef.ANNOTATION_FQ_NAMES } ?: return null
    val type = TypeDef.ANNOTATION_FQ_NAMES[typeDefAnnotation.qualifiedName] ?: return null
    return TypeDef(this, typeDefAnnotation.getValueAttributeValues(), type)
  }

  private fun KtClass.toTypeDef(): TypeDef? {
    return analyze(this) {
      val (typeDefAnnotation, type) = annotationEntries.asSequence()
        .mapNotNull { annotationEntry ->
          val qualifiedName = annotationEntry.getQualifiedName(this)
          TypeDef.ANNOTATION_FQ_NAMES[qualifiedName]?.let { annotationEntry to it }
        }
        .firstOrNull() ?: return null
      TypeDef(this@toTypeDef, typeDefAnnotation.getValueAttributeValues(), type)
    }
  }

  /** Returns values of attribute "value". */
  private fun PsiAnnotation.getValueAttributeValues(): List<PsiElement> {
    val values = findDeclaredAttributeValue(VALUES_ATTR_NAME) as? PsiArrayInitializerMemberValue ?: return emptyList()
    return values.initializers.mapNotNull {
      if (it is PsiReferenceExpression) it.resolve()
      else (it.navigationElement as? KtExpression)?.unqualified()?.mainReference?.resolve()
    }
  }

  /**
   * Returns values of attribute "value", or varargs if no attribute is named "value".
   *
   * The `value` argument to typedef annotations can be named or vararg, e.g.
   * * @IntDef(THING_ONE, THING_TWO)
   * * @IntDef(value = [THING_ONE, THING_TWO])
   * * @IntDef(flag = true, value = [THING_ONE, THING_TWO])
   *
   * So the "value" parameter may or may not be explicitly named.
   */
  private fun KtAnnotationEntry.getValueAttributeValues(): List<PsiElement> =
    getExplicitValueAttributeValues() ?: getImplicitValueAttributeValues()

  /** Handles the case where the `value` attribute is explicitly named. */
  private fun KtAnnotationEntry.getExplicitValueAttributeValues(): List<PsiElement>? {
    val namedValueAttribute = valueArguments.find { it.getArgumentName()?.asName?.asString() == VALUES_ATTR_NAME }
    return (namedValueAttribute?.getArgumentExpression() as? KtCollectionLiteralExpression)?.getInnerExpressions()
      ?.mapNotNull { (it.unqualified() as? KtReferenceExpression)?.mainReference?.resolve() }
  }

  /** Handles the case where varargs are passed instead of a named `value` attribute. */
  private fun KtAnnotationEntry.getImplicitValueAttributeValues(): List<PsiElement> =
    valueArguments.mapNotNull { (it.getArgumentExpression()?.unqualified() as? KtReferenceExpression)?.mainReference?.resolve() }

  private tailrec fun KtExpression.unqualified(): KtExpression? = when (this) {
    is KtQualifiedExpression -> selectorExpression?.unqualified()
    else -> this
  }
}
