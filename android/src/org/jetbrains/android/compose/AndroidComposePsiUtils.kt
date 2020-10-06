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

package org.jetbrains.android.compose

import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction

fun PsiElement.isComposableFunction(): Boolean {
  if (this !is KtNamedFunction) return false

  return CachedValuesManager.getCachedValue(this) {
    val hasComposableAnnotation = annotationEntries.any { it.fqNameMatches(COMPOSABLE_FQ_NAMES) }
    val containingKtFile = this.containingKtFile

    CachedValueProvider.Result.create(
      // TODO: see if we can handle alias imports without ruining performance.
      hasComposableAnnotation,
      containingKtFile,
      ProjectRootModificationTracker.getInstance(project)
    )
  }
}

fun PsiElement.isComposableAnnotation():Boolean {
  if (this !is KtAnnotationEntry) return false
  val fqName = this.getQualifiedName() ?: return false
  return COMPOSABLE_FQ_NAMES.any { it == fqName }
}

fun PsiElement.isInsideComposableCode(): Boolean {
  // TODO: also handle composable lambdas.
  return language == KotlinLanguage.INSTANCE && parentOfType<KtNamedFunction>()?.isComposableFunction() == true
}
