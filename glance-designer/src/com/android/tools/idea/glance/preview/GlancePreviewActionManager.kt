/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import javax.swing.JComponent

/**
 * Noop, glance specific [ActionManager] for the [DesignSurface].
 *
 * TODO(b/239802877): add toolbar functionality similar for the Compose one.
 */
internal class GlancePreviewActionManager(
  private val surface: DesignSurface<LayoutlibSceneManager>
) : ActionManager<DesignSurface<LayoutlibSceneManager>>(surface) {
  override fun registerActionsShortcuts(component: JComponent) {}

  override fun getPopupMenuActions(leafComponent: NlComponent?) = DefaultActionGroup()

  override fun getToolbarActions(selection: MutableList<NlComponent>) = DefaultActionGroup()
}
