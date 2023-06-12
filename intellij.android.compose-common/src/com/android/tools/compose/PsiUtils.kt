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
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

private val composableFunctionKey = Key.create<CachedValue<KtAnnotationEntry?>>("com.android.tools.compose.PsiUtil.isComposableFunction")
private val deprecatedKey = Key.create<CachedValue<KtAnnotationEntry?>>("com.android.tools.compose.PsiUtil.isDeprecated")

fun PsiElement.isComposableFunction(): Boolean = this.getComposableAnnotation() != null

fun PsiElement.getComposableAnnotation(): KtAnnotationEntry? =
  (this as? KtNamedFunction)?.getAnnotationWithCaching(composableFunctionKey) { it.isComposableAnnotation() }

/**
 * K2 version of [isComposableFunction].
 */
fun KtAnalysisSession.isComposableFunction(element: PsiElement): Boolean =
  (element as? KtNamedFunction)?.getAnnotationWithCaching(composableFunctionKey) { isComposableAnnotation(it) } != null

fun PsiElement.isDeprecated(): Boolean =
  (this as? KtAnnotated)?.getAnnotationWithCaching(deprecatedKey) { it.isDeprecatedAnnotation() } != null

private fun KtAnnotated.getAnnotationWithCaching(key: Key<CachedValue<KtAnnotationEntry?>>, doCheck: (KtAnnotationEntry) -> Boolean)
  : KtAnnotationEntry? {
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

  return fqNameMatches(COMPOSABLE_FQ_NAMES)
}

/**
 * K2 version of [isComposableAnnotation].
 */
fun KtAnalysisSession.isComposableAnnotation(element: PsiElement): Boolean {
  if (element !is KtAnnotationEntry) return false

  return fqNameMatches(element, COMPOSABLE_FQ_NAMES)
}

private const val DEPRECATED_ANNOTATION_NAME = "Deprecated"

private val DEPRECATED_FQ_NAMES = setOf(
  "kotlin.$DEPRECATED_ANNOTATION_NAME",
  "java.lang.$DEPRECATED_ANNOTATION_NAME"
)

private fun KtAnnotationEntry.isDeprecatedAnnotation() =
  // fqNameMatches is expensive, so we first verify that the short name of the annotation matches.
  shortName?.identifier == DEPRECATED_ANNOTATION_NAME && fqNameMatches(DEPRECATED_FQ_NAMES)

fun PsiElement.isInsideComposableCode(): Boolean {
  // TODO: also handle composable lambdas.
  return language == KotlinLanguage.INSTANCE && parentOfType<KtNamedFunction>()?.isComposableFunction() == true
}
