// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.common.actions;

import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * Switch between {@link NlDesignSurface}'s design modes
 */
public class SwitchDesignModeAction extends AnAction {

  private final NlDesignSurface mySurface;

  public SwitchDesignModeAction(NlDesignSurface surface) {
    mySurface = surface;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    SceneMode nextMode = mySurface.getSceneMode().next();
    mySurface.setScreenMode(nextMode, true);
  }
}
