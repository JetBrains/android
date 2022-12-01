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
package com.android.tools.idea.uibuilder.drawable

import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.configurations.ThemeMenuAction
import com.android.tools.idea.uibuilder.actions.DrawableBackgroundMenuAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup

/**
 * The default [ToolbarActionGroups] for Drawable files.
 */
class DrawableActionGroups(surface: DesignSurface<*>) : ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup {
    // TODO(b/136258816): Update to support multi-model
    return DefaultActionGroup().apply {
      add(ThemeMenuAction(mySurface::getConfiguration))
      add(DrawableBackgroundMenuAction())
    }
  }
}
