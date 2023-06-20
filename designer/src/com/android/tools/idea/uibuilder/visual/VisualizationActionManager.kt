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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.uibuilder.editor.NlActionManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintHighlightingIssue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class VisualizationActionManager(surface: NlDesignSurface,
                                 private val visualizationModelsProvider: () -> VisualizationModelsProvider) : NlActionManager(surface) {
  private val zoomInAction: AnAction = ZoomInAction.getInstance()
  private val zoomOutAction: AnAction = ZoomOutAction.getInstance()
  private val zoomToFitAction: AnAction = ZoomToFitAction.getInstance()

  override fun registerActionsShortcuts(component: JComponent) = Unit

  override fun getPopupMenuActions(leafComponent: NlComponent?): DefaultActionGroup {
    val group = DefaultActionGroup()
    group.add(zoomInAction)
    group.add(zoomOutAction)
    group.add(zoomToFitAction)
    return group
  }

  override fun getToolbarActions(newSelection: List<NlComponent>) = DefaultActionGroup()

  override fun getSceneViewContextToolbar(sceneView: SceneView): JComponent? {
    val model = sceneView.scene.sceneManager.model
    val visualizationModel = visualizationModelsProvider() as? CustomModelsProvider ?: return null
    return if (sceneView.scene.sceneManager.model.dataContext.getData(IS_CUSTOM_MODEL) == true) {
      JPanel(BorderLayout()).apply {
        // For now, we just display a mock toolbar. This will be replaced in the future with SceneView the toolbar.
        add(CommonButton(StudioIcons.Common.CLOSE).apply {
          verticalAlignment = JLabel.CENTER
          isRolloverEnabled = true

          addActionListener {
            visualizationModel.removeCustomConfigurationAttributes(model)
          }
        }, BorderLayout.LINE_END)
      }
    }
    else {
      null
    }
  }

  override fun getSceneViewRightBar(sceneView: SceneView): JComponent {
    return object: JBLabel(ColoredIconGenerator.generateColoredIcon(StudioIcons.Common.WARNING_INLINE, JBColor.background())) {
      init {
        isOpaque = true
        background = Color.ORANGE
      }

      override fun isVisible() = sceneView.visualLintWarning() != null
    }
  }
}

fun SceneView.visualLintWarning(): Issue? {
  val issue = IssuePanelService.getInstance(surface.project).getSelectedIssues()
    .filterIsInstance<VisualLintHighlightingIssue>().firstOrNull { it.shouldHighlight(sceneManager.model) }
  return issue as? Issue
}