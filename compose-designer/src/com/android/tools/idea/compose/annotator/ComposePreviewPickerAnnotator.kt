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

import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.pickers.PsiPickerManager
import com.android.tools.idea.compose.preview.pickers.properties.PsiCallPropertyModel
import com.android.tools.idea.compose.preview.toPreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement
import java.awt.MouseInfo
import javax.swing.Icon

/**
 * Annotator for the @Preview annotation for Compose.
 *
 * Returns a [GutterIconRenderer] that brings up a properties panel to edit the annotation.
 */
class ComposePreviewPickerAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!StudioFlags.COMPOSE_EDITOR_SUPPORT.get()) return
    if (!StudioFlags.COMPOSE_PREVIEW_ELEMENT_PICKER.get()) return
    if (element.getModuleSystem()?.usesCompose != true) return

    if (element is KtAnnotationEntry) {
      val uElement = (element.toUElement() as? UAnnotation) ?: return

      if (uElement.isPreviewAnnotation()) {
        uElement.toPreviewElement()?.let {
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION).gutterIconRenderer(
            ComposePreviewPickerRenderer(element.project, it)).create()
        }
      }
    }
  }
}

/**
 * [GutterIconRenderer] for the @Preview annotator.
 */
private class ComposePreviewPickerRenderer(private val project: Project,
                                           private val previewElement: PreviewElement) : GutterIconRenderer() {
  private val psiPickerAction = object : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val model = PsiCallPropertyModel.fromPreviewElement(project, previewElement)
      PsiPickerManager.show(MouseInfo.getPointerInfo().location, model)
    }
  }

  override fun getIcon(): Icon = AllIcons.Actions.InlayGear

  override fun getClickAction() = psiPickerAction

  override fun getTooltipText() = message("picker.preview.tooltip")

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ComposePreviewPickerRenderer

    if (project != other.project) return false
    if (previewElement != other.previewElement) return false

    return true
  }

  override fun hashCode(): Int {
    var result = project.hashCode()
    result = 31 * result + previewElement.hashCode()
    return result
  }
}