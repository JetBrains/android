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
package com.android.tools.idea.uibuilder.adaptiveicon;

import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.ShapeMenuAction;
import com.android.tools.idea.configurations.ThemeMenuAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

public class AdaptiveIconActionGroups extends ToolbarActionGroups {
  public AdaptiveIconActionGroups(@NotNull DesignSurface<?> surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getNorthGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    NlModel model = mySurface.getModel();
    if (model != null) {
      group.add(new DensityMenuAction(model));
    }
    // TODO(b/136258816): Update to support multi-model
    group.add(new ShapeMenuAction(mySurface::getConfiguration));
    group.add(new ThemeMenuAction(mySurface::getConfiguration));
    return group;
  }
}