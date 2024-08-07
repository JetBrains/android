/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.vcs.VcsSyncListener;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;

/** Kicks off a prefetch task when the base VCS revision/commit/CL/whatever changes. */
class PrefetchVcsSyncListener implements VcsSyncListener {

  private static final BoolExperiment enabled = new BoolExperiment("prefetch.on.vcs.sync", true);

  @Override
  public void onVcsSync(Project project) {
    if (!Blaze.isBlazeProject(project) || !enabled.getValue()) {
      return;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return;
    }
    PrefetchService.getInstance().clearPrefetchCache();
    PrefetchIndexingTask.submitPrefetchingTask(
        project,
        PrefetchService.getInstance().prefetchProjectFiles(project, projectViewSet, projectData),
        "Prefetching on VCS state change");
  }
}
