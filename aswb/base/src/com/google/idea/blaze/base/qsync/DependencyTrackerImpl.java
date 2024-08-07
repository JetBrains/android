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

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.idea.blaze.base.bazel.BazelExitCode;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.SnapshotHolder;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.deps.OutputInfo;
import com.google.idea.blaze.qsync.project.DependencyTrackingBehavior;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.RequestedTargets;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.util.Optional;

/**
 * A file that tracks what files in the project can be analyzed and what is the status of their
 * dependencies.
 *
 * <p>The dependencies tracked for a target depends on its {@link DependencyTrackingBehavior}, which
 * is in turn determined by the source code language of the target.
 */
public class DependencyTrackerImpl implements DependencyTracker {

  private final SnapshotHolder snapshotHolder;
  private final DependencyBuilder builder;
  private final ArtifactTracker<BlazeContext> artifactTracker;

  public DependencyTrackerImpl(
      SnapshotHolder snapshotHolder,
      DependencyBuilder builder,
      ArtifactTracker<BlazeContext> artifactTracker) {
    this.snapshotHolder = snapshotHolder;
    this.builder = builder;
    this.artifactTracker = artifactTracker;
  }

  private QuerySyncProjectSnapshot getCurrentSnapshot() {
    return snapshotHolder
        .getCurrent()
        .orElseThrow(() -> new IllegalStateException("Sync is not yet complete"));
  }

  /**
   * Builds the external dependencies of the given targets, putting the resultant libraries in the
   * shared library directory so that they are picked up by the IDE.
   */
  @Override
  public boolean buildDependenciesForTargets(BlazeContext context, DependencyBuildRequest request)
      throws IOException, BuildException {
    BuildDepsStatsScope.fromContext(context)
        .ifPresent(stats -> stats.setRequestedTargets(request.targets));
    QuerySyncProjectSnapshot snapshot = getCurrentSnapshot();

    Optional<RequestedTargets> maybeRequestedTargets = getRequestedTargets(snapshot, request);
    if (maybeRequestedTargets.isEmpty()) {
      return false;
    }

    buildDependencies(context, snapshot, maybeRequestedTargets.get());
    return true;
  }

  private Optional<RequestedTargets> getRequestedTargets(
      QuerySyncProjectSnapshot snapshot, DependencyBuildRequest request) {
    switch (request.requestType) {
      case MULTIPLE_TARGETS:
        return snapshot.graph().computeRequestedTargets(request.targets);
      case SINGLE_TARGET:
        return Optional.of(new RequestedTargets(request.targets, request.targets));
      case WHOLE_PROJECT:
        return Optional.of(
            new RequestedTargets(
                ImmutableSet.copyOf(snapshot.graph().allTargets()),
                snapshot.graph().projectDeps()));
    }
    throw new IllegalArgumentException("Invalid request type: " + request.requestType);
  }

  private void buildDependencies(
      BlazeContext context, QuerySyncProjectSnapshot snapshot, RequestedTargets requestedTargets)
      throws IOException, BuildException {
    BuildDepsStatsScope.fromContext(context)
        .ifPresent(stats -> stats.setBuildTargets(requestedTargets.buildTargets));
    OutputInfo outputInfo =
        builder.build(
            context,
            requestedTargets.buildTargets,
            snapshot.graph().getTargetLanguages(requestedTargets.buildTargets));
    reportErrorsAndWarnings(context, snapshot, outputInfo);

    artifactTracker.update(requestedTargets.expectedDependencyTargets, outputInfo, context);
  }

  private void reportErrorsAndWarnings(
      BlazeContext context, QuerySyncProjectSnapshot snapshot, OutputInfo outputInfo)
      throws NoDependenciesBuiltException {
    if (outputInfo.isEmpty()) {
      throw new NoDependenciesBuiltException(
          "Build produced no usable outputs. Please fix any build errors and retry. If you"
              + " observe 'no such target' errors, your project may be out of sync. Please sync"
              + " the project and retry.");
    }

    if (!outputInfo.getTargetsWithErrors().isEmpty()) {
      ProjectDefinition projectDefinition = snapshot.queryData().projectDefinition();
      context.setHasWarnings();
      ImmutableListMultimap<Boolean, Label> targetsByInclusion =
          Multimaps.index(outputInfo.getTargetsWithErrors(), projectDefinition::isIncluded);
      if (targetsByInclusion.containsKey(false)) {
        ImmutableList<?> errorTargets = targetsByInclusion.get(false);
        context.output(
            PrintOutput.error(
                "%d external %s had build errors: \n  %s",
                errorTargets.size(),
                StringUtil.pluralize("dependency", errorTargets.size()),
                errorTargets.stream().limit(10).map(Object::toString).collect(joining("\n  "))));
        if (errorTargets.size() > 10) {
          context.output(PrintOutput.log("and %d more.", errorTargets.size() - 10));
        }
      }
      if (targetsByInclusion.containsKey(true)) {
        ImmutableList<?> errorTargets = targetsByInclusion.get(true);
        context.output(
            PrintOutput.output(
                "%d project %s had build errors: \n  %s",
                errorTargets.size(),
                StringUtil.pluralize("target", errorTargets.size()),
                errorTargets.stream().limit(10).map(Object::toString).collect(joining("\n  "))));
        if (errorTargets.size() > 10) {
          context.output(PrintOutput.log("and %d more.", errorTargets.size() - 10));
        }
      }
    } else if (outputInfo.getExitCode() != BazelExitCode.SUCCESS) {
      // This will happen if there is an error in a build file, as no build actions are attempted
      // in that case.
      context.setHasWarnings();
      context.output(PrintOutput.error("There were build errors."));
    }
    if (context.hasWarnings()) {
      context.output(
          PrintOutput.error(
              "Your dependencies may be incomplete. If you see unresolved symbols, please fix the"
                  + " above build errors and try again."));
      context.setHasWarnings();
    }
  }

}
