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
import com.android.tools.idea.common.actions.ZoomInAction;
import com.android.tools.idea.common.actions.ZoomLabelAction;
import com.android.tools.idea.common.actions.ZoomOutAction;
import com.android.tools.idea.common.actions.ZoomToFitAction;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.ZoomShortcut;
import com.android.tools.idea.naveditor.actions.AutoArrangeAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
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
    group.add(new AutoArrangeAction(mySurface));
    group.addSeparator();

    group.add(ZoomShortcut.ZOOM_OUT.registerForAction(new ZoomOutAction(mySurface), mySurface, this));
    group.add(new ZoomLabelAction(mySurface));
    group.add(ZoomShortcut.ZOOM_IN.registerForAction(new ZoomInAction(mySurface), mySurface, this));
    group.add(ZoomShortcut.ZOOM_FIT.registerForAction(new ZoomToFitAction(mySurface), mySurface, this));
    group.addSeparator();
    group.add(TOGGLE_ISSUE_PANEL.registerForAction(new IssueNotificationAction(mySurface), mySurface, this));
    return group;
  }
}
