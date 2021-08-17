/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.annotator

import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_NAME
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.pickers.PsiPickerManager
import com.android.tools.idea.compose.preview.pickers.properties.PsiCallPropertyModel
import com.android.tools.idea.compose.preview.toPreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.NavigateAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement
import javax.swing.Icon

/**
 * LineMarkerProvider for the @Preview annotation for Compose.
 *
 * Returns a [LineMarkerInfo] that brings up a properties panel to edit the annotation.
 */
class ComposePreviewPickerAnnotator : LineMarkerProviderDescriptor() {
  override fun getName(): String = message("picker.preview.annotator.name")

  override fun getIcon(): Icon = AllIcons.Actions.InlayGear

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (element !is LeafPsiElement) return null
    if (element.tokenType != KtTokens.IDENTIFIER) return null
    if (!StudioFlags.COMPOSE_EDITOR_SUPPORT.get()) return null
    if (!StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.get()) return null
    if (element.getModuleSystem()?.usesCompose != true) return null
    if (element.text != COMPOSE_PREVIEW_ANNOTATION_NAME) return null

    val parentElement = element.parentOfType<KtAnnotationEntry>() ?: return null
    val uElement = (parentElement.toUElement() as? UAnnotation) ?: return null

    if (uElement.isPreviewAnnotation()) {
      uElement.toPreviewElement()?.let {
        val info = createInfo(element, parentElement.textRange, parentElement.project, it)
        NavigateAction.setNavigateAction(info, message("picker.preview.annotator.action.title"), null, icon)
        return info
      }
    }
    return null
  }

  /**
   * Creates a [LineMarkerInfo] that when clicked/selected, opens the Properties panel for the @Preview annotation, this [LineMarkerInfo]
   * should be available for the entire annotation entry, including parameters. I.e: Invoking the [LineMarkerInfo] from a parameter should
   * also show the @Preview picker option.
   */
  private fun createInfo(
    element: PsiElement,
    textRange: TextRange,
    project: Project,
    previewElement: PreviewElement
  ) = LineMarkerInfo<PsiElement>(
    element,
    textRange,
    AllIcons.Actions.InlayGear,
    { message("picker.preview.annotator.tooltip") },
    { mouseEvent, _ ->
      val model = PsiCallPropertyModel.fromPreviewElement(project, previewElement)
      PsiPickerManager.show(RelativePoint(mouseEvent.component, mouseEvent.point).screenPoint, model)
    },
    GutterIconRenderer.Alignment.LEFT,
    { message("picker.preview.annotator.tooltip") }
  )
}