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
package com.android.tools.idea.common.actions;

import com.android.tools.idea.actions.DesignerActions;
import com.android.tools.idea.actions.DesignerDataKeys;
import com.android.tools.idea.common.error.IssueModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlSupportedActions;
import com.android.tools.idea.uibuilder.surface.NlSupportedActionsKt;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.util.IconUtil;
import icons.StudioIcons;
import javax.swing.Icon;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * Action which shows the current number of warnings in the layout and when clicked, shows them.
 */
public class IssueNotificationAction extends ToggleAction {
  public static final String NO_ISSUE = "No Issue";
  public static final String SHOW_ISSUE = "Show Warnings and Errors";
  private static final String DEFAULT_TOOLTIP = "Toggle visibility of issue panel";
  private static final Icon DISABLED_ICON = IconUtil.desaturate(StudioIcons.Common.ERROR);

  /**
   * Returns the icon and description to be used when the surface is active but there are no errors.
   * Both can be null to allow using the {@link IssueNotificationAction} defaults.
   */
  @NotNull
  protected Pair<Icon, String> getNoErrorsIconAndDescription(@NotNull AnActionEvent event) {
    return new Pair<>(null, null);
  }

  @NotNull
  public static IssueNotificationAction getInstance() {
    return (IssueNotificationAction)ActionManager.getInstance().getAction(DesignerActions.ACTION_TOGGLE_ISSUE_PANEL);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (DesignerActionUtils.isActionEventFromJTextField(event)) {
      event.getPresentation().setEnabled(false);
      return;
    }
    super.update(event);
    DesignSurface surface = event.getData(DesignerDataKeys.DESIGN_SURFACE);
    Presentation presentation = event.getPresentation();

    if (surface == null || !NlSupportedActionsKt.isActionSupported(surface, NlSupportedActions.TOGGLE_ISSUE_PANEL)) {
      event.getPresentation().setEnabled(false);
      presentation.setText(SHOW_ISSUE);
      presentation.setDescription(DEFAULT_TOOLTIP);
      presentation.setIcon(DISABLED_ICON);
    }
    else {
      event.getPresentation().setEnabled(true);
      IssueModel issueModel = surface.getIssueModel();
      int markerCount = issueModel.getIssueCount();

      presentation.setDescription(markerCount == 0 ? NO_ISSUE : SHOW_ISSUE);
      if (markerCount == 0) {
        Pair<Icon, String> iconAndDescription = getNoErrorsIconAndDescription(event);
        if (iconAndDescription.getSecond() != null) {
          presentation.setText(iconAndDescription.getSecond());
        }

        if (iconAndDescription.getFirst() == null) {
          presentation.setIcon(getIssueTypeIcon(issueModel));
        }
        else {
          presentation.setIcon(iconAndDescription.getFirst());
        }
      }
      else {
        presentation.setIcon(getIssueTypeIcon(issueModel));
        presentation.setText(DEFAULT_TOOLTIP);
      }
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    DesignSurface surface = e.getData(DesignerDataKeys.DESIGN_SURFACE);
    return surface != null && !surface.getIssuePanel().isMinimized();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    DesignSurface surface = e.getData(DesignerDataKeys.DESIGN_SURFACE);
    if (surface != null) {
      surface.getAnalyticsManager().trackShowIssuePanel();
      surface.setShowIssuePanel(state, true);
    }
  }

  @NotNull
  private static Icon getIssueTypeIcon(@NotNull IssueModel issueModel) {
    Icon icon;
    if (issueModel.getErrorCount() > 0) {
      icon = StudioIcons.Common.ERROR;
    }
    else if (issueModel.getWarningCount() > 0) {
      icon = StudioIcons.Common.WARNING;
    }
    else if (issueModel.getIssueCount() > 0) {
      icon = StudioIcons.Common.INFO;
    }
    else {
      icon = DISABLED_ICON;
    }
    return icon;
  }
}
