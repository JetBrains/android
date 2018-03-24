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
package com.android.tools.idea.naveditor.editor;

import com.android.tools.idea.common.actions.*;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceShortcut;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.common.surface.DesignSurfaceShortcut.TOGGLE_ISSUE_PANEL;

/**
 * Toolbar actions for the navigation editor
 */
public class NavToolbarActionGroups extends ToolbarActionGroups {
  public NavToolbarActionGroups(@NotNull DesignSurface surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(DesignSurfaceShortcut.ZOOM_OUT.registerForAction(new ZoomOutAction(mySurface), mySurface));
    group.add(new ZoomLabelAction(mySurface));
    group.add(DesignSurfaceShortcut.ZOOM_IN.registerForAction(new ZoomInAction(mySurface), mySurface));
    group.add(DesignSurfaceShortcut.ZOOM_FIT.registerForAction(new ZoomToFitAction(mySurface), mySurface));
    group.addSeparator();
    group.add(TOGGLE_ISSUE_PANEL.registerForAction(new IssueNotificationAction(mySurface), mySurface));
    return group;
  }
}
