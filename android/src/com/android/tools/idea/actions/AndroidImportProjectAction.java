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
import com.android.tools.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.eclipse.AdtImportBuilder;
import com.android.tools.idea.gradle.eclipse.AdtImportProvider;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

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
  @NonNls private static final String ANDROID_NATURE_NAME = "com.android.ide.eclipse.adt.AndroidNature";

  private static final Logger LOG = Logger.getInstance(AndroidImportProjectAction.class);

  @NonNls static final String ECLIPSE_CLASSPATH_FILE_NAME = ".classpath";
  @NonNls static final String ECLIPSE_PROJECT_FILE_NAME = ".project";

  private static final String WIZARD_TITLE = "Select Gradle Project Import";
  private static final String WIZARD_DESCRIPTION = "Select build.gradle or settings.gradle";

  private final boolean myIsProjectImport;

  public AndroidImportProjectAction(boolean isProjectImport) {
    super(isProjectImport ? "Import Project..." : "Import Module...");
    this.myIsProjectImport = isProjectImport;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = !myIsProjectImport ? getEventProject(e) : null;
    try {
      AddModuleWizard wizard = selectFileAndCreateWizard(project);
      if (wizard != null) {
        if (wizard.getStepCount() > 0) {
          if (!wizard.showAndGet()) {
            return;
          }
          //noinspection ConstantConditions
          NewProjectUtil.createFromWizard(wizard, null);
        }
      }
    }
    catch (IOException exception) {
      handleImportException(project, exception);
    }
    catch (ConfigurationException exception) {
      handleImportException(project, exception);
    }
  }

  private void handleImportException(@Nullable Project project, @NotNull Exception e1) {
    String projectOrModule = myIsProjectImport ? "Project" : "Module";
    String message = String.format("%s import failed: %s", projectOrModule, e1.getMessage());
    Messages.showErrorDialog(project, message, String.format("Import %s", projectOrModule));
    LOG.error(e1);
  }

  @Nullable
  private static AddModuleWizard selectFileAndCreateWizard(@Nullable Project project) throws IOException, ConfigurationException {
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
    String description = project == null ? WIZARD_DESCRIPTION : ImportModuleAction.getFileChooserDescription(project);
    descriptor.setDescription(description);
    return selectFileAndCreateWizard(project, descriptor);
  }

  @Nullable
  private static AddModuleWizard selectFileAndCreateWizard(@Nullable Project project, @NotNull FileChooserDescriptor descriptor)
      throws IOException, ConfigurationException {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);
    VirtualFile toSelect = null;
    String lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(toSelect, null);
    if (files.length == 0) {
      return null;
    }
    VirtualFile file = files[0];
    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.getPath());
    return createImportWizard(project, file);
  }

  @Nullable
  private static AddModuleWizard createImportWizard(@Nullable Project project, @NotNull VirtualFile file)
    throws IOException, ConfigurationException {
    //noinspection TestOnlyProblems
    VirtualFile target = findImportTarget(file);
    if (target == null) {
      return null;
    }
    List<ProjectImportProvider> available = Lists.newArrayList();

    // Prioritize ADT importer
    VirtualFile targetDir = target.isDirectory() ? target : target.getParent();
    File targetDirFile = VfsUtilCore.virtualToIoFile(targetDir);
    if (GradleImport.isAdtProjectDir(targetDirFile)
        && targetDir.findChild(SdkConstants.FN_BUILD_GRADLE) == null) {
      importAdtProject(file, project);
      return null;
    }
    if (GradleImport.isEclipseProjectDir(targetDirFile)
        && targetDir.findChild(SdkConstants.FN_BUILD_GRADLE) == null) {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          Messages.showErrorDialog(String.format(
            "%1$s is an Eclipse project, but not an Android Eclipse project.\n\n" +
            "Please select the directory of an Android Eclipse project (which for example will contain\n" +
            "an AndroidManifest.xml file) and try again.",
            targetDirFile.getPath()), "Import Project");
        }
      return null;
    }

    if (GradleConstants.EXTENSION.equals(target.getExtension())) {
      // Gradle file, we handle this ourselves.
      if (project == null) {
        GradleProjectImporter gradleImporter = GradleProjectImporter.getInstance();
        gradleImporter.importProject(file);
      } else {
        importGradleProjectAsModule(project, file);
      }
      return null;
    }

    for (ProjectImportProvider provider : ProjectImportProvider.PROJECT_IMPORT_PROVIDER.getExtensions()) {
      if (provider.canImport(target, project)) {
        available.add(provider);
      }
    }
    if (available.isEmpty()) {
      Messages.showInfoMessage(project, "Cannot import anything from " + file.getPath(), "Cannot Import");
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
    return new AddModuleWizard(project, path, availableProviders);
  }

  private static void importGradleProjectAsModule(Project project, VirtualFile file)
      throws IOException, ConfigurationException {
    GradleProjectImporter importer = GradleProjectImporter.getInstance();
    Map<String, VirtualFile> projectsToImport = importer.getRelatedProjects(file, project);
    importer.importModules(projectsToImport, project, null);
  }

  @VisibleForTesting
  @Nullable
  static VirtualFile findImportTarget(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      VirtualFile target = findMatchingChild(file, SdkConstants.FN_BUILD_GRADLE, SdkConstants.FN_SETTINGS_GRADLE);
      if (target != null) {
        return target;
      }
      target = findMatchingChild(file, ECLIPSE_PROJECT_FILE_NAME);
      if (target != null) {
        return findImportTarget(target);
      }
    }
    else {
      if (ECLIPSE_PROJECT_FILE_NAME.equals(file.getName()) && hasAndroidNature(file)) {
        return file;
      }
      if (ECLIPSE_CLASSPATH_FILE_NAME.equals(file.getName())) {
        return findImportTarget(file.getParent());
      }
    }
    return file;
  }

  @Nullable
  private static VirtualFile findMatchingChild(@NotNull VirtualFile parent, @NotNull String... validNames) {
    if (parent.isDirectory()) {
      for (VirtualFile child : parent.getChildren()) {
        for (String name : validNames) {
          if (name.equals(child.getName())) {
            return child;
          }
        }
      }
    }
    return null;
  }

  private static boolean hasAndroidNature(@NotNull VirtualFile projectFile) {
    File dotProjectFile = new File(projectFile.getPath());
    try {
      Element naturesElement = JDOMUtil.loadDocument(dotProjectFile).getRootElement().getChild("natures");
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


  private static void importAdtProject(@NotNull VirtualFile file, @Nullable Project project) {
    AdtImportProvider adtImportProvider = new AdtImportProvider(project == null);
    if (project != null) {
      ((AdtImportBuilder)adtImportProvider.getBuilder()).setSelectedProject(VfsUtilCore.virtualToIoFile(file));
    }
    AddModuleWizard wizard = new AddModuleWizard(project, ProjectImportProvider.getDefaultPath(file), adtImportProvider);
    if (wizard.showAndGet()) {
      try {
        doCreate(wizard, project);
      }
      catch (final IOException e) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(e.getMessage(), "Project Initialization Failed");
          }
        });
      }
    }
  }

  private static void doCreate(@NotNull AddModuleWizard wizard, @Nullable Project project) throws IOException {
    // TODO: Now we need to add as module if file does not exist
    ProjectBuilder projectBuilder = wizard.getProjectBuilder();

    try {
      File projectFilePath = new File(wizard.getNewProjectFilePath());
      File projectDirPath = projectFilePath.isDirectory() ? projectFilePath : projectFilePath.getParentFile();
      LOG.assertTrue(projectDirPath != null, "Cannot create project in '" + projectFilePath + "': no parent file exists");
      FileUtil.ensureExists(projectDirPath);

      if (StorageScheme.DIRECTORY_BASED == wizard.getStorageScheme()) {
        File ideaDirPath = new File(projectDirPath, Project.DIRECTORY_STORE_FOLDER);
        FileUtil.ensureExists(ideaDirPath);
      }

      boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      if (project == null) {
        ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
        project = projectManager.newProject(wizard.getProjectName(), projectDirPath.getPath(), true, false);
        if (project == null) {
          return;
        }
        if (!unitTestMode) {
          project.save();
        }
      }
      if (projectBuilder != null) {
        if (!projectBuilder.validate(null, project)) {
          return;
        }
        projectBuilder.commit(project, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
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

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(myIsProjectImport || getEventProject(e) != null);
  }
}
