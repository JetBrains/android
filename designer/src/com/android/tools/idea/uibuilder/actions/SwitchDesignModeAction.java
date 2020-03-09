/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.actions.DesignerActions;
import com.android.tools.idea.actions.DesignerDataKeys;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Switch between {@link NlDesignSurface}'s design modes
 */
public class SwitchDesignModeAction extends AnAction {

  private SwitchDesignModeAction() {
  }

  @NotNull
  public static SwitchDesignModeAction getInstance() {
    return (SwitchDesignModeAction) ActionManager.getInstance().getAction(DesignerActions.ACTION_SWITCH_DESIGN_MODE);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(NlActionManager.LAYOUT_EDITOR) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    NlDesignSurface surface = e.getRequiredData(NlActionManager.LAYOUT_EDITOR);
    SceneMode nextMode = surface.getSceneMode().next();
    surface.setScreenMode(nextMode, true);
  }
}
