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

import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceLocationSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import javax.annotation.Nullable;

/** Class that manages access to a project's {@link ProjectView}. */
public abstract class ProjectViewManager {
  private static final Logger logger = Logger.getInstance(ProjectViewManager.class);

  public static ProjectViewManager getInstance(Project project) {
    return project.getService(ProjectViewManager.class);
  }

  public static void migrateImportSettingsToProjectViewFile(BlazeImportSettings importSettings,
                                                            ProjectViewSet.ProjectViewFile projectViewFile) {
    ScalarSection<String> workspaceRootSection = null;
    ScalarSection<UseQuerySyncSection.UseQuerySync> useQuerySyncSection = null;
    if (projectViewFile.projectView.getSections().stream().noneMatch(x -> x.isSectionType(WorkspaceLocationSection.KEY))) {
      workspaceRootSection = ScalarSection.builder(WorkspaceLocationSection.KEY)
        .set(importSettings.getWorkspaceRoot())
        .build();
    }
    if (projectViewFile.projectView.getSections().stream().noneMatch(x -> x.isSectionType(UseQuerySyncSection.KEY))) {
      useQuerySyncSection = ScalarSection.builder(UseQuerySyncSection.KEY)
        .set(importSettings.getProjectType() == BlazeImportSettings.ProjectType.QUERY_SYNC
             ? UseQuerySyncSection.UseQuerySync.TRUE
             : UseQuerySyncSection.UseQuerySync.FALSE).build();
    }
    if (workspaceRootSection != null || useQuerySyncSection != null) {
      ProjectView.Builder projectView = ProjectView.builder(projectViewFile.projectView);
      projectView.add(TextBlockSection.of(TextBlock.newLine()));
      if (workspaceRootSection != null) {
        projectView.add(workspaceRootSection);
      }
      if (useQuerySyncSection != null) {
        projectView.add(useQuerySyncSection);
      }
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

  /** Returns the current project view collection. If there is an error, returns null. */
  @Nullable
  public abstract ProjectViewSet getProjectViewSet();

  /**
   * Reloads the project view, replacing the current one only if there are no errors. Calculates a
   * VCS-aware {@link WorkspacePathResolver} if necessary.
   */
  public abstract ProjectViewSet reloadProjectView(BlazeContext context) throws BuildException;
}
