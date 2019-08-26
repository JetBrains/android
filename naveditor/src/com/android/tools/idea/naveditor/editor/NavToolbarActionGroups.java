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

import static com.android.tools.idea.common.surface.DesignSurfaceShortcut.TOGGLE_ISSUE_PANEL;

import com.android.tools.idea.common.actions.IssueNotificationAction;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import java.util.List;
import org.jetbrains.annotations.NotNull;

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
    addActionsWithSeparator(group, getZoomActionsWithShortcuts(mySurface, this));
    group.add(TOGGLE_ISSUE_PANEL.registerForAction(new IssueNotificationAction(mySurface), mySurface, this));
    return group;
  }
}
