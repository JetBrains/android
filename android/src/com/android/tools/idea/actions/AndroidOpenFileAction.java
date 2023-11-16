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

import static com.intellij.ide.actions.OpenFileAction.openFile;
import static com.intellij.ide.impl.ProjectUtil.focusProjectWindow;
import static com.intellij.ide.impl.ProjectUtil.openOrImport;
import static com.intellij.openapi.fileChooser.FileChooser.chooseFiles;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor;
import static com.intellij.openapi.fileChooser.impl.FileChooserUtil.setLastOpenedFile;
import static com.intellij.openapi.fileTypes.ex.FileTypeChooser.getKnownFileTypeOrAssociate;
import static com.intellij.openapi.vfs.VfsUtil.getUserHomeDir;

import com.android.tools.adtui.validation.Validator;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.GeneralLocalSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectAttachProcessor;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Opens existing project or file in Android Studio.
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
      e.getPresentation().setIcon(AllIcons.Welcome.Open);
      e.getPresentation().setSelectedIcon(AllIcons.Welcome.OpenSelected);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Disposable disposable = Disposer.newDisposable();
    try {
      Project project = e.getProject();
      boolean showFiles = project != null || PlatformProjectOpenProcessor.getInstanceIfItExists() != null;
      OpenProjectFileChooserDescriptorWithAsyncIcon descriptor =
        showFiles ? new ProjectOrFileChooserDescriptor() : new ProjectOnlyFileChooserDescriptor();
      descriptor.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, showFiles);
      Disposer.register(disposable, descriptor);

      VirtualFile explicitPreferredDirectory = ((project != null) && !project.isDefault()) ? project.getBaseDir() : null;
      if (explicitPreferredDirectory == null) {
        String defaultProjectDirectory = GeneralLocalSettings.getInstance().getDefaultProjectDirectory();
        if (StringUtil.isNotEmpty(defaultProjectDirectory)) {
          explicitPreferredDirectory = VfsUtil.findFileByIoFile(new File(defaultProjectDirectory), true);
        }
        else {
          explicitPreferredDirectory = getUserHomeDir();
        }
      }

      // The chooseFiles method shows a FileChooserDialog and it doesn't return control until
      // a user closes the dialog.
      // Note: this method is invoked from the main thread but chooseFiles uses a nested message
      // loop to avoid the IDE from freeze.
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
        doOpenFile(e, files);
      });
    } finally {
      Disposer.dispose(disposable);
    }
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

  private static void doOpenFile(@NotNull AnActionEvent e, @NotNull List<VirtualFile> result) {
    Project project = e.getProject();
    for (VirtualFile file : result) {
      if (file.isDirectory()) {
        // proceed with opening as a directory only if the pointed directory is not the base one
        // for the current project. The check is similar to what is done below for file-based projects
        if ((project != null) && !project.isDefault() && file.equals(project.getBaseDir())) {
          focusProjectWindow(project, false);
          continue;
        }
        if (ProjectAttachProcessor.canAttachToProject()) {
          Project openedProject = PlatformProjectOpenProcessor.doOpenProject(file, project, -1, null, EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class));
          setLastOpenedFile(openedProject, file.toNioPath());
        }
        else {
          openOrImportProject(file.toNioPath(), project);
        }
        continue;
      }

      // try to open as a project - unless the file is an .ipr of the current one
      if ((project == null || !file.equals(project.getProjectFile())) && OpenProjectFileChooserDescriptor.isProjectFile(file)) {
        if (openOrImportProject(file.toNioPath(), project)) {
          continue;
        }
      }

      FileType type = getKnownFileTypeOrAssociate(file, project);
      if (type == null) {
        continue;
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

  private static boolean openOrImportProject(@NotNull Path file, @Nullable Project project) {
    Project opened = openOrImport(file, project, false);
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

  private static class ProjectOnlyFileChooserDescriptor extends OpenProjectFileChooserDescriptorWithAsyncIcon {
    public ProjectOnlyFileChooserDescriptor() {
      setTitle(IdeBundle.message("title.open.project"));
    }
  }

  private static class ProjectOrFileChooserDescriptor extends OpenProjectFileChooserDescriptorWithAsyncIcon {
    private final FileChooserDescriptor myStandardDescriptor = createSingleFileNoJarsDescriptor().withHideIgnored(false);

    public ProjectOrFileChooserDescriptor() {
      setTitle(IdeBundle.message("title.open.file.or.project"));
    }

    @Override
    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      return file.isDirectory() ? super.isFileVisible(file, showHiddenFiles) : myStandardDescriptor.isFileVisible(file, showHiddenFiles);
    }

    @Override
    public boolean isFileSelectable(@Nullable VirtualFile file) {
      if (file == null) return false;
      return file.isDirectory() ? super.isFileSelectable(file) : myStandardDescriptor.isFileSelectable(file);
    }

    @Override
    public boolean isChooseMultiple() {
      return true;
    }
  }
}
