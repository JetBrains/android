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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.actions.BlueprintAndDesignModeAction;
import com.android.tools.idea.actions.BlueprintModeAction;
import com.android.tools.idea.actions.DesignModeAction;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.configurations.*;
import com.android.tools.idea.rendering.RefreshRenderAction;
import com.android.tools.idea.common.actions.IssueNotificationAction;
import com.android.tools.idea.common.actions.SetZoomAction;
import com.android.tools.idea.uibuilder.actions.TogglePanningDialogAction;
import com.android.tools.idea.common.actions.ZoomLabelAction;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.common.surface.ZoomType;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

public final class DefaultNlToolbarActionGroups extends ToolbarActionGroups {

  public DefaultNlToolbarActionGroups(@NotNull NlDesignSurface surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getNorthGroup() {
    DefaultActionGroup group = new DefaultActionGroup();

    DropDownAction designSurfaceMenu = new DropDownAction("", "Select Design Surface",
                                                          StudioIcons.LayoutEditor.Toolbar.BLUEPRINT_MODE_INACTIVE);
    designSurfaceMenu.addAction(new DesignModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addAction(new BlueprintModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addAction(new BlueprintAndDesignModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addSeparator();
    designSurfaceMenu.addAction(new RefreshRenderAction(mySurface));
    group.add(designSurfaceMenu);

    group.addSeparator();

    group.add(new OrientationMenuAction(mySurface::getConfiguration, mySurface));
    group.addSeparator();

    group.add(new DeviceMenuAction(mySurface::getConfiguration));
    group.add(new TargetMenuAction(mySurface::getConfiguration));
    group.add(new ThemeMenuAction(mySurface::getConfiguration));

    group.addSeparator();

    group.add(new LocaleMenuAction(mySurface::getConfiguration));
    return group;
  }

  @NotNull
  @Override
  protected ActionGroup getEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new SetZoomAction(mySurface, ZoomType.OUT));
    group.add(new ZoomLabelAction(mySurface));
    group.add(new SetZoomAction(mySurface, ZoomType.IN));
    group.add(new SetZoomAction(mySurface, ZoomType.FIT));
    group.add(new TogglePanningDialogAction((NlDesignSurface)mySurface));
    group.addSeparator();

    group.add(new IssueNotificationAction(mySurface));
    return group;
  }
}
