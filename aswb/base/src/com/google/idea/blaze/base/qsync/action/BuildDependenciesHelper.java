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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Helper class for actions that build dependencies for source files, to allow the core logic to be
 * shared.
 */
public class BuildDependenciesHelper {

  /** Enum specifying which target(s) dependencies are built for */
  public enum DepsBuildType {
    /** Build dependencies of the specified target(s) */
    SELF,
    /** Build dependencies of the reverse dependencies of the specified target(s) */
    REVERSE_DEPS,
  }

  private final Project project;
  private final QuerySyncManager syncManager;
  private final DepsBuildType depsBuildType;

  public BuildDependenciesHelper(Project project, DepsBuildType buildType) {
    this.project = project;
    this.syncManager = QuerySyncManager.getInstance(project);
    this.depsBuildType = buildType;
  }

  boolean canEnableAnalysisNow() {
    return !syncManager.operationInProgress();
  }

  public TargetsToBuild getTargetsToEnableAnalysisFor(VirtualFile virtualFile) {
    if (!syncManager.isProjectLoaded() || syncManager.operationInProgress()) {
      return TargetsToBuild.NONE;
    }
    return syncManager.getTargetsToBuild(virtualFile);
  }

  public TargetsToBuild getTargetsToEnableAnalysisFor(Path workspaceRelativeFile) {
    if (!syncManager.isProjectLoaded() || syncManager.operationInProgress()) {
      return TargetsToBuild.NONE;
    }
    return syncManager.getTargetsToBuild(workspaceRelativeFile);
  }

