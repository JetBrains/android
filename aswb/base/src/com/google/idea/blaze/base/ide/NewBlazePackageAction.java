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
package com.google.idea.blaze.base.ide;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateDirectoryOrPackageHandler;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import javax.annotation.Nullable;

class NewBlazePackageAction extends BlazeProjectAction implements DumbAware {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent event) {
    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      return;
    }
    PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory == null) {
      return;
    }
    CreateDirectoryOrPackageHandler validator =
        new CreateDirectoryOrPackageHandler(project, directory, false, ".") {
          @Override
          protected void createDirectories(String subDirName) {
            super.createDirectories(subDirName);
            PsiFileSystemItem element = getCreatedElement();
            if (element instanceof PsiDirectory) {
              createBuildFile(project, (PsiDirectory) element);
            }
          }
        };
    Messages.showInputDialog(
        project,
        "Enter new package name:",
        String.format("New %s Package", Blaze.buildSystemName(project)),
        Messages.getQuestionIcon(),
        "",
        validator);
    PsiDirectory newDir = (PsiDirectory) validator.getCreatedElement();
    if (newDir != null) {
      PsiFile buildFile = findBuildFile(project, newDir);
      if (buildFile != null) {
        view.selectElement(buildFile);
        OpenFileAction.openFile(buildFile.getViewProvider().getVirtualFile(), project);
      }
    }
  }

  @Nullable
  private static PsiFile findBuildFile(Project project, PsiDirectory parent) {
    String filename = Blaze.getBuildSystemProvider(project).possibleBuildFileNames().get(0);
    VirtualFile vf = parent.getVirtualFile().findChild(filename);
    return vf != null ? PsiManager.getInstance(project).findFile(vf) : null;
  }

  private static void createBuildFile(Project project, PsiDirectory parent) {
    String filename = Blaze.getBuildSystemProvider(project).possibleBuildFileNames().get(0);
    PsiFile file =
        PsiFileFactory.getInstance(project)
            .createFileFromText(filename, BuildFileType.INSTANCE, "");
    parent.add(file);
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    String buildSystem = Blaze.buildSystemName(project);
    presentation.setEnabledAndVisible(isEnabled(event));
    presentation.setText(String.format("%s Package", buildSystem));
    presentation.setDescription(String.format("Create a new %s package", buildSystem));
    presentation.setIcon(PlatformIcons.PACKAGE_ICON);
  }

  private boolean isEnabled(AnActionEvent event) {
    Project project = event.getProject();
    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (project == null || view == null) {
      return false;
    }
    return view.getDirectories().length != 0;
  }
}
