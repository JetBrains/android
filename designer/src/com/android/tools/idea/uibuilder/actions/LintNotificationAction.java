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

import com.android.tools.idea.uibuilder.lint.LintAnnotationsModel;
import com.android.tools.idea.uibuilder.lint.LintNotificationPanel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Action which shows the current number of warnings in the layout
 * and when clicked, shows them
 */
public class LintNotificationAction extends AnAction {
  private final DesignSurface mySurface;
  private int myCount;

  public LintNotificationAction(@NotNull DesignSurface surface) {
    mySurface = surface;
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView == null) {
      presentation.setVisible(false);
      return;
    }
    LintAnnotationsModel lintModel = screenView.getModel().getLintAnnotationsModel();
    if (lintModel == null) {
      presentation.setVisible(false);
      return;
    }

    int markerCount = lintModel.getIssueCount();
    if (markerCount == 0) {
      presentation.setVisible(false);
    } else {
      presentation.setVisible(true);

      if (markerCount != myCount) {
        myCount = markerCount;
        Icon icon;
        switch (markerCount) {
          case 1: icon = AndroidIcons.LintNotification.Lint1; break;
          case 2: icon = AndroidIcons.LintNotification.Lint2; break;
          case 3: icon = AndroidIcons.LintNotification.Lint3; break;
          case 4: icon = AndroidIcons.LintNotification.Lint4; break;
          case 5: icon = AndroidIcons.LintNotification.Lint5; break;
          case 6: icon = AndroidIcons.LintNotification.Lint6; break;
          case 7: icon = AndroidIcons.LintNotification.Lint7; break;
          case 8: icon = AndroidIcons.LintNotification.Lint8; break;
          case 9: icon = AndroidIcons.LintNotification.Lint9; break;
          default: icon = AndroidIcons.LintNotification.Lint9plus; break;
        }
        presentation.setIcon(icon);
      }
    }
  }

  /** Shows list of warnings/errors */
  @Override
  public void actionPerformed(AnActionEvent e) {
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView != null) {
      LintAnnotationsModel lintModel = screenView.getModel().getLintAnnotationsModel();
      if (lintModel != null) {
        new LintNotificationPanel(screenView, lintModel).show(e);
      }
    }
  }
}
