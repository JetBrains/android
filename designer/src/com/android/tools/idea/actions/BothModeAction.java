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
package com.android.tools.idea.actions;

import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface.ScreenMode;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import icons.AndroidIcons;

/**
 * Mode for switching to both blueprint and design mode at the same time
 */
public class BothModeAction extends AnAction {
  private final NlDesignSurface mySurface;

  public BothModeAction(NlDesignSurface surface) {
    super(null, "Show Design + Blueprint", AndroidIcons.NeleIcons.BothMode);
    mySurface = surface;
  }

  @Override
  public void update(AnActionEvent event) {
    ScreenView screenView = mySurface.getCurrentSceneView();

    if (screenView != null) {
      event.getPresentation().setEnabled(screenView.getModel().getType().isLayout());
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    mySurface.setScreenMode(ScreenMode.BOTH, true);
  }
}
