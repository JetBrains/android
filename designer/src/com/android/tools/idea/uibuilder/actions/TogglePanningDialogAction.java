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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.surface.PanZoomPanel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.StudioIcons;

import java.util.Locale;

/**
 * Display the Pan and Inspect Dialog
 */
public class TogglePanningDialogAction extends ToggleAction {

  private final NlDesignSurface mySurface;

  public TogglePanningDialogAction(NlDesignSurface surface) {
    mySurface = surface;
    String title = String.format(Locale.US, "%s %s", PanZoomPanel.TITLE, PanZoomPanel.HINT);
    getTemplatePresentation().setIcon(StudioIcons.LayoutEditor.Toolbar.PAN_TOOL);
    getTemplatePresentation().setDescription(title);
    getTemplatePresentation().setText(title);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    PanZoomPanel popup = mySurface.getPanZoomPanel();
    return popup != null && popup.isVisible();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    mySurface.setPanZoomPanelVisible(state);
  }
}
