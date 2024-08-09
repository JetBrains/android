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
import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass

private const val VALUES_ATTR_NAME = "value"

/**
 * Base class for language-specific [CompletionContributor]s that enhance completions for Android
 * TypeDefs
 */
sealed class TypeDefCompletionContributor : CompletionContributor() {
  private val postDotElementPattern: ElementPattern<PsiElement> =
    PlatformPatterns.psiElement().afterLeaf(".")

  /** A pattern defining where the contributor should run. */
  internal abstract val elementPattern: ElementPattern<PsiElement>

  /** For a given [PsiElement], computes the [TypeDef], if any, that constrains it. */
  internal abstract fun computeConstrainingTypeDef(position: PsiElement): TypeDef?

  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    result: CompletionResultSet,
  ) {
    // First, find the typedef, if it exists.
    val def =
      parameters.position
        .takeIf { elementPattern.accepts(it) }
        ?.let { computeConstrainingTypeDef(it) }

    // If no typedef, just pass the existing results through.
    if (def == null) {
      result.runRemainingContributors(parameters, result::passResult)
      return
    }

    ProgressManager.checkCanceled()

    // Do not add anything if we are after a dot in the completion as we don't want to pollute the
    // results with potentially irrelevant items. Instead, only redecorate where appropriate.
    if (postDotElementPattern.accepts(parameters.position)) {
      result.runRemainingContributors(parameters) {
        val lookupElement = it.lookupElement
        if (lookupElement.psiElement?.navigationElement in def.values) {
          result.passResult(it.withLookupElement(def.maybeDecorateAndPrioritize(it.lookupElement)))
        } else {
          result.passResult(it)
        }
      }
      return
    }

    // Otherwise, add results for the typedef values.
    def.values.forEach {
      val lookupElement = LookupElementBuilder.create(it).withInsertHandler(insertHandler)
      result.addElement(def.maybeDecorateAndPrioritize(lookupElement))
    }

    // Filter out any subsequent results for those typedef values.
    result.runRemainingContributors(parameters) {
      if (it.lookupElement.psiElement?.navigationElement !in def.values) result.passResult(it)
    }
  }

  protected abstract val insertHandler: InsertHandler<LookupElement>

  protected abstract class TypeDefInsertHandler : InsertHandler<LookupElement> {
    protected abstract fun shouldOptimizeImports(project: Project): Boolean

    protected abstract fun bindToTarget(context: InsertionContext, target: PsiElement)

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
      val target = item.psiElement ?: return
      bindToTarget(context, target)
      if (shouldOptimizeImports(context.project)) {
        val psiFile =
          PsiDocumentManager.getInstance(context.project).getPsiFile(context.document) ?: return
        OptimizeImportsProcessor(context.project, psiFile).runWithoutProgress()
      }
    }

    protected fun InsertionContext.getParent(): PsiElement? =
      file.findElementAt(startOffset)?.parent

    protected inline fun <reified T : PsiElement> InsertionContext.getMaximalParentOfType():
      PsiElement? {
      val parent = getParent()
      return parent?.parents(true)?.firstOrNull() { it.parent !is T }
    }
  }

  /**
   * Returns the [TypeDef] for an argument with given [argName] or [argIndex] within this element.
   *
   * For example, if this element is a function whose third argument named "argThree" is a
   * `@StringDef`, then this method will return a [TypeDef] with type [TypeDef.Type.STRING] if
   * called with [argIndex] 2 or [argName] "argThree".
   */
  internal fun PsiElement.getArgumentTypeDef(
    argName: String? = null,
    argIndex: Int = -1,
  ): TypeDef? =
    when (this) {
      is PsiMethod -> getArgumentTypeDef(argIndex)
      is KtFunction -> getArgumentTypeDef(argName, argIndex)
      else -> null
    }

  private fun PsiMethod.getArgumentTypeDef(argIndex: Int): TypeDef? {
    return (parameters.getOrNull(argIndex) as? PsiParameter)
      ?.takeIf { it.isPotentialTypeDefType() }
      ?.annotations
      ?.firstNotNullOfOrNull { it.getReferredTypeDef() }
  }

  /**
   * Heuristically determines whether this type is worth investigating further for being a typedef.
   * May speed things up so that we don't bother trying to find a TypeDef for a parameter that
   * doesn't satisfy this predicate.
   *
   * If you had a `com.example.String`, we would return `true`, but going on to the next step we
   * would find that it was not a `@StringDef`.
   */
  private fun PsiParameter.isPotentialTypeDefType() =
    type == PsiTypes.intType() ||
      type == PsiTypes.longType() ||
      (type as? PsiClassType)?.name == "String"

  private fun KtFunction.getArgumentTypeDef(argName: String?, argIndex: Int): TypeDef? =
    when (argName) {
        null -> valueParameters.getOrNull(argIndex)
        else -> valueParameters.find { it.name == argName }
      }
      ?.annotationEntries
      ?.firstNotNullOfOrNull { it.getReferredTypeDef() }

  /**
   * Returns [TypeDef] for annotation class to which given [KtAnnotationEntry] resolves, or `null`
   * if there is no TypeDef annotation.
   */
  private fun KtAnnotationEntry.getReferredTypeDef(): TypeDef? {
    val annotationElement =
      calleeExpression?.constructorReferenceExpression?.mainReference?.resolve() ?: return null

    return CachedValuesManager.getCachedValue(annotationElement) {
      val source =
        annotationElement.let { if (it is KtPrimaryConstructor) it.containingClass() else it }
      CachedValueProvider.Result(source?.navigationElement?.toTypeDef(), annotationElement)
    }
  }

  /**
   * Returns [TypeDef] for annotation class to which given [PsiAnnotation] resolves, or `null` if
   * there is no TypeDef annotation.
   */
  private fun PsiAnnotation.getReferredTypeDef(): TypeDef? {
    val declaration = nameReferenceElement?.resolve()?.navigationElement
    val annotationElement =
      when {
        declaration is PsiClass && declaration.isAnnotationType -> declaration
        declaration is KtClass && declaration.isAnnotation() -> declaration
        else -> return null
      }

    return CachedValuesManager.getCachedValue(declaration) {
      CachedValueProvider.Result(annotationElement.toTypeDef(), declaration)
    }
  }

  /**
   * Returns [TypeDef] represented by a given [PsiElement] if it is an annotated TypeDef annotation,
   * otherwise `null`.
   */
  private fun PsiElement.toTypeDef(): TypeDef? =
    when (this) {
      is PsiClass -> toTypeDef()
      is KtClass -> toTypeDef()
      else -> null
    }

  private fun PsiClass.toTypeDef(): TypeDef? {
    val typeDefAnnotation =
      annotations.find { it.qualifiedName in TypeDef.ANNOTATION_FQ_NAMES } ?: return null
    val type = TypeDef.ANNOTATION_FQ_NAMES[typeDefAnnotation.qualifiedName] ?: return null
    return TypeDef(this, typeDefAnnotation.getValueAttributeValues(), type)
  }

  private fun KtClass.toTypeDef(): TypeDef? {
    return analyze(this) {
      val (typeDefAnnotation, type) =
        annotationEntries
          .asSequence()
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
    val values =
      findDeclaredAttributeValue(VALUES_ATTR_NAME) as? PsiArrayInitializerMemberValue
        ?: return emptyList()
    return values.initializers.mapNotNull {
      if (it is PsiReferenceExpression) it.resolve()
      else (it.navigationElement as? KtExpression)?.resolveMainReference()
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
    val valueArgument =
      valueArguments.find { it.getArgumentName()?.asName?.asString() == VALUES_ATTR_NAME }
    val valueExpressions =
      (valueArgument?.getArgumentExpression() as? KtCollectionLiteralExpression)?.innerExpressions
    return valueExpressions?.mapNotNull { it.resolveMainReference() }
  }

  /** Handles the case where varargs are passed instead of a named `value` attribute. */
  private fun KtAnnotationEntry.getImplicitValueAttributeValues(): List<PsiElement> =
    valueArguments.mapNotNull { it.getArgumentExpression()?.resolveMainReference() }

  private fun KtExpression.resolveMainReference(): PsiElement? =
    unqualified()?.mainReference?.resolve()

  private tailrec fun KtExpression.unqualified(): KtExpression? =
    when (this) {
      is KtQualifiedExpression -> selectorExpression?.unqualified()
      else -> this
    }
}
