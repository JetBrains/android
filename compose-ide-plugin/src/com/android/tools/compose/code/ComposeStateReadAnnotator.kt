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
package com.android.tools.compose.code

import com.android.tools.compose.hasComposableAnnotation
import com.android.tools.idea.flags.StudioFlags
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.descendants
import com.intellij.psi.util.parentOfType
import icons.StudioIcons
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.resolveMainReference
import org.jetbrains.kotlin.idea.structuralsearch.resolveExprType
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

const val FQNAME = "androidx.compose.runtime.State"

/**
 * Annotator that highlights reads of `androidx.compose.runtime.State` variables inside `@Composable` functions.
 *
 * TODO(b/225218822): Before productionizing this, depending on whether we want a gutter icon, highlighting, or both,
 *  we must change this to use `KotlinHighlightingVisitorExtension` (to avoid race conditions), or use a
 *  `RelatedItemLineMarkerProvider` for the gutter icon so it can be disabled with a setting. If we do both, we will
 *  need to share the logic and store result on the `PsiElement` to avoid computing it twice.
 */
class ComposeStateReadAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!StudioFlags.COMPOSE_STATE_READ_HIGHLIGHTING_ENABLED.get()) return
    if (element !is KtNameReferenceExpression) return
    val scopeName = element.parentOfType<KtNamedFunction>()?.takeIf { it.hasComposableAnnotation() }?.name ?: return
    element.getStateReadElement()?.let {
      holder.newAnnotation(HighlightSeverity.INFORMATION, createMessage(it.text, scopeName))
        .textAttributes(COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY)
        .gutterIconRenderer(ComposeStateReadGutterIconRenderer(it.text, scopeName))
        .create()
    }
  }

  private fun KtNameReferenceExpression.getStateReadElement(): PsiElement? {
    if (isAssignee()) return null
    if (isImplicitStateRead()) return this
    return getExplicitStateReadElement()
  }

  /**
   * Returns the element representing the State variable being read, if any.
   *
   * e.g. for `foo.bar.baz.value`, will return the [PsiElement] for `baz`.
   */
  private fun KtNameReferenceExpression.getExplicitStateReadElement(): PsiElement? {
    if (text != "value") return null
    return (parent as? KtDotQualifiedExpression)
      ?.takeIf { it.selectorExpression == this }
      ?.receiverExpression
      ?.takeIf { it.resolveExprType()?.isStateType() == true }
      ?.let {
        when (it) {
          is KtDotQualifiedExpression -> it.selectorExpression
          else -> it
        }
      }
  }

  /**
   * Returns whether the expression represents an implicit call to `State#getValue`, i.e. if the expression
   * is for a delegated property where the delegate is of type `State`.
   *
   * E.g. for a name reference expression `foo` if `foo` is defined as:
   *
   * `val foo by stateOf(...)`
   */
  private fun KtNameReferenceExpression.isImplicitStateRead(): Boolean {
    return analyze(this) {
      (resolveMainReference() as? KtProperty)?.delegateExpression?.resolveExprType()?.isStateType() ?: false
    }
  }

  private fun KotlinType.isStateType() =
    (fqName?.asString() == FQNAME || supertypes().any { it.fqName?.asString() == FQNAME })

  private fun KtNameReferenceExpression.isAssignee(): Boolean {
    return parentOfType<KtBinaryExpression>()
             ?.takeIf { it.operationToken.toString() == "EQ" }
             ?.let { it.left == this || it.left?.descendants()?.contains(this) == true }
             ?: false
  }

  private data class ComposeStateReadGutterIconRenderer(private val stateName: String,
                                                        private val functionName: String) : GutterIconRenderer() {
    override fun getIcon() = StudioIcons.Common.INFO
    override fun getTooltipText() = createMessage(stateName, functionName)
  }

  companion object {
    const val COMPOSE_STATE_READ_TEXT_ATTRIBUTES_NAME = "ComposeStateReadTextAttributes"
    val COMPOSE_STATE_READ_TEXT_ATTRIBUTES_KEY: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey(COMPOSE_STATE_READ_TEXT_ATTRIBUTES_NAME, DefaultLanguageHighlighterColors.FUNCTION_CALL)

    private fun createMessage(stateVariable: String, composable: String) =
      "State read: when the value of \"$stateVariable\" changes, \"$composable\" will recompose."
  }
}
