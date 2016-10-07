/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.monitor.memory.actions;

import com.android.tools.adtui.TimelineComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

public class ToggleDebugRender extends ToggleAction {
  private final TimelineComponent myTimelineComponent;

  public ToggleDebugRender(TimelineComponent timelineComponent) {
    super("Enable debug renderer", "Enables debug rendering", AllIcons.General.Debug);
    myTimelineComponent = timelineComponent;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myTimelineComponent.isDrawDebugInfo();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myTimelineComponent.setDrawDebugInfo(state);
  }
}
