/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.QuerySyncProjectListener;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.deps.TargetBuildInfo;
import java.util.Collection;
import java.util.Optional;

/** Updates project info according to the newly generated build graph. */
public class ProjectStatsLogger implements QuerySyncProjectListener {

  /** Entry point for instantiating {@link ProjectStatsLogger}. */
  public static class Provider implements QuerySyncProjectListenerProvider {
    @Override
    public QuerySyncProjectListener createListener(QuerySyncProject querySyncProject) {
      return new ProjectStatsLogger(
          querySyncProject.getArtifactTracker(), querySyncProject.getProjectViewSet());
    }
  }

  private ArtifactTracker<?> artifactTracker;
  private ProjectViewSet projectViewSet;

  public ProjectStatsLogger(ArtifactTracker<?> artifactTracker, ProjectViewSet projectViewSet) {
    this.artifactTracker = artifactTracker;
    this.projectViewSet = projectViewSet;
  }

  @Override
  public void onNewProjectSnapshot(Context<?> context, QuerySyncProjectSnapshot instance) {
    Optional.ofNullable(context.getScope(QuerySyncActionStatsScope.class))
        .ifPresent(
            scope -> {
              scope
                  .getProjectInfoStatsBuilder()
                  .setLanguagesActive(instance.queryData().projectDefinition().languageClasses())
                  .setBlazeProjectFiles(
                      projectViewSet.listScalarItems(ImportSection.KEY).stream()
                          .map(WorkspacePath::asPath)
                          .collect(toImmutableSet()))
                  .setProjectTargetCount(instance.graph().allTargets().size())
                  .setExternalDependencyCount(instance.graph().projectDeps().size());
              scope
                  .getDependenciesInfoStatsBuilder()
                  .setTargetMapSize(instance.graph().targetMap().size())
                  .setLibraryCount(instance.project().getLibraryCount())
                  .setJarCount(
                      instance.artifactState().targets().stream()
                          .map(TargetBuildInfo::javaInfo)
                          .flatMap(Optional::stream)
                          .map(JavaArtifactInfo::jars)
                          .mapToInt(Collection::size)
                          .sum());
            });
    ;
  }
}
