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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.base.util.SerializationUtil;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.exception.ConfigurationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;

/** Project view manager implementation. Stores mutable per-project user settings. */
final class ProjectViewManagerImpl extends ProjectViewManager {

  private static final Logger logger = Logger.getInstance(ProjectViewManagerImpl.class);
  private static final String CACHE_FILE_NAME = "project.view.dat";

  private final Project project;
  @Nullable private ProjectViewSet projectViewSet;
  private boolean projectViewSetLoaded = false;

  public ProjectViewManagerImpl(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public ProjectViewSet getProjectViewSet() {
    if (projectViewSet == null && !projectViewSetLoaded) {
      ProjectViewSet loadedProjectViewSet = null;
      try {
        BlazeImportSettings importSettings =
            BlazeImportSettingsManager.getInstance(project).getImportSettings();
        if (importSettings == null) {
          return null;
        }
        File file = getCacheFile(project, importSettings);

        List<ClassLoader> classLoaders = Lists.newArrayList();
        classLoaders.add(getClass().getClassLoader());
        classLoaders.add(Thread.currentThread().getContextClassLoader());
        loadedProjectViewSet = (ProjectViewSet) SerializationUtil.loadFromDisk(file, classLoaders);
      } catch (IOException e) {
        logger.warn("Failed to load project view set: " + e.getMessage());
      }
      this.projectViewSet = loadedProjectViewSet;
      this.projectViewSetLoaded = true;
    }
    return projectViewSet;
  }

  @Nullable
  @Override
  public ProjectViewSet reloadProjectView(BlazeContext context) {
    SaveUtil.saveAllFiles();
    WorkspacePathResolver pathResolver = computeWorkspacePathResolver(project, context);
    try {
      return pathResolver != null ? reloadProjectView(context, pathResolver) : null;
    } catch (BuildException e) {
      context.handleException("Failed to reload project view", e);
      return null;
    }
  }

  @Override
  public ProjectViewSet reloadProjectView(
      BlazeContext context, WorkspacePathResolver workspacePathResolver) throws BuildException {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    File projectViewFile = new File(importSettings.getProjectViewFile());
    ProjectViewParser parser = new ProjectViewParser(context, workspacePathResolver);
    parser.parseProjectView(projectViewFile);

    if (context.hasErrors()) {
      throw new ConfigurationException(
          "Failed to read project view from " + projectViewFile.getAbsolutePath());
    }
    ProjectViewSet projectViewSet = parser.getResult();
    File file = getCacheFile(project, importSettings);
    try {
      SerializationUtil.saveToDisk(file, projectViewSet);
    } catch (IOException e) {
      logger.error(e);
    }
    this.projectViewSet = projectViewSet;
    return projectViewSet;
  }

  private static File getCacheFile(Project project, BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectCacheDir(project, importSettings), CACHE_FILE_NAME);
  }

  @Nullable
  private static WorkspacePathResolver computeWorkspacePathResolver(
      Project project, BlazeContext context) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData != null) {
      return projectData.getWorkspacePathResolver();
    }
    // otherwise try to compute the workspace path resolver from scratch
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    BlazeVcsHandler vcsHandler = BlazeVcsHandlerProvider.vcsHandlerForProject(project);
    BlazeVcsHandlerProvider.BlazeVcsSyncHandler vcsSyncHandler =
        vcsHandler != null ? vcsHandler.createSyncHandler() : null;
    if (vcsSyncHandler == null) {
      return new WorkspacePathResolverImpl(workspaceRoot);
    }
    boolean ok = vcsSyncHandler.update(context, BlazeExecutor.getInstance().getExecutor());
    return ok ? vcsSyncHandler.getWorkspacePathResolver() : null;
  }
}
