/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.statelist;

import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.ThemeMenuAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.actions.DrawableBackgroundMenuAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

public final class StateListActionGroups extends ToolbarActionGroups {
  public StateListActionGroups(@NotNull DesignSurface<?> surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getNorthGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    // TODO(b/136258816): Update to support multi-model
    group.add(new ThemeMenuAction(mySurface::getConfiguration));
    group.add(new DrawableBackgroundMenuAction());
    return group;
  }

  @Override
  protected @NotNull ActionGroup getNorthEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new SelectorMenuAction());
    return group;
  }
}
