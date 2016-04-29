/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.actions;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.canImportAsGradleProject;
import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.intellij.ide.actions.OpenFileAction.openFile;
import static com.intellij.ide.impl.ProjectUtil.openOrImport;
import static com.intellij.openapi.fileChooser.FileChooser.chooseFiles;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor;
import static com.intellij.openapi.fileChooser.impl.FileChooserUtil.setLastOpenedFile;
import static com.intellij.openapi.fileTypes.ex.FileTypeChooser.getKnownFileTypeOrAssociate;
import static com.intellij.openapi.vfs.VfsUtil.getUserHomeDir;

public class AndroidOpenFileAction extends DumbAwareAction {
  public AndroidOpenFileAction() {
    this("Open...");
  }

  public AndroidOpenFileAction(@NotNull String text) {
    super(text);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.Welcome.OpenProject);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final boolean showFiles = project != null || PlatformProjectOpenProcessor.getInstanceIfItExists() != null;
    final FileChooserDescriptor descriptor = showFiles ? new ProjectOrFileChooserDescriptor() : new ProjectOnlyFileChooserDescriptor();
    descriptor.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, showFiles);

    chooseFiles(descriptor, project, getUserHomeDir(), new Consumer<List<VirtualFile>>() {
      @Override
      public void consume(final List<VirtualFile> files) {
        for (VirtualFile file : files) {
          if (!descriptor.isFileSelectable(file)) {
            String message = IdeBundle.message("error.dir.contains.no.project", file.getPresentableUrl());
            Messages.showInfoMessage(project, message, IdeBundle.message("title.cannot.open.project"));
            return;
          }
        }
        doOpenFile(project, files);
      }
    });

  }

  private static void doOpenFile(@Nullable Project project, @NotNull List<VirtualFile> result) {
    for (VirtualFile file : result) {
      if (file.isDirectory()) {
        if (ProjectAttachProcessor.canAttachToProject()) {
          Project openedProject = PlatformProjectOpenProcessor.doOpenProject(file, project, false, -1, null, false);
          setLastOpenedFile(openedProject, file);
        }
        else {
          openOrImportProject(file, project);
        }
        return;
      }

      // try to open as a project - unless the file is an .ipr of the current one
      if ((project == null || !file.equals(project.getProjectFile())) && OpenProjectFileChooserDescriptor.isProjectFile(file)) {
        if (openOrImportProject(file, project)) {
          return;
        }
      }

      FileType type = getKnownFileTypeOrAssociate(file, project);
      if (type == null) return;

      if (project != null) {
        openFile(file, project);
      }
      else {
        PlatformProjectOpenProcessor processor = PlatformProjectOpenProcessor.getInstanceIfItExists();
        if (processor != null) {
          processor.doOpenProject(file, null, false);
        }
      }
    }
  }

  private static boolean openOrImportProject(@NotNull VirtualFile file, @Nullable Project project) {
    if (canImportAsGradleProject(file)) {
      VirtualFile target = findImportTarget(file);
      if (target != null) {
        GradleProjectImporter gradleImporter = GradleProjectImporter.getInstance();
        gradleImporter.importProject(file);
        return true;
      }
    }
    Project opened = openOrImport(file.getPath(), project, false);
    if (opened != null) {
      setLastOpenedFile(opened, file);
      return true;
    }
    return false;
  }

  private static class ProjectOnlyFileChooserDescriptor extends OpenProjectFileChooserDescriptor {
    public ProjectOnlyFileChooserDescriptor() {
      super(true);
      setTitle(IdeBundle.message("title.open.project"));
    }
  }

  private static class ProjectOrFileChooserDescriptor extends OpenProjectFileChooserDescriptor {
    private final FileChooserDescriptor myStandardDescriptor = createSingleFileNoJarsDescriptor().withHideIgnored(false);

    public ProjectOrFileChooserDescriptor() {
      super(true);
      setTitle(IdeBundle.message("title.open.file.or.project"));
    }

    @Override
    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      return file.isDirectory() ? super.isFileVisible(file, showHiddenFiles) : myStandardDescriptor.isFileVisible(file, showHiddenFiles);
    }

    @Override
    public boolean isFileSelectable(VirtualFile file) {
      return file.isDirectory() ? super.isFileSelectable(file) : myStandardDescriptor.isFileSelectable(file);
    }

    @Override
    public boolean isChooseMultiple() {
      return true;
    }
  }

}
