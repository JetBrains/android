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

import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.error.IssueModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.icons.AllIcons;
import com.intellij.notification.impl.ui.NotificationsUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.TextIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Action which shows the current number of warnings in the layout
 * and when clicked, shows them
 */
public class IssueNotificationAction extends AnAction {
  public static final String NO_ISSUE = "No Issue";
  public static final String SHOW_ISSUE = "Show Warnings and Errors";
  private static final String ERROR_MORE_THAN_9 = "9+";
  private static final String EMPTY_STRING = "";
  private final DesignSurface mySurface;
  private int myCount = -1;
  private LayeredIcon myIcon = new LayeredIcon(2);
  private TextIcon myTextIcon = new TextIcon("", JBColor.white, null, 2);

  public IssueNotificationAction(@NotNull DesignSurface surface) {
    super(NO_ISSUE, NO_ISSUE, AllIcons.Ide.Notification.NoEvents);
    mySurface = surface;
    myTextIcon.setFont(new Font(NotificationsUtil.getFontName(), Font.BOLD, JBUI.scale(9)));
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    IssueModel issueModel = mySurface.getIssueModel();
    int markerCount = issueModel.getIssueCount();

    updateIssueTypeIcon(issueModel);
    if (markerCount != myCount) {
      myCount = markerCount;
      updateCountIcon(markerCount);
      presentation.setText(markerCount == 0 ? NO_ISSUE : SHOW_ISSUE);
    }
    presentation.setIcon(myIcon);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    NlUsageTrackerManager.getInstance(mySurface).logAction(LayoutEditorEvent.LayoutEditorEventType.SHOW_LINT_MESSAGES);
    mySurface.setShowIssuePanel(true);
  }

  private void updateIssueTypeIcon(@NotNull IssueModel issueModel) {
    Icon icon;
    if (issueModel.getErrorCount() > 0) {
      icon = AllIcons.Ide.Notification.ErrorEvents;
    }
    else if (issueModel.getWarningCount() > 0) {
      icon = AllIcons.Ide.Notification.WarningEvents;
    }
    else if (issueModel.getIssueCount() > 0) {
      icon = AllIcons.Ide.Notification.InfoEvents;
    }
    else {
      icon = AllIcons.Ide.Notification.NoEvents;
    }
    myIcon.setIcon(icon, 0);
  }

  private void updateCountIcon(int markerCount) {
    if (markerCount > 10) {
      myTextIcon.setText(ERROR_MORE_THAN_9);
    }
    else if (markerCount > 1) {
      myTextIcon.setText(String.valueOf(markerCount));
    }
    else {
      myTextIcon.setText(EMPTY_STRING);
    }
    myIcon.setIcon(myTextIcon, 1, SwingConstants.CENTER);
  }
}
