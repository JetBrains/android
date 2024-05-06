/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.adtimport.actions;

import static com.android.tools.idea.gradle.util.GradleProjects.canImportAsGradleProject;
import static com.intellij.ide.impl.NewProjectUtil.createFromWizard;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.actions.OpenProjectFileChooserDescriptorWithAsyncIcon;
import com.android.tools.idea.gradle.project.ProjectImportUtil;
import com.android.tools.idea.ui.validation.validators.ProjectImportPathValidator;
import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.actions.ImportProjectAction;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import java.io.IOException;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Imports a new project into Android Studio.
 * <p/>
 * This action replaces the default "Import Project..." changing the behavior of project imports. If the user selects a project's root
 * directory of a Gradle project, this action will detect that the project is a Gradle project and it will open that project directly,
 * instead of providing a wizard where users can choose to import a project from existing sources. This has
 * been a source of confusion for our users.
 * <p/>
 * The code in the original action cannot be extended or reused. It is implemented using static methods and the method where we change the
 * behavior is in the middle of the call chain; we can re-use some parts underneath.
 */
public class AndroidImportProjectAction extends AnAction {
  @NonNls private static final String LAST_IMPORTED_LOCATION = "last.imported.location";
  private static final Logger LOG = Logger.getInstance(AndroidImportProjectAction.class);

  private static final String WIZARD_TITLE = "Select Eclipse or Gradle Project to Import";
  private static final String WIZARD_DESCRIPTION = "Select your Eclipse project folder, build.gradle or settings.gradle";

  public AndroidImportProjectAction(@Nullable @NlsActions.ActionText String text,
                                    @Nullable @NlsActions.ActionDescription String description,
                                    @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Disposable wizardDisposable = Disposer.newDisposable();
    try {
      AddModuleWizard wizard = selectFileAndCreateWizard(wizardDisposable, e.getProject());
      if (wizard != null) {
        if (wizard.getStepCount() > 0) {
          if (!wizard.showAndGet()) {
            return;
          }
          createFromWizard(wizard, null);
        }
      }
    }
    catch (IOException | ConfigurationException exception) {
      handleImportException(e.getProject(), exception);
    } finally {
      Disposer.dispose(wizardDisposable);
    }
  }

  private static void handleImportException(@Nullable Project project, @NotNull Exception e) {
    String message = String.format("Project import failed: %s", e.getMessage());
    Messages.showErrorDialog(project, message, "Import Project");
    LOG.error(e);
  }

  @NotNull
  protected FileChooserDescriptor createFileChooserDescriptor(Disposable wizardDisposable) {
    OpenProjectFileChooserDescriptorWithAsyncIcon delegate = new OpenProjectFileChooserDescriptorWithAsyncIcon();
    Disposer.register(wizardDisposable, delegate);
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, false) {
      @Override
      public Icon getIcon(VirtualFile file) {
        Icon icon = delegate.getIcon(file);
        return icon == null ? super.getIcon(file) : icon;
      }
    };
    descriptor.setHideIgnored(false);
    descriptor.setTitle(WIZARD_TITLE);
    descriptor.setDescription(WIZARD_DESCRIPTION);
    return descriptor;
  }

  @Nullable
  private AddModuleWizard selectFileAndCreateWizard(Disposable disposable, @Nullable Project project) throws IOException, ConfigurationException {
    return selectFileAndCreateWizard(project, createFileChooserDescriptor(disposable));
  }

  @Nullable
  private AddModuleWizard selectFileAndCreateWizard(@Nullable Project project, @NotNull FileChooserDescriptor descriptor)
    throws IOException, ConfigurationException {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);
    VirtualFile toSelect = null;
    String lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(project, toSelect);
    if (files.length == 0) {
      return null;
    }
    VirtualFile file = files[0];
    if (!isSelectedFileValid(project, file)) return null;

    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.getPath());
    return createImportWizard(file);
  }

  private static boolean isSelectedFileValid(@Nullable Project project, @NotNull VirtualFile file) {
    ProjectImportPathValidator validator = new ProjectImportPathValidator("project file");
    Validator.Result result = validator.validate(file.toNioPath());
    if (result.getSeverity() != Validator.Severity.OK) {
      boolean isError = result.getSeverity() == Validator.Severity.ERROR;
      Messages.showInfoMessage(project, result.getMessage(), isError ? "Cannot Import Project" : "Project Import Warning");
      if (isError) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  protected AddModuleWizard createImportWizard(@NotNull VirtualFile file) {
    VirtualFile target = findImportTarget(file);
    VirtualFile targetDir = target.isDirectory() ? target : target.getParent();

    if (canImportAsGradleProject(target)) {
      if (ProjectUtil.findAndFocusExistingProjectForPath(targetDir.toNioPath()) == null) {
        ProjectUtil.openOrImport(target.getPath(), null, true);
      }
    }
    else {
      List<ProjectImportProvider> providers = ImportModuleAction.getProviders(null);
      return ImportProjectAction.createImportWizard(null, null, file, providers.toArray(new ProjectImportProvider[0]));
    }
    return null;
  }

  @NotNull
  public static VirtualFile findImportTarget(@NotNull VirtualFile file) {
    VirtualFile gradleTarget = ProjectImportUtil.findGradleTarget(file);
    if (gradleTarget != null) {
      return gradleTarget;
    }

    return file;
  }
}
