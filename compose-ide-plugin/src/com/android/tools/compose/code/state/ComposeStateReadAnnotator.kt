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
package com.android.tools.compose.code.state

import androidx.compose.compiler.plugins.kotlin.ComposeClassIds
import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.hasComposableAnnotation
import com.android.tools.compose.isComposableAnnotation
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import icons.StudioIcons
import org.jetbrains.kotlin.idea.base.plugin.isK2Plugin
import org.jetbrains.kotlin.idea.base.psi.hasInlineModifier
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

const val COMPOSE_STATE_READ_TEXT_ATTRIBUTES_NAME = "ComposeStateReadTextAttributes"

val COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY: TextAttributesKey =
  TextAttributesKey.createTextAttributesKey(
    COMPOSE_STATE_READ_TEXT_ATTRIBUTES_NAME,
    DefaultLanguageHighlighterColors.FUNCTION_CALL
  )

/**
 * Annotator that highlights reads of `androidx.compose.runtime.State` variables inside
 * `@Composable` functions.
 *
 * TODO(b/225218822): Before productionizing this, depending on whether we want a gutter icon,
 *   highlighting, or both, we must change this to use `KotlinHighlightingVisitorExtension` (to
 *   avoid race conditions), or use a `RelatedItemLineMarkerProvider` for the gutter icon so it can
 *   be disabled with a setting.
 */
class ComposeStateReadAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!StudioFlags.COMPOSE_STATE_READ_HIGHLIGHTING_ENABLED.get()) return
    if (element !is KtNameReferenceExpression) return
    val scopeName =
      when (val scope = element.composableScope()) {
        is KtParameter ->
          ComposeBundle.message("compose.state.read.recompose.target.enclosing.lambda")
        else -> scope?.name ?: return
      }
    element.getStateReadElement()?.let {
      holder
        .newAnnotation(HighlightSeverity.INFORMATION, createMessage(it.text, scopeName))
        .textAttributes(COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY)
        .gutterIconRenderer(ComposeStateReadGutterIconRenderer(it.text, scopeName))
        .create()
    }
  }

  private data class ComposeStateReadGutterIconRenderer(
    private val stateName: String,
    private val functionName: String
  ) : GutterIconRenderer() {
    override fun getIcon() = StudioIcons.Common.INFO

    override fun getTooltipText() = createMessage(stateName, functionName)
  }
}

private tailrec fun PsiElement.composableScope(): PsiNamedElement? {
  if (this !is KtElement) return null
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
        param.takeIf { it.typeReference?.hasComposableAnnotation() == true }
      }
    }
    else -> null
  }
}

private fun KtTypeReference.hasComposableAnnotation() =
  if (isK2Plugin()) {
    hasAnnotation(ComposeClassIds.Composable)
  } else {
    annotationEntries.any { it.isComposableAnnotation() }
  }

private fun KtValueArgument.toFunction(): KtFunction? =
  parentOfType<KtCallExpression>()?.calleeExpression?.mainReference?.resolve() as? KtFunction

private fun KtFunction.getParameterForArgument(argument: KtValueArgument): KtParameter? {
  // If it's a lambda argument, it's always the last one.
  if (argument is KtLambdaArgument) return valueParameters.last()

  // If it's a named argument, then we have to look it up in our parameter list.
  val argumentName = argument.getArgumentName()?.asName?.asString()
  if (argumentName != null) return valueParameters.first { it.name == argumentName }

  // Otherwise, it's a positional argument, so just take its current position.
  return (argument.parent as? KtValueArgumentList)
    ?.arguments
    ?.indexOf(argument)
    ?.let(valueParameters::getOrNull)
}
