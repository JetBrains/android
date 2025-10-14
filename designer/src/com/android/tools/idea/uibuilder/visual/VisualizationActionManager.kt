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
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.uibuilder.editor.NlActionManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue
import com.google.common.primitives.Ints
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.icons.RgbImageFilterSupplier
import icons.StudioIcons
import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.image.RGBImageFilter
import java.util.function.Supplier
import javax.swing.BoxLayout
import javax.swing.BoxLayout.Y_AXIS
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.abs

private fun colorDistanceToOrange(color: Color): Int =
  Ints.max(
    abs(color.red - Color.ORANGE.red),
    abs(color.green - Color.ORANGE.green),
    abs(color.blue - Color.ORANGE.blue),
  )

/**
 * Generate an icon based on the [WARNING_INLINE] icon, where orange pixels are replaced by [color]
 * and all other pixels are made transparent
 */
fun getVisualizationWarningIcon(color: Color) =
  IconLoader.createLazy(
    object : Supplier<Icon> {
      private val cache = mutableMapOf<Int, Icon>()

      @Suppress("UnstableApiUsage")
      override fun get(): Icon =
        cache.getOrPut(color.rgb) {
          IconLoader.filterIcon(
            StudioIcons.Common.WARNING_INLINE,
            object : RgbImageFilterSupplier {
              override fun getFilter(): RGBImageFilter =
                object : RGBImageFilter() {
                  override fun filterRGB(x: Int, y: Int, rgb: Int) =
                    if (colorDistanceToOrange(Color(rgb, true)) > 50) 0
                    else (rgb or 0xffffff) and color.rgb
                }
            },
          )
        }
    }
  )

class VisualizationActionManager(
  surface: NlDesignSurface,
  private val visualizationModelsProvider: () -> VisualizationModelsProvider,
) : NlActionManager(surface) {
  private val zoomInAction: AnAction = ZoomInAction.getInstance()
  private val zoomOutAction: AnAction = ZoomOutAction.getInstance()
  private val zoomToFitAction: AnAction = ZoomToFitAction.getInstance()

  override fun registerActionsShortcuts(component: JComponent) = Unit

  override fun getPopupMenuActions(
    leafComponent: NlComponent?,
    mouseEvent: MouseEvent,
  ): DefaultActionGroup {
    val group = DefaultActionGroup()
    group.add(zoomInAction)
    group.add(zoomOutAction)
    group.add(zoomToFitAction)
    return group
  }

  override fun getToolbarActions(newSelection: List<NlComponent>) = DefaultActionGroup()

  override fun getSceneViewContextToolbarOverflowActions(): List<AnAction> {
    return listOf(RemoveCustomAction(visualizationModelsProvider))
  }

  override fun getSceneViewRightBar(sceneView: SceneView): JComponent {
    val warningIcon =
      object : JBLabel(getVisualizationWarningIcon(JBColor.background())) {
        private val issueListener = IssueListener { issue ->
          if (issue is VisualLintRenderIssue) {
            isVisible = issue.shouldHighlight(sceneView.sceneManager.model)
            toolTipText = issue.summary
          }
          else {
            isVisible = false
          }
        }

        init {
          isOpaque = true
          background = Color.ORANGE
          isVisible = false
          val success =
            Disposer.tryRegister(sceneView) { sceneView.surface.removeIssueListener(issueListener) }
          if (success) {
            sceneView.surface.addIssueListener(issueListener)
          }
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
  ) : AnAction("Remove from Configuration Set", null, StudioIcons.Common.CLOSE) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
