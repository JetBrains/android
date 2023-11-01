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
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.uibuilder.editor.NlActionManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintHighlightingIssue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import java.awt.Color
import javax.swing.BoxLayout
import javax.swing.BoxLayout.Y_AXIS
import javax.swing.JComponent
import javax.swing.JPanel

class VisualizationActionManager(
  surface: NlDesignSurface,
  private val visualizationModelsProvider: () -> VisualizationModelsProvider
) : NlActionManager(surface) {
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

  override fun getSceneViewContextToolbarActions(): List<AnAction> {
    return listOf(RemoveCustomAction(visualizationModelsProvider))
  }

  override fun getSceneViewRightBar(sceneView: SceneView): JComponent {
    val warningIcon =
      object :
        JBLabel(
          ColoredIconGenerator.generateColoredIcon(
            StudioIcons.Common.WARNING_INLINE,
            JBColor.background(),
          ),
        ) {
        private val issueListener =
          object : IssueListener {
            override fun onIssueSelected(issue: Issue?) {
              isVisible =
                (issue as? VisualLintHighlightingIssue)?.shouldHighlight(
                  sceneView.sceneManager.model,
                ) ?: false
            }
          }

        init {
          isOpaque = true
          background = Color.ORANGE
          isVisible = false
          sceneView.surface.addIssueListener(issueListener)
          Disposer.register(sceneView) { sceneView.surface.removeIssueListener(issueListener) }
        }

        override fun isVisible(): Boolean {
          return super.isVisible() &&
            IssuePanelService.getInstance(sceneView.surface.project).isIssuePanelVisible()
        }
      }
    return JPanel().apply {
      layout = BoxLayout(this, Y_AXIS)
      isOpaque = false
      add(warningIcon)
    }
  }

  /** Action to delete a custom preview */
  private class RemoveCustomAction(
    private val visualizationModelsProvider: () -> VisualizationModelsProvider
  ) : AnAction(StudioIcons.Common.CLOSE) {
    override fun actionPerformed(e: AnActionEvent) {
      val visualizationModel = visualizationModelsProvider() as? CustomModelsProvider ?: return
      val model =
        (e.dataContext.getData(CONTEXT_COMPONENT) as? SceneViewPeerPanel)
          ?.sceneView
          ?.sceneManager
          ?.model ?: return
      visualizationModel.removeCustomConfigurationAttributes(model)
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isVisible = e.dataContext.getData(IS_CUSTOM_MODEL) == true
    }
  }
}
