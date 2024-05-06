/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("AndroidComposablePsiUtils")

package com.android.tools.compose

import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.psi.hasInlineModifier
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

private val composableFunctionKey =
  Key.create<CachedValue<KtAnnotationEntry?>>(
    "com.android.tools.compose.PsiUtil.isComposableFunction"
  )
private val deprecatedKey =
  Key.create<CachedValue<KtAnnotationEntry?>>("com.android.tools.compose.PsiUtil.isDeprecated")
private val COMPOSABLE_CLASS_ID =
  ClassId(FqName("androidx.compose.runtime"), Name.identifier("Composable"))

@OptIn(KtAllowAnalysisOnEdt::class)
fun PsiElement.isComposableFunction(): Boolean =
  if (KotlinPluginModeProvider.isK2Mode()) {
    (this as? KtNamedFunction)?.getAnnotationWithCaching(composableFunctionKey) { annotationEntry ->
      allowAnalysisOnEdt { analyze(annotationEntry) { isComposableAnnotation(annotationEntry) } }
    } != null
  } else {
    this.getComposableAnnotation() != null
  }

fun PsiElement.getComposableAnnotation(): KtAnnotationEntry? =
  (this as? KtNamedFunction)?.getAnnotationWithCaching(composableFunctionKey) {
    it.isComposableAnnotation()
  }

fun PsiElement.isDeprecated(): Boolean =
  (this as? KtAnnotated)?.getAnnotationWithCaching(deprecatedKey) { it.isDeprecatedAnnotation() } !=
    null

private fun KtAnnotated.getAnnotationWithCaching(
  key: Key<CachedValue<KtAnnotationEntry?>>,
  doCheck: (KtAnnotationEntry) -> Boolean
): KtAnnotationEntry? {
  return CachedValuesManager.getCachedValue(this, key) {
    val annotationEntry = annotationEntries.firstOrNull { doCheck(it) }
    val containingKtFile = this.containingKtFile

    CachedValueProvider.Result.create(
      // TODO: see if we can handle alias imports without ruining performance.
      annotationEntry,
      containingKtFile,
      ProjectRootModificationTracker.getInstance(project)
    )
  }
}

fun PsiElement.isComposableAnnotation(): Boolean {
  if (this !is KtAnnotationEntry) return false

  return fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME)
}

/** K2 version of [isComposableAnnotation]. */
fun KtAnalysisSession.isComposableAnnotation(element: PsiElement): Boolean {
  if (element !is KtAnnotationEntry) return false

  return fqNameMatches(element, COMPOSABLE_ANNOTATION_FQ_NAME)
}

private const val DEPRECATED_ANNOTATION_NAME = "Deprecated"

private val DEPRECATED_FQ_NAMES =
  setOf("kotlin.$DEPRECATED_ANNOTATION_NAME", "java.lang.$DEPRECATED_ANNOTATION_NAME")

private fun KtAnnotationEntry.isDeprecatedAnnotation() =
  // fqNameMatches is expensive, so we first verify that the short name of the annotation matches.
  shortName?.identifier == DEPRECATED_ANNOTATION_NAME && fqNameMatches(DEPRECATED_FQ_NAMES)

fun PsiElement.isInsideComposableCode(): Boolean {
  return language == KotlinLanguage.INSTANCE && parentOfType<KtElement>()?.composableScope() != null
}

/** Returns the `@Composable` scope around this [KtElement]. */
fun KtElement.composableScope(): KtExpression? =
  composableHolderAndScope()?.let { (holder, scope) ->
    scope.takeIf { holder.hasComposableAnnotation() }
  }

/**
 * Returns the [KtModifierListOwner] that should hold the `@Composable` annotation for this
 * [KtElement], irrespective of whether it actually has the annotation.
 */
fun KtElement.expectedComposableAnnotationHolder(): KtModifierListOwner? =
  composableHolderAndScope()?.first

