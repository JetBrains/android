/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.eclipse.AdtImportProvider;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.eclipse.GradleImport.isEclipseProjectDir;
import static com.android.tools.idea.gradle.project.AdtModuleImporter.isAdtProjectLocation;
import static com.android.tools.idea.gradle.util.Projects.canImportAsGradleProject;
import static com.android.tools.idea.gradle.project.ProjectImportUtil.findImportTarget;
import static com.intellij.ide.impl.NewProjectUtil.createFromWizard;
import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static com.intellij.openapi.roots.ui.configuration.ModulesProvider.EMPTY_MODULES_PROVIDER;
import static com.intellij.openapi.util.io.FileUtil.ensureExists;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;
import static java.util.Collections.emptyList;

/**
 * Imports a new project into Android Studio.
 * <p/>
 * This action replaces the default "Import Project..." changing the behavior of project imports. If the user selects a project's root
 * directory of a Gradle project, this action will detect that the project is a Gradle project and it will direct the user to the Gradle
 * "Import Project" wizard, instead of the intermediate wizard where users can choose to import a project from existing sources. This has
 * been a source of confusion for our users.
 * <p/>
 * The code in the original action cannot be extended or reused. It is implemented using static methods and the method where we change the
 * behavior is at the bottom of the call chain.
 */
public class AndroidImportProjectAction extends AnAction {
  @NonNls private static final String LAST_IMPORTED_LOCATION = "last.imported.location";
  private static final Logger LOG = Logger.getInstance(AndroidImportProjectAction.class);

  private static final String WIZARD_TITLE = "Select Eclipse or Gradle Project to Import";
  private static final String WIZARD_DESCRIPTION = "Select your Eclipse project folder, build.gradle or settings.gradle";

  public AndroidImportProjectAction() {
    this("Import Project...");
  }

