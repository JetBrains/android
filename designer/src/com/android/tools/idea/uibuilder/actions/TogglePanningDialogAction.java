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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.handlers.constraint.WidgetNavigatorPanel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.ui.popup.JBPopup;
import icons.AndroidIcons;

import java.util.Locale;

/**
 * Display the Pan and Inspect Dialog
 */
public class TogglePanningDialogAction extends ToggleAction {

  private final DesignSurface mySurface;
  JBPopup myPopup;

  public TogglePanningDialogAction(DesignSurface surface) {
    mySurface = surface;
    String title = String.format(Locale.US, "%s %s",WidgetNavigatorPanel.TITLE, WidgetNavigatorPanel.HINT);
    getTemplatePresentation().setIcon(AndroidIcons.NeleIcons.Pan);
    getTemplatePresentation().setDescription(title);
    getTemplatePresentation().setText(title);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myPopup != null && myPopup.isVisible();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if(state) {
      myPopup = WidgetNavigatorPanel.createPopup(mySurface);
    } else {
      if (myPopup != null) {
        myPopup.cancel();
        myPopup = null;
      }
    }
  }
}
