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
package com.android.tools.idea.actions;

import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface.ScreenMode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

/**
 * Mode for toggling blueprint mode
 */
public class BlueprintAndDesignModeAction extends ToggleAction {
  private final NlDesignSurface mySurface;

  public BlueprintAndDesignModeAction(@NotNull NlDesignSurface surface) {
    super("Design + Blueprint", "Show both Design and Blueprint", null);
    mySurface = surface;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return mySurface.getScreenMode() == ScreenMode.BOTH;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    mySurface.setScreenMode(ScreenMode.BOTH, true);
  }
}
