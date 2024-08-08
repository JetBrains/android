/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.dependencies.AddSourceToProjectHelper.LocationContext;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.google.idea.blaze.base.settings.ui.AddDirectoryToProjectAction;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotifications;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/**
 * For files not covered by project directories / targets, finds the relevant BUILD target and
 * updates the .blazeproject 'targets' and 'directories' as required.
 */
class AddSourceToProjectAction extends BlazeProjectAction {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.HIDDEN;
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    String description = actionDescription(project, file);

    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(description != null);
    if (description != null) {
      presentation.setText(description);
    }
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (vf == null) {
      return;
    }
    LocationContext context = AddSourceToProjectHelper.getContext(project, vf);
    if (context == null) {
      return;
    }
    boolean addDirectories = !AddSourceToProjectHelper.sourceInProjectDirectories(context);
    boolean addTargets =
        !AddSourceToProjectHelper.sourceCoveredByProjectViewTargets(context)
            && !AddSourceToProjectHelper.autoDeriveTargets(project);
    if (!addDirectories && !addTargets) {
      // nothing to do
      return;
    }

    PsiFileSystemItem psiFile = findFileOrDirectory(PsiManager.getInstance(project), vf);
    if (psiFile instanceof PsiDirectory) {
      if (addDirectories) {
        AddDirectoryToProjectAction.runAction(project, new File(vf.getPath()));
      } else {
        Messages.showErrorDialog(
            project,
            "This directory is already included in the project view file's 'directories' section.",
            "Cannot add directory to project");
      }
      return;
    }

    if (!addTargets) {
      AddSourceToProjectHelper.addSourceAndTargetsToProject(
          project, context.workspacePath, ImmutableList.of());
      return;
    }

    if (psiFile instanceof BuildFile
        && ((BuildFile) psiFile).getBlazeFileType() == BlazeFileType.BuildPackage) {
      AddSourceToProjectHelper.addSourceAndTargetsToProject(
          project,
          context.workspacePath,
          ImmutableList.of(TargetExpression.allFromPackageNonRecursive(context.blazePackage)));
      return;
    }

    // otherwise find the targets building this source file, then add them to the project
    ListenableFuture<List<TargetInfo>> targetsFuture =
        AddSourceToProjectHelper.getTargetsBuildingSource(context);
    if (targetsFuture == null) {
      return;
    }
    targetsFuture.addListener(
        () -> {
          List<TargetInfo> targets = FuturesUtil.getIgnoringErrors(targetsFuture);
          if (!addDirectories && (targets == null || targets.isEmpty())) {
            Messages.showWarningDialog(
                project,
                "Add source to project action failed",
                "No targets found building this source file");
          }
        },
        MoreExecutors.directExecutor());
    AddSourceToProjectHelper.addSourceToProject(
        project, context.workspacePath, !addDirectories, targetsFuture);
    // update editor notifications, to handle the case where the file is currently open.
    EditorNotifications.getInstance(project).updateNotifications(vf);
  }

  /**
   * Initial checks for whether this action should be enabled. Returns the relevant action string,
   * or null if the action shouldn't be shown.
   */
  @Nullable
  private static String actionDescription(Project project, @Nullable VirtualFile vf) {
    if (vf == null) {
      return null;
    }
    LocationContext context = AddSourceToProjectHelper.getContext(project, vf);
    if (context == null) {
      return null;
    }
    boolean addDirectories = !AddSourceToProjectHelper.sourceInProjectDirectories(context);
    boolean addTargets =
        !AddSourceToProjectHelper.sourceCoveredByProjectViewTargets(context)
            && !AddSourceToProjectHelper.autoDeriveTargets(project);
    if (!addDirectories && !addTargets) {
      // nothing to do
      return null;
    }
    PsiFileSystemItem psiFile = findFileOrDirectory(PsiManager.getInstance(project), vf);
    if (psiFile instanceof PsiDirectory) {
      return addDirectories ? "Add directory to project" : null;
    }

    if (psiFile instanceof BuildFile
        && ((BuildFile) psiFile).getBlazeFileType() == BlazeFileType.BuildPackage) {
      return AddSourceToProjectHelper.packageCoveredByProjectTargets(context)
          ? null
          : "Add BUILD package to project";
    }
    if (!SourceToTargetProvider.hasProvider()) {
      return null;
    }

    if (!addDirectories
        && (AddSourceToProjectHelper.sourceCoveredByProjectViewTargets(context)
            || SyncStatusContributor.getSyncStatus(project, context.file) != SyncStatus.UNSYNCED)) {
      return null;
    }
    return "Add source file to project";
  }

  private static PsiFileSystemItem findFileOrDirectory(PsiManager manager, VirtualFile vf) {
    return vf.isDirectory() ? manager.findDirectory(vf) : manager.findFile(vf);
  }
}
