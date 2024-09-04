/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.prefetch;

import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.filecache.RemoteOutputsCache;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/** Run prefetching on project open, prior to initial indexing step. */
public class PrefetchProjectInitializer implements StartupActivity.DumbAware {
  private static final Logger logger = Logger.getInstance(PrefetchProjectInitializer.class);

  private static final BoolExperiment prefetchOnProjectOpen =
      new BoolExperiment("prefetch.on.project.open2", true);

  @Override
  public void runActivity(Project project) {
    if (prefetchOnProjectOpen.getValue()) {
      prefetchProjectFiles(project);
    }
  }

  private static void prefetchProjectFiles(Project project) {
    if (Blaze.getProjectType(project) != ProjectType.ASPECT_SYNC) {
      // TODO(querysync)
      return;
    }
    PrefetchIndexingTask.submitPrefetchingTask(
        project,
        PooledThreadExecutor.INSTANCE.submit(
            () -> {
              RemoteOutputsCache.getInstance(project).initialize();
              FileCaches.initialize(project);
            }),
        "Reading local caches");

    PrefetchIndexingTask.submitPrefetchingTask(
        project,
        Futures.submitAsync(
            () -> {
              BlazeProjectData projectData = getBlazeProjectData(project);
              ProjectViewSet viewSet = getProjectViewSet(project);
              if (projectData == null || viewSet == null) {
                return Futures.immediateFuture(null);
              }
              return PrefetchService.getInstance()
                  .prefetchProjectFiles(project, viewSet, projectData);
            },
            PooledThreadExecutor.INSTANCE),
        "Initial Prefetching");
  }

  @Nullable
  private static BlazeProjectData getBlazeProjectData(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return null;
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).loadProject(importSettings);
    if (blazeProjectData == null) {
      logger.info("Couldn't load project data for prefetcher");
    }
    return blazeProjectData;
  }

  /** Get the cached {@link ProjectViewSet}, or reload it from source. */
  @Nullable
  private static ProjectViewSet getProjectViewSet(Project project) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet != null) {
      return projectViewSet;
    }
    return Scope.root(
        context -> {
          return ProjectViewManager.getInstance(project).reloadProjectView(context);
        });
  }
}
