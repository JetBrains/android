/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.compose.meshgradient

import com.android.tools.idea.flags.StudioFlags
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.NavigateAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType
import icons.StudioIcons
import javax.swing.Icon
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

class MeshGradientLineMarkerProvider : LineMarkerProviderDescriptor() {

  override fun getName(): String = "Mesh Gradient Editor"

  override fun getIcon(): Icon = StudioIcons.GutterIcons.PREVIEW_SETTINGS

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (!StudioFlags.COMPOSE_MESH_GRADIENT_EDITOR.get()) return null
    if (element !is LeafPsiElement) return null
    if (element.tokenType != KtTokens.IDENTIFIER) return null
    if (!element.isValid) return null
    if (!element.isPhysical) return null

    val file = element.containingFile as? KtFile ?: return null
    val aliasName =
      file.importDirectives.firstOrNull { it.importedFqName?.asString() == "androidx.compose.ui.graphics.MeshGradientPainter" }?.aliasName
    val expectedName = aliasName ?: "MeshGradientPainter"
    if (element.text != expectedName) return null

    val callExpression = element.parentOfType<KtCallExpression>() ?: return null

    if (!callExpression.isValidMeshGradientCall()) return null

    val info = createInfo(element, element.textRange, element.project, callExpression)
    NavigateAction.setNavigateAction(info, "Edit Mesh Gradient", null, icon)
    return info
  }

  private fun createInfo(
    element: PsiElement,
    textRange: TextRange,
    project: Project,
    callExpression: KtCallExpression,
  ): LineMarkerInfo<PsiElement> {
    return object :
      LineMarkerInfo<PsiElement>(
        element,
        textRange,
        icon,
        { "Edit Mesh Gradient" },
        { _, _ ->
          val file = element.containingFile as? KtFile
          if (file != null) {
            val dialog = MeshGradientEditorDialog(project, file, callExpression)
            dialog.show()
          }
        },
        GutterIconRenderer.Alignment.LEFT,
        { "Edit Mesh Gradient" },
      ) {
      override fun getEditorFilter(): MarkupEditorFilter {
        return MarkupEditorFilterFactory.createIsNotDiffFilter()
      }
    }
  }
}
