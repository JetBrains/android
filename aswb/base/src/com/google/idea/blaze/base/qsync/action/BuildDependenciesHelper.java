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
package com.google.idea.blaze.base.qsync.action;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Helper class for actions that build dependencies for source files, to allow the core logic to be
 * shared.
 */
public class BuildDependenciesHelper {
  private final Project project;
  private final QuerySyncManager syncManager;

  public BuildDependenciesHelper(Project project) {
    this.project = project;
    this.syncManager = QuerySyncManager.getInstance(project);
  }

  public Project getProject() {
    return project;
  }

  boolean canEnableAnalysisNow() {
    return !syncManager.operationInProgress();
  }

  public TargetsToBuild getTargetsToEnableAnalysisFor(VirtualFile virtualFile) {
    if (!syncManager.isProjectLoaded() || syncManager.operationInProgress()) {
      return TargetsToBuild.None.INSTANCE;
    }
    return syncManager.getTargetsToBuild(virtualFile);
  }

  public TargetsToBuild getTargetsToEnableAnalysisFor(Path workspaceRelativeFile) {
    if (!syncManager.isProjectLoaded() || syncManager.operationInProgress()) {
      return TargetsToBuild.None.INSTANCE;
    }
    return syncManager.getTargetsToBuild(workspaceRelativeFile);
  }

  public int getSourceFileMissingDepsCount(TargetsToBuild.SourceFile toBuild) {
    QuerySyncProjectSnapshot snapshot = syncManager.getCurrentSnapshot().orElse(null);
    if (snapshot == null) {
      return 0;
    }
    return snapshot.getPendingExternalDeps(toBuild.getTargets()).size();
  }

  public Optional<Path> getRelativePathToEnableAnalysisFor(VirtualFile virtualFile) {
    if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      return Optional.empty();
    }
    Path workspaceRoot = WorkspaceRoot.fromProject(project).path();
    Path filePath = virtualFile.toNioPath();
    if (!filePath.startsWith(workspaceRoot)) {
      return Optional.empty();
    }

    Path relative = workspaceRoot.relativize(filePath);
    if (!syncManager.canEnableAnalysisFor(relative)) {
      return Optional.empty();
    }
    return Optional.of(relative);
  }

  public ImmutableSet<Path> getWorkingSet() throws BuildException {
    // TODO: Any output from the context here is not shown in the console.
    return syncManager.getLoadedProject().orElseThrow().getWorkingSet(BlazeContext.create());
  }

  public ImmutableSet<Label> getAffectedTargetsForPaths(ImmutableSet<Path> paths) {

    TargetDisambiguator disambiguator = TargetDisambiguator.createForPaths(this, paths);
    Set<TargetsToBuild> ambiguousTargets = disambiguator.calculateUnresolvableTargets();
    if (!ambiguousTargets.isEmpty()) {
      QuerySyncManager.getInstance(project)
          .notifyWarning(
              "Ambiguous target sets found",
              "Ambiguous target sets found; not building them: "
                  + ambiguousTargets.stream()
                      .map(TargetsToBuild::getDisplayLabel)
                      .collect(joining(", ")));
    }

    return ImmutableSet.copyOf(disambiguator.getUnambiguousTargets());
  }

  /**
   * Additional targets to consider when disambiguating targets to build for a file.
   */
  public sealed interface TargetDisambiguationAnchors {
    ImmutableSet<Label> anchorTargets();

    /**
     * A set of specific targets to consider when disambiguating targets to build for a file.
     */
    record Targets(ImmutableSet<Label> anchorTargets) implements TargetDisambiguationAnchors {}
    TargetDisambiguationAnchors NONE = new Targets(ImmutableSet.of());

    /**
     * An anchor requesting that the working set be considered when disambiguating targets to build for a file.
     */
    final class WorkingSet implements TargetDisambiguationAnchors {
      private final BuildDependenciesHelper helper;

      public WorkingSet(BuildDependenciesHelper helper) {
        this.helper = helper;
      }

      @Override
      public ImmutableSet<Label> anchorTargets() {
        return helper.getWorkingSetTargetsIfEnabled();
      }
    }
  }

  public void determineTargetsAndRun(
      VirtualFile vf,
      PopupPositioner positioner,
      Consumer<ImmutableSet<Label>> consumer,
      TargetDisambiguationAnchors targetDisambiguationAnchors) {
    TargetsToBuild toBuild = getTargetsToEnableAnalysisFor(vf);
    final var additionalTargets = targetDisambiguationAnchors.anchorTargets();

    if (toBuild.overlapsWith(additionalTargets)
        || (toBuild.isEmpty() && !additionalTargets.isEmpty())) {
      consumer.accept(additionalTargets);
      return;
    }

    if (toBuild.isEmpty()) {
      consumer.accept(ImmutableSet.of());
      return;
    }

    if (!toBuild.isAmbiguous()) {
      consumer.accept(ImmutableSet.copyOf(toBuild.getTargets()));
      return;
    }

    BuildDependenciesHelperSelectTargetPopup.chooseTargetToBuildFor(
        vf.getName(),
        toBuild,
        positioner,
        label -> consumer.accept(ImmutableSet.of(label)));
  }

  /**
   * Returns the set of targets affected by files in the current working set if automatic building of the dependencies
   * in the working set is enabled.
   */
  public ImmutableSet<Label> getWorkingSetTargetsIfEnabled() {
    return QuerySyncSettings.getInstance().buildWorkingSet() ? getWorkingSetTargets() : ImmutableSet.of();
  }

  private ImmutableSet<Label> getWorkingSetTargets() {
    try {
      return getAffectedTargetsForPaths(getWorkingSet());
    } catch (BuildException be) {
      this.syncManager.notifyWarning(
        "Could not obtain working set",
        String.format("Error trying to obtain working set. Not including it in build: %s", be));
      return ImmutableSet.of();
    }
  }
}
