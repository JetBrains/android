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
import com.android.tools.idea.compose.pickers.PsiPickerManager
import com.android.tools.idea.compose.pickers.preview.model.PreviewPickerPropertiesModel
import com.android.tools.idea.compose.pickers.preview.tracking.PreviewPickerTracker
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.toSmartPsiPointer
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.NavigateAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.ui.awt.RelativePoint
import javax.swing.Icon
import org.jetbrains.kotlin.idea.util.CommentSaver.Companion.tokenType
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

/**
 * LineMarkerProvider for the @Preview annotation for Compose.
 *
 * Returns a [LineMarkerInfo] that brings up a properties panel to edit the annotation.
 */
class PreviewPickerLineMarkerProvider : LineMarkerProviderDescriptor() {
  private val log = Logger.getInstance(this.javaClass)

  override fun getName(): String = message("picker.preview.annotator.name")

  override fun getIcon(): Icon = AllIcons.Actions.InlayGear

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (element !is LeafPsiElement) return null
    if (element.tokenType != KtTokens.IDENTIFIER) return null
    if (!element.isValid) return null
    if (element.getModuleSystem()?.isPreviewPickerEnabled() != true) return null
    if (element.text != COMPOSE_PREVIEW_ANNOTATION_NAME) return null

    val annotationEntry = element.parentOfType<KtAnnotationEntry>() ?: return null
    val uElement =
      (annotationEntry.toUElement() as? UAnnotation)?.takeIf { it.isPreviewAnnotation() }
        ?: return null

    // Do not show the picker if there are any syntax issues with the annotation
    if (PreviewAnnotationCheck.checkPreviewAnnotationIfNeeded(annotationEntry).hasIssues)
      return null

    val previewElementDefinitionPsi = uElement.toSmartPsiPointer()
    val module =
      element.module
        ?: run {
          log.warn("Couldn't obtain current module")
          return null
        }
    val info =
      createInfo(element, element.textRange, element.project, module, previewElementDefinitionPsi)
    NavigateAction.setNavigateAction(
      info,
      message("picker.preview.annotator.action.title"),
      null,
      icon
    )
    return info
  }

  /**
   * Creates a [LineMarkerInfo] that when clicked/selected, opens the Properties panel for the
   * @Preview annotation, this [LineMarkerInfo] should be available for the entire annotation entry,
   * including parameters. I.e: Invoking the [LineMarkerInfo] from a parameter should also show the
   * @Preview picker option.
   */
  private fun createInfo(
    element: PsiElement,
    textRange: TextRange,
    project: Project,
    module: Module,
    previewElementDefinitionPsi: SmartPsiElementPointer<PsiElement>?
  ): LineMarkerInfo<PsiElement> {
    // Make sure there's a configuration available
    ConfigurationManager.getOrCreateInstance(module)
    return LineMarkerInfo<PsiElement>(
      element,
      textRange,
      AllIcons.Actions.InlayGear,
      { message("picker.preview.annotator.tooltip") },
      { mouseEvent, _ ->
        val model =
          PreviewPickerPropertiesModel.fromPreviewElement(
            project,
            module,
            previewElementDefinitionPsi,
            PreviewPickerTracker()
          )
        PsiPickerManager.show(
          location = RelativePoint(mouseEvent.component, mouseEvent.point).screenPoint,
          displayTitle = message("picker.preview.title"),
          model = model
        )
      },
      GutterIconRenderer.Alignment.LEFT,
      { message("picker.preview.annotator.tooltip") }
    )
  }
}
