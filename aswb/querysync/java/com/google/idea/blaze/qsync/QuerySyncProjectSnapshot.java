/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync;

import static com.google.common.base.Predicates.or;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.BuildTarget;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.deps.ArtifactIndex;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.BuildGraphDataImpl;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectProto;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A fully sync'd project at a point in time. This consists of:
 *
 * <ul>
 *   <li>The output from the query part of sync, {@link #queryData()}.
 *   <li>Build graph information derived form the sync data, {@link #graph()}.
 *   <li>The output from all dependency builds to date, {@link #artifactState()}.
 *   <li>The IntelliJ project structure derived from the above, presented as a proto, {@link
 *       #project()}.
 * </ul>
 *
 * <p>This class is immutable, any modifications to the project will yield a new instance.
 */
@AutoValue
public abstract class QuerySyncProjectSnapshot {

  public static final QuerySyncProjectSnapshot EMPTY =
      builder()
          .graph(BuildGraphData.EMPTY)
          .project(ProjectProto.Project.getDefaultInstance())
          .artifactState(ArtifactTracker.State.EMPTY)
          .queryData(PostQuerySyncData.EMPTY)
          .build();

  public abstract PostQuerySyncData queryData();

  public abstract BuildGraphData graph();

  public abstract ArtifactTracker.State artifactState();

  /** Project proto reflecting the structure of the IJ project. */
  public abstract ProjectProto.Project project();

  public abstract ImmutableSet<Label> incompleteTargets();

  public static Builder builder() {
    return new AutoValue_QuerySyncProjectSnapshot.Builder().incompleteTargets(ImmutableSet.of());
  }

  public abstract Builder toBuilder();

  /**
   * Given a path to a file it returns the targets that own the file.
   *
   * @param path a workspace relative path.
   */
  public Set<Label> getTargetOwners(Path path) {
    return graph().getSourceFileOwners(path);
  }

  /**
   * Given a path to a file it returns the target that owns the file. Note that in general there
   * could be multiple targets that compile a file, but we try to choose the smallest one, as it
   * would have everything the file needs to be compiled.
   *
   * @param path a workspace relative path.
   * @deprecated Since the "choose the smallest" logic used in here is problematic, please use
   *     {@link #getTargetOwners(Path)} instead.
   */
  @Nullable
  @Deprecated
  public Label getTargetOwner(Path path) {
    return graph().selectLabelWithLeastDeps(graph().getSourceFileOwners(path));
  }

  /** Returns mapping of targets to {@link BuildTarget} */
  public Collection<Label> getAllLoadedTargets() {
    return graph().allLoadedTargets();
  }

  @Memoized
  public ArtifactIndex getArtifactIndex() {
    return ArtifactIndex.create(artifactState());
  }

  /**
   * For given project targets, returns all dependency targets that are {@link
   * BuildGraphDataImpl#projectDeps()} external} to the project, from which build artifacts are needed
   * for the targets sources to be edited fully. This method returns the dependencies for the target
   * with fewest pending so that if dependencies have been built for one, the empty set will be
   * returned even if others have pending dependencies.
   *
   * @param projectTargets The set of project targets which include a given source file.
   */
  public Set<Label> getPendingExternalDeps(Set<Label> projectTargets) {
    Set<Label> syncedTargets = artifactState().deprecatedSyncedTargetKeys();
    Set<Label> incompleteTargets = incompleteTargets();
    ImmutableMap<Label, ImmutableSet<Label>> byTarget = projectTargets.stream()
      .collect(
        toImmutableMap(
          Function.identity(),
          it ->
          {
            BuildGraphData buildGraphData = graph();
            return buildGraphData
              .getExternalDependencies(ImmutableList.of(it))
              .stream()
              .filter(target -> !syncedTargets.contains(target) || incompleteTargets.contains(target))
              .collect(toImmutableSet());
          }
        )
      );
    return byTarget.values().stream()
        .min(Comparator.comparingInt(Collection::size))
        .orElse(ImmutableSet.of());
  }

  /** Recursively get all the transitive deps outside the project */
  public Set<Label> getPendingTargets(Path workspaceRelativePath) {
    Preconditions.checkState(!workspaceRelativePath.isAbsolute(), workspaceRelativePath);

    return getPendingExternalDeps(getTargetOwners(workspaceRelativePath));
  }

  /** Builder for {@link QuerySyncProjectSnapshot}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder queryData(PostQuerySyncData value);

    public abstract Builder graph(BuildGraphData value);

    public abstract Builder artifactState(ArtifactTracker.State state);

    public abstract Builder project(ProjectProto.Project value);

    public abstract Builder incompleteTargets(ImmutableSet<Label> value);

    public abstract QuerySyncProjectSnapshot build();
  }
}
