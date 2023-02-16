/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.compose.code.completion

import com.android.tools.compose.isComposableFunction
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Custom [CompletionWeigher] which moves Composable functions up the completion list.
 *
 * It doesn't give Composable functions "absolute" priority, some weighers are hardcoded to run first: specifically one that puts prefix
 * matches above [LookupElement]s where the match is in the middle of the name. Overriding this behavior would require an extension point in
 * [org.jetbrains.kotlin.idea.completion.CompletionSession.createSorter].
 *
 * See [com.intellij.codeInsight.completion.PrioritizedLookupElement] for more information on how ordering of [LookupElement]s works and how
 * to debug it.
 */
class ComposeCompletionWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Int {
    if (!location.completionParameters.isInComposeEnabledModuleAndFile()) return 0

    // Since Compose uses so many named arguments, promote them to the top. This is for a case where the user has typed something like
    // "Button(en<caret>)", and we want to promote the completion "enabled = Boolean".
    if (element.isNamedArgumentCompletion()) return 3

    if (location.completionParameters.isForStatement()) {
      // For statements, ensure that the order of completion ends up as:
      // [Weight 2] Non-Composable functions that are being promoted above Composables
      // [Weight 1] Composable functions
      // [Weight 0] Anything else
      if (element.isComposableFunction()) return 1
      if (element.isPromotedInStatement()) return 2
      return 0
    }

    if (location.completionParameters.isForValueArgument()) {
      // For arguments, ensure that the order of completion ends up as:
      // [Weight 2] Non-Composable functions that are being promoted
      // [Weight 1] Non-Composable functions / Anything else (default case)
      // [Weight 0] Composable functions
      if (element.isComposableFunction()) return 0
      if (element.isPromotedInArgument()) return 2
      return 1
    }

    return 0
  }
}

/** Set of fully-qualified names of non-Composable functions that should be promoted above standard Composables in a statement. */
private val PROMOTED_NON_COMPOSABLES_IN_STATEMENTS = setOf(
  "androidx.compose.material.MaterialTheme",
  "androidx.compose.material3.MaterialTheme",
)

/** Set of fully-qualified names of non-Composable functions that should be promoted in a value argument. */
private val PROMOTED_NON_COMPOSABLES_IN_ARGUMENTS = setOf(
  "androidx.compose.material.MaterialTheme",
  "androidx.compose.material3.MaterialTheme",
  "androidx.compose.material.icons.Icons.Default",
  "androidx.compose.material.icons.Icons.Filled",
  "androidx.compose.material.icons.Icons.Outlined",
  "androidx.compose.material.icons.Icons.Rounded",
  "androidx.compose.material.icons.Icons.Sharp",
  "androidx.compose.material.icons.Icons.TwoTone",
)

private fun LookupElement.isPromotedInStatement(): Boolean {
  val fqName = (psiElement as? KtNamedDeclaration)?.fqName?.asString()
  return fqName != null && PROMOTED_NON_COMPOSABLES_IN_STATEMENTS.contains(fqName)
}

private fun LookupElement.isPromotedInArgument(): Boolean {
  val fqName = (psiElement as? KtNamedDeclaration)?.fqName?.asString()
  return fqName != null && PROMOTED_NON_COMPOSABLES_IN_ARGUMENTS.contains(fqName)
}

/** Checks if the proposed completion would insert a composable function. */
private fun LookupElement.isComposableFunction() = psiElement?.isComposableFunction() == true

/** Checks if the proposed completion would insert a named argument. */
private fun LookupElement.isNamedArgumentCompletion() = lookupString.endsWith(" =")

/** Checks if this completion is for a statement (where Compose views are usually called) and not part of another expression. */
private fun CompletionParameters.isForStatement() =
  position is LeafPsiElement && position.node.elementType == KtTokens.IDENTIFIER && position.parent?.parent is KtBlockExpression

/** Checks if this completion is for a value argument, where Compose views are usually not called. */
private fun CompletionParameters.isForValueArgument() =
  position is LeafPsiElement && position.node.elementType == KtTokens.IDENTIFIER && position.parentOfType<KtValueArgument>() != null

/** Checks if the given completions parameters are in a Kotlin file in a Compose-enabled module. */
private fun CompletionParameters.isInComposeEnabledModuleAndFile() =
  position.language == KotlinLanguage.INSTANCE && position.getModuleSystem()?.usesCompose == true
