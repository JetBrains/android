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

import static com.android.tools.idea.gradle.adtimport.GradleImport.isEclipseProjectDir;
import static com.android.tools.idea.gradle.adtimport.AdtModuleImporter.isAdtProjectLocation;
import static com.android.tools.idea.gradle.util.GradleProjects.canImportAsGradleProject;
import static com.android.utils.BuildScriptUtil.findGradleBuildFile;
import static com.intellij.ide.impl.NewProjectUtil.createFromWizard;
import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static com.intellij.openapi.roots.ui.configuration.ModulesProvider.EMPTY_MODULES_PROVIDER;
import static com.intellij.openapi.util.io.FileUtil.ensureExists;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.idea.actions.OpenProjectFileChooserDescriptorWithAsyncIcon;
import com.android.tools.idea.gradle.adtimport.AdtImportProvider;
import com.android.tools.idea.gradle.adtimport.GradleImport;
import com.android.tools.idea.gradle.project.ProjectImportUtil;
import com.android.tools.idea.ui.validation.validators.ProjectImportPathValidator;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.Disposable;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  @NonNls private static final String ANDROID_NATURE_NAME = "com.android.ide.eclipse.adt.AndroidNature";

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
    File targetDirFile = virtualToIoFile(targetDir);

    if (isAdtProjectLocation(file)) {
      importAdtProject(file);
    }
    else if (isEclipseProjectDir(targetDirFile) &&
             !findGradleBuildFile(targetDirFile).exists() &&
             !ApplicationManager.getApplication().isUnitTestMode()) {
      String message = String.format("%1$s is an Eclipse project, but not an Android Eclipse project.\n\n" +
                                     "Please select the directory of an Android Eclipse project" +
                                     "(which for example will contain\nan AndroidManifest.xml file) and try again.", file.getPath());
      Messages.showErrorDialog(message, "Import Project");
    }
    else if (canImportAsGradleProject(target)) {
      if (ProjectUtil.findAndFocusExistingProjectForPath(targetDir.toNioPath()) == null) {
        ProjectUtil.openOrImport(target.getPath(), null, true);
      }
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
      Messages.showInfoMessage((Project)null, "Cannot import anything from " + file.getPath(), "Cannot Import");
      return null;
    }
    String path;
    if (available.size() == 1) {
      path = available.get(0).getPathToBeImported(file);
    }
    else {
      path = ProjectImportProvider.getDefaultPath(file);
    }

    ProjectImportProvider[] availableProviders = available.toArray(new ProjectImportProvider[0]);
    return new AddModuleWizard(null, path, availableProviders);
  }

  @NotNull
  private static List<ProjectImportProvider> getImportProvidersForTarget(@NotNull VirtualFile file) {
    VirtualFile target = findImportTarget(file);
    List<ProjectImportProvider> available = new ArrayList<>();
    for (ProjectImportProvider provider : ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions()) {
      if (provider.canImport(target, null)) {
        available.add(provider);
      }
    }
    return available;
  }

  private static void importAdtProject(@NotNull VirtualFile file) {
    AdtImportProvider adtImportProvider = new AdtImportProvider(true);
    AddModuleWizard wizard = new AddModuleWizard(null, ProjectImportProvider.getDefaultPath(file), adtImportProvider);
    if (wizard.showAndGet()) {
      try {
        doCreate(wizard);
      }
      catch (final IOException e) {
        invokeLaterIfNeeded(() -> Messages.showErrorDialog(e.getMessage(), "Project Initialization Failed"));
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
        ensureExists(new File(projectDirPath, DIRECTORY_STORE_FOLDER));
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

  @NotNull
  public static VirtualFile findImportTarget(@NotNull VirtualFile file) {
    VirtualFile gradleTarget = ProjectImportUtil.findGradleTarget(file);
    if (gradleTarget != null) {
      return gradleTarget;
    }

    VirtualFile eclipseTarget = findEclipseTarget(file);
    if (eclipseTarget != null) {
      return eclipseTarget;
    }

    return file;
  }

  @Nullable
  private static VirtualFile findEclipseTarget(@NotNull VirtualFile file) {
    VirtualFile target;
    VirtualFile result = null;
    if (file.isDirectory()) {
      target = ProjectImportUtil.findMatch(file, GradleImport.ECLIPSE_DOT_PROJECT);
      if (target != null) {
        result = findImportTarget(target);
      }
    }
    else {
      if (GradleImport.ECLIPSE_DOT_PROJECT.equals(file.getName()) && hasAndroidNature(file)) {
        result = file;
      } else if (GradleImport.ECLIPSE_DOT_CLASSPATH.equals(file.getName())) {
        result = findImportTarget(file.getParent());
      }
    }
    return result;
  }

  public static boolean hasAndroidNature(@NotNull VirtualFile projectFile) {
    File dotProjectFile = new File(projectFile.getPath());
    try {
      Element naturesElement = JDOMUtil.load(dotProjectFile).getChild("natures");
      if (naturesElement != null) {
        List<Element> naturesList = naturesElement.getChildren("nature");
        for (Element nature : naturesList) {
          String natureName = nature.getText();
          if (ANDROID_NATURE_NAME.equals(natureName)) {
            return true;
          }
        }
      }
    }
    catch (Exception e) {
      LOG.info(String.format("Unable to get natures for Eclipse project file '%1$s", projectFile.getPath()), e);
    }
    return false;
  }
}
