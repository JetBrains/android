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
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper.DepsBuildType;
import com.google.idea.blaze.common.Context;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.BreakIterator;
import com.intellij.icons.AllIcons.Actions;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Action to build dependencies and enable analysis.
 *
 * <p>It can operate on a source file, BUILD file or package. See {@link
 * com.google.idea.blaze.qsync.project.BuildGraphData#getProjectTargets(Context, Path)} for a
 * description of what targets dependencies aren built for in each case.
 */
public class BuildDependenciesAction extends BlazeProjectAction {

  static final String NAME =
      UCharacter.toTitleCase(
          Locale.US, QuerySync.BUILD_DEPENDENCIES_ACTION_NAME, BreakIterator.getWordInstance());

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
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setIcon(Actions.Compile);
    if (e.getPlace().equals(ActionPlaces.MAIN_MENU)) {
      presentation.setText(NAME + " for Current File");
    } else {
      presentation.setText(NAME);
    }
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    BuildDependenciesHelper helper = new BuildDependenciesHelper(project, DepsBuildType.SELF);
    Optional<Path> relativePath = helper.getRelativePathToEnableAnalysisFor(virtualFile);
    if (relativePath.isEmpty()) {
      presentation.setEnabled(false);
      return;
    }
    if (!helper.canEnableAnalysisNow()) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(true);
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    BuildDependenciesHelper helper = new BuildDependenciesHelper(project, DepsBuildType.SELF);
    helper.enableAnalysis(getClass(), e, PopupPositioner.showAtMousePointerOrCentered(e));
  }
}
