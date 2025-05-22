/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.projectview;

import com.google.idea.blaze.base.project.BaseQuerySyncConversionUtility;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.sections.EnableCodeAnalysisOnSyncSection;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceLocationSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.exception.ConfigurationException;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Class that manages access to a project's {@link ProjectView}.
 */
public abstract class ProjectViewManager {
  private static final Logger logger = Logger.getInstance(ProjectViewManager.class);

  public static ProjectViewManager getInstance(Project project) {
    return project.getService(ProjectViewManager.class);
  }

  public static void migrateImportSettingsToProjectViewFile(Project project,
                                                            BlazeImportSettings importSettings,
                                                            ProjectViewSet.ProjectViewFile projectViewFile) {
    ProjectView.Builder projectView = ProjectView.builder(projectViewFile.projectView);
    boolean isWorkspaceLocationUpdated = addUpdateWorkspaceLocationSection(importSettings, projectViewFile, projectView);
    boolean isUseQuerySyncSectionUpdated = addUpdateUseQuerySyncSection(project, importSettings, projectViewFile, projectView);
    if (isWorkspaceLocationUpdated || isUseQuerySyncSectionUpdated) {
      String projectViewText = ProjectViewParser.projectViewToString(projectView.build());
      try {
        ProjectViewStorageManager.getInstance()
          .writeProjectView(projectViewText, projectViewFile.projectViewFile);
      }
      catch (IOException e) {
        logger.error(e);
      }
    }
  }

  private static boolean addUpdateWorkspaceLocationSection(BlazeImportSettings importSettings,
                                                           ProjectViewSet.ProjectViewFile projectViewFile,
                                                           ProjectView.Builder projectView) {
    ScalarSection<String> workspaceRootSection = null;
    if (projectViewFile.projectView.getSections().stream().noneMatch(x -> x.isSectionType(WorkspaceLocationSection.KEY))) {
      workspaceRootSection = ScalarSection.builder(WorkspaceLocationSection.KEY)
        .set(importSettings.getWorkspaceRoot())
        .build();
    }
    if (workspaceRootSection != null) {
      projectView.add(workspaceRootSection);
      return true;
    }
    return false;
  }

  private static boolean addUpdateUseQuerySyncSection(Project project,
                                                      BlazeImportSettings importSettings,
                                                      ProjectViewSet.ProjectViewFile projectViewFile,
                                                      ProjectView.Builder projectView) {
    ScalarSection<Boolean> useQuerySyncSection = ScalarSection.builder(UseQuerySyncSection.KEY)
      .set(importSettings.getProjectType() == BlazeImportSettings.ProjectType.QUERY_SYNC).build();
    Optional<Section<?>> existingUseQuerySyncSection = projectViewFile.projectView
      .getSections().stream().filter(x -> x.isSectionType(UseQuerySyncSection.KEY)).findAny();

    if (existingUseQuerySyncSection.isEmpty()) {
      projectView.add(useQuerySyncSection);
      return true;
    }

    BlazeImportSettings.ProjectType existingProjectType =
      projectViewFile.projectView.getScalarValue(UseQuerySyncSection.KEY)
      ? BlazeImportSettings.ProjectType.QUERY_SYNC
      : BlazeImportSettings.ProjectType.ASPECT_SYNC;

    if (existingProjectType == BlazeImportSettings.ProjectType.ASPECT_SYNC
          && importSettings.getProjectType() == BlazeImportSettings.ProjectType.QUERY_SYNC) {
        existingUseQuerySyncSection.ifPresent(projectView::remove);
        projectView.add(TextBlockSection.of(TextBlock.of(BaseQuerySyncConversionUtility.AUTO_CONVERSION_INDICATOR)));
        projectView.add(useQuerySyncSection);
        projectView.add(ScalarSection.builder(EnableCodeAnalysisOnSyncSection.KEY).set(true).build());
        Notifications.Bus.notify(
          new Notification(
            "QuerySync",
            "Your project was auto-converted to Query Sync. To learn more, click the link below.",
            NotificationType.INFORMATION).addAction(new NotificationAction("go/query-sync#auto-convert") {

            @Override
            public void actionPerformed(@NotNull AnActionEvent e,
                                        @NotNull Notification notification) {
              BrowserUtil.browse("http://go/query-sync#auto-convert");

            }
          }),
          project);
        return true;
    }
    return false;
  }

  /**
   * Returns the current project view collection. If there is an error, returns null.
   */
  @Nullable
  public abstract ProjectViewSet getProjectViewSet();

  /**
   * Reloads the project view, replacing the current one only if there are no errors.
   */
  public abstract ProjectViewSet reloadProjectView(BlazeContext context) throws BuildException;

  public abstract ProjectViewSet doLoadProjectView(BlazeContext context, BlazeImportSettings importSettings)
    throws ConfigurationException;
}
