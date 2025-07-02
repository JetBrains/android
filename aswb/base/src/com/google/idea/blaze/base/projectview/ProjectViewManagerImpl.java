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

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceLocationSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.exception.ConfigurationException;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/** Project view manager implementation. Stores mutable per-project user settings. */
public final class ProjectViewManagerImpl extends ProjectViewManager {

  private final Project project;

  public ProjectViewManagerImpl(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public ProjectViewSet getProjectViewSet() {
      return BlazeImportSettingsManager.getInstance(project).getProjectViewSet();
  }

  @Override
  public ProjectViewSet reloadProjectView(BlazeContext context) throws BuildException {
    return BlazeImportSettingsManager.getInstance(project).reloadProjectView();
  }

  @Override
  public ProjectViewSet doLoadProjectView(BlazeContext context, BlazeImportSettings importSettings) throws ConfigurationException {
    final var projectViewRootFile = importSettings.getProjectViewFile();
    ProjectViewParser rootParser = new ProjectViewParser(BlazeContext.create(), null);
    rootParser.parseProjectView(projectViewRootFile, List.of(WorkspaceLocationSection.PARSER));
    final var rootProjectViewSet = rootParser.getResult();
    final var rootProjectView = Optional.ofNullable(rootProjectViewSet.getTopLevelProjectViewFile()).map(it -> it.projectView);
    final var workspaceLocation = rootProjectView.map(it -> it.getScalarValue(WorkspaceLocationSection.KEY));
    final WorkspacePathResolver workspacePathResolver;
    workspacePathResolver =
      new WorkspacePathResolverImpl(WorkspaceRoot.fromProto(workspaceLocation.orElseGet(importSettings::getWorkspaceRoot)));

    File projectViewFile = new File(projectViewRootFile);
    ProjectViewParser parser = new ProjectViewParser(context, workspacePathResolver);
    parser.parseProjectView(projectViewFile);

    if (context.hasErrors()) {
      throw new ConfigurationException(
          "Failed to read project view from " + projectViewFile.getAbsolutePath());
    }
    return parser.getResult();
  }
}