/**
 * Returns the [KtModifierListOwner] that we would expect to be holding the `@Composable` annotation
 * as well as what would be the `@Composable` scope for `this` [KtElement].
 */
private tailrec fun KtElement.composableHolderAndScope(): Pair<KtModifierListOwner, KtExpression>? {
  when (val scope = possibleComposableScope()) {
    // Named function - the holder and the scope are the same
    is KtNamedFunction -> return scope to scope
    // Property accessor - if it's a getter, the holder and the scope are the same, otherwise, we
    // have neither
    is KtPropertyAccessor -> return scope.takeIf(KtPropertyAccessor::isGetter)?.let { it to it }
    // Lambdas are the most complicated case
    is KtLambdaExpression -> {
      val lambdaParent = scope.parent
      // If we have a lambda argument, then either it's inline, in which case we skip and keep going
      // up, or it's not, in which case, we need to look at the function parameter's type.
      if (lambdaParent is KtValueArgument) {
        val function = lambdaParent.toFunction() ?: return null
        val param = function.getParameterForArgument(lambdaParent) ?: return null
        // If this is an inline function and we're not a noinline parameter, then we keep going up.
        if (function.hasInlineModifier() && !param.hasModifier(KtTokens.NOINLINE_KEYWORD)) {
          return lambdaParent.composableHolderAndScope()
        }
        // Otherwise, the type of the parameter is where the annotation would go, and the lambda
        // itself is the scope.
        return param.typeReference?.let { it to scope }
      }
      // If we're a lambda stored in a property, and that property has an explicit type, then the
      // type has to be annotated. The lambda is again the actual scope.
      if (lambdaParent is KtProperty) {
        val typeReference = lambdaParent.typeReference
        if (typeReference != null) return typeReference to scope
      }
      // Otherwise, the function literal itself may be annotated, and type inference makes this
      // work. In this case, the function literal is the annotation holder, and the lambda is
      // again the scope.
      val functionLiteral = scope.children.singleOrNull() as? KtFunctionLiteral
      if (functionLiteral != null) return functionLiteral to scope
    }
  }
  // None of the cases we understand worked out, so we don't have anything to return.
  return null
}

private fun KtElement.possibleComposableScope(): KtExpression? =
  parentOfTypes(
      KtNamedFunction::class,
      KtPropertyAccessor::class,
      KtLambdaExpression::class,
      KtClassInitializer::class
    )
    ?.takeIf { it !is KtClassInitializer }

private fun KtModifierListOwner.hasComposableAnnotation(): Boolean =
  if (KotlinPluginModeProvider.isK2Mode()) {
    hasAnnotation(COMPOSABLE_CLASS_ID)
  } else {
    when (this) {
      is KtFunction ->
        descriptor?.annotations?.findAnnotation(COMPOSABLE_CLASS_ID.asSingleFqName()) != null
      is KtTypeReference,
      is KtPropertyAccessor -> annotationEntries.any { it.isComposableAnnotation() }
      else -> false
    }
  }

private fun KtValueArgument.toFunction(): KtFunction? {
  val callee = parentOfType<KtCallExpression>()?.calleeExpression?.mainReference?.resolve()
  if (callee is KtFunction) return callee
  return (callee as? KtProperty)?.initializer as? KtFunction
}

private fun KtFunction.getParameterForArgument(argument: KtValueArgument): KtParameter? {
  // If it's a lambda argument, it's always the last one.
  if (argument is KtLambdaArgument) return valueParameters.lastOrNull()

  // If it's a named argument, then we have to look it up in our parameter list.
  val argumentName = argument.getArgumentName()?.asName?.asString()
  if (argumentName != null) return valueParameters.firstOrNull { it.name == argumentName }

  // Otherwise, it's a positional argument, so just take its current position.
  return (argument.parent as? KtValueArgumentList)
    ?.arguments
    ?.indexOf(argument)
    ?.let(valueParameters::getOrNull)
}
