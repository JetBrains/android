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
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
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
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
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
  if (isK2Plugin()) {
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
tailrec fun KtElement.composableScope(): KtExpression? {
  return when (val nextParent = parentOfTypes(KtNamedFunction::class, KtLambdaExpression::class)) {
    // Always stop at a named function - if it's not composable, we're done.
    is KtNamedFunction -> nextParent.takeIf { it.hasComposableAnnotation() }
    // A lambda that is a @Composable function argument may be what recomposes, unless it is
    // inlined.
    is KtLambdaExpression -> {
      val argument = nextParent.parent as? KtValueArgument ?: return null
      val function = argument.toFunction() ?: return null
      val param = function.getParameterForArgument(argument) ?: return null
      if (function.hasInlineModifier() && !param.hasModifier(KtTokens.NOINLINE_KEYWORD)) {
        // If it's inlined then continue up to the enclosing function (i.e. recurse).
        argument.composableScope()
      } else {
        nextParent.takeIf { param.typeReference?.hasComposableAnnotation() == true }
      }
    }
    else -> null
  }
}

private fun KtFunction.hasComposableAnnotation(): Boolean =
  if (isK2Plugin()) {
    hasAnnotation(COMPOSABLE_CLASS_ID)
  } else {
    descriptor?.annotations?.findAnnotation(COMPOSABLE_CLASS_ID.asSingleFqName()) != null
  }

private fun KtTypeReference.hasComposableAnnotation() =
  if (isK2Plugin()) {
    hasAnnotation(COMPOSABLE_CLASS_ID)
  } else {
    annotationEntries.any { it.isComposableAnnotation() }
  }

private fun KtValueArgument.toFunction(): KtFunction? =
  parentOfType<KtCallExpression>()?.calleeExpression?.mainReference?.resolve() as? KtFunction

private fun KtFunction.getParameterForArgument(argument: KtValueArgument): KtParameter? {
  // If it's a lambda argument, it's always the last one.
  if (argument is KtLambdaArgument) return valueParameters.lastOrNull()

  // If it's a named argument, then we have to look it up in our parameter list.
  val argumentName = argument.getArgumentName()?.asName?.asString()
  if (argumentName != null) return valueParameters.first { it.name == argumentName }

  // Otherwise, it's a positional argument, so just take its current position.
  return (argument.parent as? KtValueArgumentList)
    ?.arguments
    ?.indexOf(argument)
    ?.let(valueParameters::getOrNull)
}
