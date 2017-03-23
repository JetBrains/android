/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.actions.BlueprintModeAction;
import com.android.tools.idea.actions.BothModeAction;
import com.android.tools.idea.actions.DesignModeAction;
import com.android.tools.idea.configurations.*;
import com.android.tools.idea.uibuilder.actions.LintNotificationAction;
import com.android.tools.idea.uibuilder.actions.SetZoomAction;
import com.android.tools.idea.uibuilder.actions.TogglePanningDialogAction;
import com.android.tools.idea.uibuilder.actions.ZoomLabelAction;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ActionsToolbar} with layout editor content.
 */
public class NlActionsToolbar extends ActionsToolbar {

  public NlActionsToolbar(@NotNull NlDesignSurface surface) {
    super(surface);
  }

  @Override
  protected DefaultActionGroup createConfigActions(ConfigurationHolder configurationHolder, DesignSurface designSurface) {
    assert designSurface instanceof NlDesignSurface;
    NlDesignSurface surface = (NlDesignSurface)designSurface;
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new DesignModeAction(surface));
    group.add(new BlueprintModeAction(surface));
    group.add(new BothModeAction(surface));
    group.addSeparator();

    OrientationMenuAction orientationAction = new OrientationMenuAction(configurationHolder);
    group.add(orientationAction);
    group.addSeparator();

    DeviceMenuAction deviceAction = new DeviceMenuAction(configurationHolder);
    group.add(deviceAction);

    TargetMenuAction targetMenuAction = new TargetMenuAction(configurationHolder);
    group.add(targetMenuAction);

    ThemeMenuAction themeAction = new ThemeMenuAction(configurationHolder);
    group.add(themeAction);

    group.addSeparator();
    LocaleMenuAction localeAction = new LocaleMenuAction(configurationHolder);
    group.add(localeAction);

    group.addSeparator();
    ConfigurationMenuAction configAction = new ConfigurationMenuAction(surface);
    group.add(configAction);

    return group;
  }

  @Override
  protected ActionGroup getRhsActions(DesignSurface designSurface) {
    assert designSurface instanceof NlDesignSurface;
    NlDesignSurface surface = (NlDesignSurface)designSurface;
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new SetZoomAction(surface, ZoomType.OUT));
    group.add(new ZoomLabelAction(surface));
    group.add(new SetZoomAction(surface, ZoomType.IN));
    group.add(new SetZoomAction(surface, ZoomType.FIT));
    group.add(new TogglePanningDialogAction(surface));
    group.addSeparator();
    group.add(new LintNotificationAction(surface));

    return group;
  }
}
