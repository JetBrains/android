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
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration

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
  override fun weigh(element: LookupElement, location: CompletionLocation): Int = when {
    location.completionParameters.position.language != KotlinLanguage.INSTANCE -> 0
    location.completionParameters.position.getModuleSystem()?.usesCompose != true -> 0

    // Since Compose uses so many named arguments, promote them to the top. This is for a case where the user has typed something like
    // "Button(en<caret>)", and we want to promote the completion "enabled = Boolean".
    element.isNamedArgumentCompletion() -> 3
    location.completionParameters.isForStatement() -> {
      val isConflictingName = COMPOSABLE_CONFLICTING_NAMES.contains((element.psiElement as? KtNamedDeclaration)?.fqName?.asString() ?: "")
      val isComposableFunction = element.psiElement?.isComposableFunction() ?: false
      // This method ensures that the order of completion ends up as:
      //
      // Composables with non-conflicting names (like Button {}) +2
      // Non Composables with conflicting names (like the MaterialTheme object) +2
      // Composable with conflicting names      (like MaterialTheme {}) +1
      // Anything else 0
      when {
        isComposableFunction && !isConflictingName -> 2
        !isComposableFunction && isConflictingName -> 2
        isComposableFunction && isConflictingName -> 1
        else -> 0
      }
    }
    else -> 0
  }
}

/** Set of Composable FQNs that have a conflicting name with a non-composable and where we want to promote the non-composable instead. */
private val COMPOSABLE_CONFLICTING_NAMES = setOf(
  "androidx.compose.material.MaterialTheme"
)

/** Checks if the proposed completion would insert a named argument. */
private fun LookupElement.isNamedArgumentCompletion() = lookupString.endsWith(" =")

/** Checks if this completion is for a statement (where Compose views usually called) and not part of another expression. */
private fun CompletionParameters.isForStatement(): Boolean {
  return position is LeafPsiElement &&
         position.node.elementType == KtTokens.IDENTIFIER &&
         position.parent?.parent is KtBlockExpression
}
