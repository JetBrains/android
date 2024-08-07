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
package com.google.idea.blaze.base.run.testmap;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.qsync.QuerySyncProjectData;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Used to locate tests from source files for things like right-clicks.
 *
 * <p>It's essentially a map from source file -> reachable test rules.
 */
public class ProjectSourceToTargetFinder implements SourceToTargetFinder {

  @Override
  public Future<Collection<TargetInfo>> targetsForSourceFiles(
      Project project, Set<File> sourceFiles, Optional<RuleType> ruleType) {
    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      QuerySyncProjectData projectData =
          (QuerySyncProjectData) BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (projectData == null) {
        return Futures.immediateFuture(ImmutableList.of());
      }
      ImmutableSet<TargetInfo> targets =
          sourceFiles.stream()
              .map(file -> projectData.getWorkspacePathResolver().getWorkspacePath(file))
              .filter(Objects::nonNull)
              .flatMap(path -> projectData.getReverseDeps(path.asPath()).stream())
              .filter(not(target -> target.tags().contains("no-ide")))
              .filter(
                  buildTarget -> {
                    if (ruleType.isEmpty()) {
                      return true;
                    }
                    Kind kind = Kind.fromRuleName(buildTarget.kind());
                    if (kind == null) {
                      return false;
                    }
                    return kind.getRuleType().equals(ruleType.get());
                  })
              .map(TargetInfo::fromBuildTarget)
              .collect(toImmutableSet());
      return Futures.immediateFuture(targets);
    }
    FilteredTargetMap targetMap =
        SyncCache.getInstance(project)
            .get(ProjectSourceToTargetFinder.class, ProjectSourceToTargetFinder::computeTargetMap);
    if (targetMap == null) {
      return Futures.immediateFuture(ImmutableList.of());
    }
    ImmutableSet<TargetInfo> targets =
        targetMap.targetsForSourceFiles(sourceFiles).stream()
            .map(TargetIdeInfo::toTargetInfo)
            .filter(target -> !ruleType.isPresent() || target.getRuleType().equals(ruleType.get()))
            .collect(toImmutableSet());
    return Futures.immediateFuture(targets);
  }

  private static FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return computeTargetMap(
        project, projectData.getArtifactLocationDecoder(), projectData.getTargetMap());
  }

  private static FilteredTargetMap computeTargetMap(
      Project project, ArtifactLocationDecoder decoder, TargetMap targetMap) {
    return new FilteredTargetMap(project, decoder, targetMap, t -> true);
  }
}