  public AndroidImportProjectAction(@NotNull String text) {
    super(text);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.Welcome.ImportProject);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    try {
      AddModuleWizard wizard = selectFileAndCreateWizard();
      if (wizard != null) {
        if (wizard.getStepCount() > 0) {
          if (!wizard.showAndGet()) {
            return;
          }
          //noinspection ConstantConditions
          createFromWizard(wizard, null);
        }
      }
    }
    catch (IOException exception) {
      handleImportException(e.getProject(), exception);
    }
    catch (ConfigurationException exception) {
      handleImportException(e.getProject(), exception);
    }
  }

  private static void handleImportException(@Nullable Project project, @NotNull Exception e) {
    String message = String.format("Project import failed: %s", e.getMessage());
    Messages.showErrorDialog(project, message, "Import Project");
    LOG.error(e);
  }

  @NotNull
  protected FileChooserDescriptor createFileChooserDescriptor() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, false) {
      FileChooserDescriptor myDelegate = new OpenProjectFileChooserDescriptor(true);

      @Override
      public Icon getIcon(VirtualFile file) {
        Icon icon = myDelegate.getIcon(file);
        return icon == null ? super.getIcon(file) : icon;
      }
    };
    descriptor.setHideIgnored(false);
    descriptor.setTitle(WIZARD_TITLE);
    descriptor.setDescription(WIZARD_DESCRIPTION);
    return descriptor;
  }

  @Nullable
  private AddModuleWizard selectFileAndCreateWizard() throws IOException, ConfigurationException {
    return selectFileAndCreateWizard(createFileChooserDescriptor());
  }

  @Nullable
  private AddModuleWizard selectFileAndCreateWizard(@NotNull FileChooserDescriptor descriptor)
      throws IOException, ConfigurationException {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);
    VirtualFile toSelect = null;
    String lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(null, toSelect);
    if (files.length == 0) {
      return null;
    }
    VirtualFile file = files[0];
    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.getPath());
    return createImportWizard(file);
  }

  @Nullable
  protected AddModuleWizard createImportWizard(@NotNull VirtualFile file) throws IOException, ConfigurationException {
    VirtualFile target = findImportTarget(file);
    if (target == null) {
      return null;
    }
    VirtualFile targetDir = target.isDirectory() ? target : target.getParent();
    File targetDirFile = VfsUtilCore.virtualToIoFile(targetDir);

    if (isAdtProjectLocation(file)) {
      importAdtProject(file);
    }
    else if (isEclipseProjectDir(targetDirFile) &&
             targetDir.findChild(SdkConstants.FN_BUILD_GRADLE) == null &&
             !ApplicationManager.getApplication().isUnitTestMode()) {
      String message = String.format("%1$s is an Eclipse project, but not an Android Eclipse project.\n\n" +
                                     "Please select the directory of an Android Eclipse project" +
                                     "(which for example will contain\nan AndroidManifest.xml file) and try again.", file.getPath());
      Messages.showErrorDialog(message, "Import Project");
    }
    else if (canImportAsGradleProject(target)) {
      GradleProjectImporter gradleImporter = GradleProjectImporter.getInstance();
      gradleImporter.importProject(file);
    }
    else {
      return importWithExtensions(file);
    }
    return null;
  }

  @Nullable
  private static AddModuleWizard importWithExtensions(@NotNull VirtualFile file) {
    List<ProjectImportProvider> available = getImportProvidersForTarget(file);
    if (available.isEmpty()) {
      Messages.showInfoMessage((Project) null, "Cannot import anything from " + file.getPath(), "Cannot Import");
      return null;
    }
    String path;
    if (available.size() == 1) {
      path = available.get(0).getPathToBeImported(file);
    }
    else {
      path = ProjectImportProvider.getDefaultPath(file);
    }

    ProjectImportProvider[] availableProviders = available.toArray(new ProjectImportProvider[available.size()]);
    return new AddModuleWizard(null, path, availableProviders);
  }

  @NotNull
  private static List<ProjectImportProvider> getImportProvidersForTarget(@NotNull VirtualFile file) {
    VirtualFile target = findImportTarget(file);
    if (target == null) {
      return emptyList();
    }
    else {
      List<ProjectImportProvider> available = Lists.newArrayList();
      for (ProjectImportProvider provider : ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions()) {
        if (provider.canImport(target, null)) {
          available.add(provider);
        }
      }
      return available;
    }
  }

  private static void importAdtProject(@NotNull VirtualFile file) {
    AdtImportProvider adtImportProvider = new AdtImportProvider(true);
    AddModuleWizard wizard = new AddModuleWizard(null, ProjectImportProvider.getDefaultPath(file), adtImportProvider);
    if (wizard.showAndGet()) {
      try {
        doCreate(wizard);
      }
      catch (final IOException e) {
        invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(e.getMessage(), "Project Initialization Failed");
          }
        });
      }
    }
  }

  private static void doCreate(@NotNull AddModuleWizard wizard) throws IOException {
    // TODO: Now we need to add as module if file does not exist
    ProjectBuilder projectBuilder = wizard.getProjectBuilder();

    try {
      File projectFilePath = new File(wizard.getNewProjectFilePath());
      File projectDirPath = projectFilePath.isDirectory() ? projectFilePath : projectFilePath.getParentFile();
      LOG.assertTrue(projectDirPath != null, "Cannot create project in '" + projectFilePath + "': no parent file exists");
      ensureExists(projectDirPath);

      if (StorageScheme.DIRECTORY_BASED == wizard.getStorageScheme()) {
        File ideaDirPath = new File(projectDirPath, DIRECTORY_STORE_FOLDER);
        ensureExists(ideaDirPath);
      }

      boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      Project project = projectManager.newProject(wizard.getProjectName(), projectDirPath.getPath(), true, false);
      if (project == null) {
        return;
      }
      if (!unitTestMode) {
        project.save();
      }
      if (projectBuilder != null) {
        if (!projectBuilder.validate(null, project)) {
          return;
        }
        projectBuilder.commit(project, null, EMPTY_MODULES_PROVIDER);
      }
      if (!unitTestMode) {
        project.save();
      }
    }
    finally {
      if (projectBuilder != null) {
        projectBuilder.cleanup();
      }
    }
  }
}
