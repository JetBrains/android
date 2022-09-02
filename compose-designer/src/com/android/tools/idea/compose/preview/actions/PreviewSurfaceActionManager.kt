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
package com.android.tools.idea.compose.preview.actions

import com.android.flags.ifEnabled
import com.android.tools.idea.common.actions.CopyResultImageAction
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/** [ActionManager] to be used by the Compose Preview. */
internal class PreviewSurfaceActionManager(
  private val surface: DesignSurface<LayoutlibSceneManager>
) : ActionManager<DesignSurface<LayoutlibSceneManager>>(surface) {
  private val copyResultImageAction =
    CopyResultImageAction(
      {
        // Copy the model of the current selected object (if any)
        surface.selectionModel.primary?.model?.let {
          return@CopyResultImageAction surface.getSceneManager(it) as LayoutlibSceneManager
        }

        surface.sceneViewAtMousePosition?.sceneManager as? LayoutlibSceneManager
      },
      message("copy.result.image.action.title"),
      message("copy.result.image.action.done.text")
    )

  override fun registerActionsShortcuts(component: JComponent) {
    registerAction(copyResultImageAction, IdeActions.ACTION_COPY, component)
  }

  override fun getPopupMenuActions(leafComponent: NlComponent?): DefaultActionGroup =
    DefaultActionGroup().apply { add(copyResultImageAction) }

  override fun getToolbarActions(selection: MutableList<NlComponent>): DefaultActionGroup =
    DefaultActionGroup()

  override fun getSceneViewContextToolbar(sceneView: SceneView): JComponent =
    ActionManagerEx.getInstanceEx()
      .createActionToolbar(
        "sceneView",
        DefaultActionGroup(
          listOf(Separator()) +
            listOfNotNull(
                StudioFlags.COMPOSE_PIN_PREVIEW.ifEnabled {
                  StudioFlags.COMPOSE_INDIVIDUAL_PIN_PREVIEW.ifEnabled {
                    PinPreviewElementAction { sceneView.scene.sceneManager.model.dataContext }
                  }
                },
                AnimationInspectorAction { sceneView.scene.sceneManager.model.dataContext },
                EnableInteractiveAction { sceneView.scene.sceneManager.model.dataContext },
                DeployToDeviceAction { sceneView.scene.sceneManager.model.dataContext },
              )
              .disabledIfRefreshingOrRenderErrors(sceneView)
              .visibleOnlyInComposeStaticPreview()
        ),
        true,
        false
      )
      .apply {
        // Do not allocate space for the "see more" chevron if not needed
        setReservePlaceAutoPopupIcon(false)
        setShowSeparatorTitles(true)
        targetComponent = sceneView.surface
      }
      .component
      .apply {
        isOpaque = false
        border = JBUI.Borders.empty()
      }

  override fun getSceneViewStatusIcon(sceneView: SceneView): JComponent {
    val component =
      ActionManagerEx.getInstanceEx()
        .createActionToolbar(
          "sceneView",
          DefaultActionGroup(
            ComposePreviewStatusIconAction(sceneView).visibleOnlyInComposeStaticPreview()
          ),
          true,
          false
        )
        .apply {
          targetComponent = sceneView.surface
          (this as? ActionToolbarImpl)?.setForceMinimumSize(true)
        }
        .component
        .apply {
          isOpaque = false
          border = JBUI.Borders.empty()
        }

    return JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty()
      isOpaque = false
      isVisible = true
      add(component, BorderLayout.LINE_END)

      // Make the size to be fixed, even when the no icon is visible
      minimumSize = component.minimumSize
      preferredSize = component.minimumSize
    }
  }
}
