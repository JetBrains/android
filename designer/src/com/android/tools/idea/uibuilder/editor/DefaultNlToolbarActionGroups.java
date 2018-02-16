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
import com.android.tools.idea.common.actions.*;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.surface.ZoomType;
import com.android.tools.idea.configurations.*;
import com.android.tools.idea.rendering.RefreshRenderAction;
import com.android.tools.idea.uibuilder.actions.SwitchDesignModeAction;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.common.surface.DesignSurfaceShortcut.*;

public final class DefaultNlToolbarActionGroups extends ToolbarActionGroups {

  public DefaultNlToolbarActionGroups(@NotNull NlDesignSurface surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getNorthGroup() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(DESIGN_MODE.registerForAction(createDesignModeAction(),
                                            new SwitchDesignModeAction((NlDesignSurface)mySurface), mySurface));
    group.addSeparator();

    group.add(SWITCH_ORIENTATION.registerForAction(new OrientationMenuAction(mySurface::getConfiguration, mySurface),
                                                   new ToggleDeviceOrientationAction(mySurface), mySurface));
    group.addSeparator();
    group.add(NEXT_DEVICE.registerForAction(new DeviceMenuAction(mySurface::getConfiguration),
                                            new SwitchDeviceAction(mySurface), mySurface));
    group.add(new TargetMenuAction(mySurface::getConfiguration));
    group.add(new ThemeMenuAction(mySurface::getConfiguration));

    group.addSeparator();

    group.add(new LocaleMenuAction(mySurface::getConfiguration));
    return group;
  }

  @NotNull
  private DropDownAction createDesignModeAction() {
    DropDownAction designSurfaceMenu = new DropDownAction(
      "", "Select Design Surface",
      StudioIcons.LayoutEditor.Toolbar.VIEW_MODE);
    designSurfaceMenu.addAction(new DesignModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addAction(new BlueprintModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addAction(new BlueprintAndDesignModeAction((NlDesignSurface)mySurface));
    designSurfaceMenu.addSeparator();
    designSurfaceMenu.addAction(REFRESH_LAYOUT.registerForAction(new RefreshRenderAction(mySurface), mySurface));
    return designSurfaceMenu;
  }

  @NotNull
  @Override
  protected ActionGroup getNorthEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(ZOOM_OUT.registerForAction(new SetZoomAction(mySurface, ZoomType.OUT), mySurface));
    group.add(new ZoomLabelAction(mySurface));
    group.add(ZOOM_IN.registerForAction(new SetZoomAction(mySurface, ZoomType.IN), mySurface));
    group.add(ZOOM_FIT.registerForAction(new SetZoomAction(mySurface, ZoomType.FIT), mySurface));
    group.addSeparator();
    group.add(TOGGLE_ISSUE_PANEL.registerForAction(new IssueNotificationAction(mySurface), mySurface));

    return group;
  }
}
