// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.compose.debug

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.debugger.core.ClassNameProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression

/**
 * The Compose Compiler Plugin transforms 'Composable' lambdas into singletons.
 * For each source file, a corresponding 'ComposableSingletons$<ClassName>' class is emitted, containing the lambda.
 * The exact name of this class is computed by [computeComposableSingletonsClassName] and contributed as candate for lambdas.
 */
internal class ComposeClassNameContributor : ClassNameProvider.ClassNameContributor {
  override fun contributeClassNameCandidatesForElement(element: PsiElement): List<String> {
    /* Contribute the `ComposableSingleton` as top level name */
    if(element is KtFile) return listOf(computeComposableSingletonsClassName(element))

    /* Contribute the `ComposableSingleton` as name for lambdas */
    val file = element.containingFile as? KtFile ?: return emptyList()
    if (element is KtLambdaExpression) {
      return listOf(computeComposableSingletonsClassName(file))
    }
    return emptyList()
  }
}