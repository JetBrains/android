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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectAttachProcessor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.android.tools.idea.gradle.util.GradleProjects.canImportAsGradleProject;
import static com.intellij.ide.actions.OpenFileAction.openFile;
import static com.intellij.ide.impl.ProjectUtil.*;
import static com.intellij.openapi.fileChooser.FileChooser.chooseFiles;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor;
import static com.intellij.openapi.fileChooser.impl.FileChooserUtil.setLastOpenedFile;
import static com.intellij.openapi.fileTypes.ex.FileTypeChooser.getKnownFileTypeOrAssociate;
import static com.intellij.openapi.vfs.VfsUtil.getUserHomeDir;

/**
 * Opens existing project or file in Android Stduio
 * This action replaces the default File -> Open action.
 */
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
    Project project = e.getProject();
    boolean showFiles = project != null || PlatformProjectOpenProcessor.getInstanceIfItExists() != null;
    FileChooserDescriptor descriptor = showFiles ? new ProjectOrFileChooserDescriptor() : new ProjectOnlyFileChooserDescriptor();
    descriptor.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, showFiles);

    VirtualFile explicitPreferredDirectory = ((project != null) && !project.isDefault()) ? project.getBaseDir() : getUserHomeDir();
    chooseFiles(descriptor, project, explicitPreferredDirectory, files -> {
      ValidationIssue issue = validateFiles(files, descriptor);
      if (issue.result.getSeverity() != Validator.Severity.OK) {
        boolean isError = issue.result.getSeverity() == Validator.Severity.ERROR;
        String title = isError ? IdeBundle.message("title.cannot.open.project") : "Warning Opening Project";
        Messages.showInfoMessage(project, issue.result.getMessage(), title);
        if (isError) {
          return;
        }
      }
      doOpenFile(project, files);
    });
  }

  /**
   * Checks the list of files passes validation. Returns null if there are no issues.
   */
  @VisibleForTesting
  @NotNull
  static ValidationIssue validateFiles(List<VirtualFile> files, FileChooserDescriptor descriptor) {
    for (VirtualFile file : files) {
      if (!descriptor.isFileSelectable(file)) {
        Validator.Result result =
          new Validator.Result(Validator.Severity.ERROR, AndroidBundle.message("title.cannot.open.file", file.getPresentableUrl()));
        return new ValidationIssue(result, file);
      }
    }
    return new ValidationIssue(Validator.Result.OK, null);
  }

  private static void doOpenFile(@Nullable Project project, @NotNull List<VirtualFile> result) {
    for (VirtualFile file : result) {
      if (file.isDirectory()) {
        // proceed with opening as a directory only if the pointed directory is not the base one
        // for the current project. The check is similar to what is done below for file-based projects
        if ((project != null) && !project.isDefault() && file.equals(project.getBaseDir())) {
          focusProjectWindow(project, false);
          return;
        }
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
      if (type == null) {
        return;
      }

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
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length > 0) {
          int exitCode = confirmOpenNewProject(false);
          if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
            Project toClose = ((project != null) && !project.isDefault()) ? project : openProjects[openProjects.length - 1];
            if (!closeAndDispose(toClose)) {
              return false;
            }
          }
          else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
            return false;
          }
        }

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

  /**
   * Returned by validateFiles after validating a project if there is an issue.
   */
  @VisibleForTesting
  static final class ValidationIssue {
    @NotNull Validator.Result result;
    @Nullable VirtualFile file;

    public ValidationIssue(@NotNull Validator.Result result, @Nullable VirtualFile file) {
      this.result = result;
      this.file = file;
    }
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
