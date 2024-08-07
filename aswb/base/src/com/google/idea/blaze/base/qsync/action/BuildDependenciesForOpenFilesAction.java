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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper.DepsBuildType;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/** Action to build dependencies and enable analysis for all open editor tabs. */
public class BuildDependenciesForOpenFilesAction extends BlazeProjectAction {

  private final Logger logger = Logger.getInstance(BuildDependenciesForOpenFilesAction.class);

  @Override
  @NotNull
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent event) {
    BuildDependenciesHelper helper = new BuildDependenciesHelper(project, DepsBuildType.SELF);
    if (!helper.canEnableAnalysisNow()) {
      return;
    }
    // Each open source file may map to multiple targets, either because they're a build file
    // or because a source file is included in multiple targets.
    ImmutableSet<VirtualFile> openFiles =
        stream(FileEditorManager.getInstance(project).getAllEditors())
            .map(FileEditor::getFile)
            .collect(toImmutableSet());
    TargetDisambiguator disambiguator =
        TargetDisambiguator.createForFiles(project, openFiles, helper);
    ImmutableSet<TargetsToBuild> ambiguousTargets = disambiguator.calculateUnresolvableTargets();
    QuerySyncActionStatsScope querySyncActionStats =
        QuerySyncActionStatsScope.createForFiles(getClass(), event, openFiles);

    if (ambiguousTargets.isEmpty()) {
      // there are no ambiguous targets that could not be automatically disambiguated.
      helper.enableAnalysis(disambiguator.unambiguousTargets, querySyncActionStats);
    } else if (ambiguousTargets.size() == 1) {
      // there is a single ambiguous target set. Show the UI to disambiguate it.
      TargetsToBuild ambiguousOne = Iterables.getOnlyElement(ambiguousTargets);
      helper.chooseTargetToBuildFor(
          ambiguousOne.sourceFilePath().orElseThrow(),
          ambiguousOne,
          PopupPositioner.showAtMousePointerOrCentered(event),
          chosen ->
              helper.enableAnalysis(
                  ImmutableSet.<Label>builder()
                      .addAll(disambiguator.unambiguousTargets)
                      .add(chosen)
                      .build(),
                  querySyncActionStats));
    } else {
      logger.warn(
          "Multiple ambiguous target sets for open files; not building them: "
              + ambiguousTargets.stream()
                  .map(TargetsToBuild::sourceFilePath)
                  .flatMap(Optional::stream)
                  .map(Path::toString)
                  .collect(joining(", ")));
      if (!disambiguator.unambiguousTargets.isEmpty()) {
        helper.enableAnalysis(disambiguator.unambiguousTargets, querySyncActionStats);
      } else {
        // TODO(mathewi) show an error?
        // or should we show multiple popups in parallel? (doesn't seem great if there are lots)
      }
    }
  }
}
