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
import com.android.tools.idea.common.error.Issue;
import com.android.tools.idea.common.error.IssueModel;
import com.android.tools.idea.common.error.IssuePanelService;
import com.android.tools.idea.common.error.IssuePanelServiceKt;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.surface.NlSupportedActions;
import com.android.tools.idea.uibuilder.surface.NlSupportedActionsKt;
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import icons.StudioIcons;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.Icon;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Action which shows the current number of warnings in the layout and when clicked, shows them.
 */
public class IssueNotificationAction extends ToggleAction {
  public static final String NO_ISSUE = "No Issue";
  public static final String SHOW_ISSUE = "Show Warnings and Errors";
  private static final String DEFAULT_TOOLTIP = "Toggle visibility of issue panel";

  @VisibleForTesting
  public static final Icon DISABLED_ICON = IconUtil.desaturate(StudioIcons.Common.ERROR_INLINE);

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
    DesignSurface<?> surface = event.getData(DesignerDataKeys.DESIGN_SURFACE);
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

      VisualLintService service = VisualLintService.getInstance(event.getRequiredData(PlatformDataKeys.PROJECT));
      List<VirtualFile> files = surface.getModels().stream().map(NlModel::getVirtualFile).toList();
      Set<Issue> visualLintIssues =
        service.getIssueModel().getIssues().stream().filter(it -> files.contains(it.getSource().getFile())).collect(Collectors.toSet());

      markerCount += visualLintIssues.size();
      presentation.setDescription(markerCount == 0 ? NO_ISSUE : SHOW_ISSUE);
      if (markerCount == 0) {
        Pair<Icon, String> iconAndDescription = getNoErrorsIconAndDescription(event);
        if (iconAndDescription.getSecond() != null) {
          presentation.setText(iconAndDescription.getSecond());
        }

        if (iconAndDescription.getFirst() == null) {
          presentation.setIcon(getIssueTypeIcon(issueModel, visualLintIssues));
        }
        else {
          presentation.setIcon(iconAndDescription.getFirst());
        }
      }
      else {
        presentation.setIcon(getIssueTypeIcon(issueModel, visualLintIssues));
        presentation.setText(DEFAULT_TOOLTIP);
      }
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    DesignSurface<?> surface = e.getData(DesignerDataKeys.DESIGN_SURFACE);
    if (surface == null) {
      return false;
    }
    return IssuePanelService.getInstance(surface.getProject()).isIssuePanelVisible(surface);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    DesignSurface<?> surface = e.getData(DesignerDataKeys.DESIGN_SURFACE);
    if (surface == null) {
      return;
    }
    IssuePanelServiceKt.setIssuePanelVisibility(surface, state, true, () -> {
      if (StudioFlags.NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS.get()) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project != null) {
          IssuePanelService.getInstance(project).focusIssuePanelIfVisible();
        }
      }
    });

  }

  @NotNull
  private static Icon getIssueTypeIcon(@NotNull IssueModel issueModel, @NotNull Set<Issue> visualLintIssues) {
    Set<HighlightSeverity> visualLintSeverities = visualLintIssues.stream()
      .map(Issue::getSeverity).collect(Collectors.toSet());

    Icon icon;
    if (issueModel.getErrorCount() > 0 || visualLintSeverities.contains(HighlightSeverity.ERROR)) {
      icon = StudioIcons.Common.ERROR_INLINE;
    }
    else if (issueModel.getWarningCount() > 0 || visualLintSeverities.contains(HighlightSeverity.WARNING)) {
      icon = StudioIcons.Common.WARNING_INLINE;
    }
    else if (issueModel.getIssueCount() > 0 || !visualLintSeverities.isEmpty()) {
      icon = StudioIcons.Common.INFO_INLINE;
    }
    else {
      icon = DISABLED_ICON;
    }
    return icon;
  }
}
