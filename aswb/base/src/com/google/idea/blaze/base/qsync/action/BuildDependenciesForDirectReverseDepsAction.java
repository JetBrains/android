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

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper.DepsBuildType;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Optional;

/** Action to build dependencies and enable analysis for a file and it's reverse dependencies */
public class BuildDependenciesForDirectReverseDepsAction extends BlazeProjectAction {

  private static final String NAME = BuildDependenciesAction.NAME + " for Reverse Dependencies";

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    if (e.getData(CommonDataKeys.VIRTUAL_FILE) == null) {
      e.getPresentation().setEnabled(false);
    }
    if (e.getPlace().equals(ActionPlaces.MAIN_MENU)) {
      e.getPresentation().setText(NAME + " of Current File");
    } else {
      e.getPresentation().setText(NAME);
    }
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (virtualFile == null) {
      return;
    }

    BuildDependenciesHelper helper = createHelper(project);
    if (!helper.canEnableAnalysisNow()) {
      return;
    }

    Optional<Path> relativePath = helper.getRelativePathToEnableAnalysisFor(virtualFile);
    if (relativePath.isEmpty()) {
      return;
    }
    helper.enableAnalysis(getClass(), e, PopupPositioner.showAtMousePointerOrCentered(e));
  }

  private BuildDependenciesHelper createHelper(Project project) {
    return new BuildDependenciesHelper(project, DepsBuildType.REVERSE_DEPS);
  }
}