  public int getSourceFileMissingDepsCount(TargetsToBuild toBuild) {
    Preconditions.checkState(toBuild.type() == TargetsToBuild.Type.SOURCE_FILE);
    QuerySyncProjectSnapshot snapshot = syncManager.getCurrentSnapshot().orElse(null);
    if (snapshot == null) {
      return 0;
    }
    return snapshot.getPendingExternalDeps(toBuild.targets()).size();
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

  public static VirtualFile getVirtualFile(AnActionEvent e) {
    return e.getData(CommonDataKeys.VIRTUAL_FILE);
  }

  public ImmutableSet<Path> getWorkingSet() throws BuildException {
    // TODO: Any output from the context here is not shown in the console.
    return syncManager.getLoadedProject().orElseThrow().getWorkingSet(BlazeContext.create());
  }

  public ImmutableSet<Label> getAffectedTargetsForPaths(ImmutableSet<Path> paths) {

    TargetDisambiguator disambiguator = TargetDisambiguator.createForPaths(paths, this);
    ImmutableSet<TargetsToBuild> ambiguousTargets = disambiguator.calculateUnresolvableTargets();
    if (!ambiguousTargets.isEmpty()) {
      QuerySyncManager.getInstance(project)
          .notifyWarning(
              "Ambiguous target sets found",
              "Ambiguous target sets for some files; not building them: "
                  + ambiguousTargets.stream()
                      .map(TargetsToBuild::sourceFilePath)
                      .flatMap(Optional::stream)
                      .map(Path::toString)
                      .collect(joining(", ")));
    }

    return disambiguator.unambiguousTargets;
  }

  public void enableAnalysis(
      Class<? extends AnAction> actionClass, AnActionEvent e, PopupPositioner popupPositioner) {
    ImmutableSet<Label> additionalTargets;
    if (QuerySyncSettings.getInstance().buildWorkingSet()) {
      try {
        additionalTargets = getAffectedTargetsForPaths(getWorkingSet());
      } catch (BuildException be) {
        syncManager.notifyWarning(
            "Could not obtain working set",
            String.format("Error trying to obtain working set. Not including it in build: %s", be));
        additionalTargets = ImmutableSet.of();
      }
    } else {
      additionalTargets = ImmutableSet.of();
    }
    enableAnalysis(actionClass, e, popupPositioner, additionalTargets);
  }

  public void enableAnalysis(
      Class<? extends AnAction> actionClass,
      AnActionEvent e,
      PopupPositioner positioner,
      ImmutableSet<Label> additionalTargetsToBuild) {
    VirtualFile vfile = getVirtualFile(e);
    determineTargetsAndRun(
        vfile,
        positioner,
        labels -> {
          if (labels.isEmpty()) {
            return;
          }
          QuerySyncActionStatsScope querySyncActionStats =
              QuerySyncActionStatsScope.createForFile(actionClass, e, vfile);
          enableAnalysis(labels, querySyncActionStats);
        },
        additionalTargetsToBuild);
  }

  public void determineTargetsAndRun(
      VirtualFile vf, PopupPositioner positioner, Consumer<ImmutableSet<Label>> consumer) {
    determineTargetsAndRun(vf, positioner, consumer, ImmutableSet.of());
  }

  public void determineTargetsAndRun(
      VirtualFile vf,
      PopupPositioner positioner,
      Consumer<ImmutableSet<Label>> consumer,
      ImmutableSet<Label> additionalTargetsToBuild) {
    TargetsToBuild toBuild = getTargetsToEnableAnalysisFor(vf);

    if (toBuild.overlapsWith(additionalTargetsToBuild)
        || (toBuild.isEmpty() && !additionalTargetsToBuild.isEmpty())) {
      consumer.accept(additionalTargetsToBuild);
      return;
    }

    if (toBuild.isEmpty()) {
      consumer.accept(ImmutableSet.of());
      return;
    }

    if (!toBuild.isAmbiguous()) {
      consumer.accept(
          ImmutableSet.<Label>builder()
              .addAll(toBuild.targets())
              .addAll(additionalTargetsToBuild)
              .build());
      return;
    }

    chooseTargetToBuildFor(
        vf.getName(),
        toBuild,
        positioner,
        label ->
            consumer.accept(
                ImmutableSet.<Label>builder().add(label).addAll(additionalTargetsToBuild).build()));
  }

  void enableAnalysis(ImmutableSet<Label> targets, QuerySyncActionStatsScope querySyncActionStats) {
    switch (depsBuildType) {
      case SELF:
        syncManager.enableAnalysis(targets, querySyncActionStats, TaskOrigin.USER_ACTION);
        break;
      case REVERSE_DEPS:
        syncManager.enableAnalysisForReverseDeps(
            targets, querySyncActionStats, TaskOrigin.USER_ACTION);
    }
  }

  public void chooseTargetToBuildFor(
      Path workspaceRelativePath,
      TargetsToBuild toBuild,
      PopupPositioner positioner,
      Consumer<Label> chosenConsumer) {
    chooseTargetToBuildFor(
        WorkspaceRoot.fromProject(project).path().resolve(workspaceRelativePath).toString(),
        toBuild,
        positioner,
        chosenConsumer);
  }

  public void chooseTargetToBuildFor(
      String fileName,
      TargetsToBuild toBuild,
      PopupPositioner positioner,
      Consumer<Label> chosenConsumer) {
    JBPopupFactory factory = JBPopupFactory.getInstance();
    ListPopup popup =
        factory.createListPopup(SelectTargetPopupStep.create(toBuild, fileName, chosenConsumer));
    positioner.showInCorrectPosition(popup);
  }

  static class SelectTargetPopupStep extends BaseListPopupStep<Label> {
    static SelectTargetPopupStep create(
        TargetsToBuild toBuild, String fileName, Consumer<Label> onChosen) {
      ImmutableList<Label> rows =
          ImmutableList.sortedCopyOf(Comparator.comparing(Label::toString), toBuild.targets());

      return new SelectTargetPopupStep(rows, fileName, onChosen);
    }

    private final Consumer<Label> onChosen;

    SelectTargetPopupStep(ImmutableList<Label> rows, String forFileName, Consumer<Label> onChosen) {
      super("Select target to build for " + forFileName, rows);
      this.onChosen = onChosen;
    }

    @Override
    public PopupStep<?> onChosen(Label selectedValue, boolean finalChoice) {
      if (selectedValue == null) {
        return FINAL_CHOICE;
      }
      if (finalChoice) {
        onChosen.accept(selectedValue);
      }
      return FINAL_CHOICE;
    }
  }
}
