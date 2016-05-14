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
package com.android.tools.idea.configurations;

import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurface.ScreenMode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import icons.AndroidIcons;

/**
 * Mode for toggling design mode
 */
public class DesignModeAction extends AnAction {
  private final DesignSurface mySurface;

  public DesignModeAction(DesignSurface surface) {
    super(null, "Show Design", AndroidIcons.NeleIcons.DesignView);
    mySurface = surface;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    // If we're already in design mode, go to both-mode, otherwise go to design only
    ScreenMode mode;
    switch (mySurface.getScreenMode()) {
      case SCREEN_ONLY:
        mode = ScreenMode.BOTH;
        break;
      case BLUEPRINT_ONLY:
      case BOTH:
      default:
        mode = ScreenMode.SCREEN_ONLY;
        break;
    }
    mySurface.setScreenMode(mode, true);
  }
}
