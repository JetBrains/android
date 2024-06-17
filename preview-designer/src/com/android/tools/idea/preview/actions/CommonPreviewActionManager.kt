/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import javax.swing.JComponent

/** Common preview [ActionManager] for the [DesignSurface]. */
class CommonPreviewActionManager(
  surface: DesignSurface<LayoutlibSceneManager>,
  supportAnimationPreview: Boolean = true,
  supportInteractivePreview: Boolean = true,
) : ActionManager<DesignSurface<LayoutlibSceneManager>>(surface) {
  private val animationPreviewAction =
    if (supportAnimationPreview) AnimationInspectorAction() else null
  private val interactivePreviewAction =
    if (supportInteractivePreview) EnableInteractiveAction() else null

  override fun registerActionsShortcuts(component: JComponent) {}

  override fun getPopupMenuActions(leafComponent: NlComponent?) = DefaultActionGroup()

  override fun getToolbarActions(selection: MutableList<NlComponent>) = DefaultActionGroup()

  override fun getSceneViewStatusIconAction(): AnAction = PreviewStatusIcon()

  override fun getSceneViewContextToolbarActions(): List<AnAction> =
    listOf(Separator()) +
      listOfNotNull(animationPreviewAction, interactivePreviewAction)
        .disabledIfRefreshingOrHasErrorsOrProjectNeedsBuild()
        .hideIfRenderErrors()
        .visibleOnlyInStaticPreview()
}
