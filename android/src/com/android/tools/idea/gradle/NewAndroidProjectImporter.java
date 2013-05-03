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
package com.android.tools.idea.gradle;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Imports a new Android project without showing the "Import Project" Wizard UI.
 */
public class NewAndroidProjectImporter {
  private static final Logger LOG = Logger.getInstance(NewAndroidProjectImporter.class);
  private static final ProjectSystemId SYSTEM_ID = GradleConstants.SYSTEM_ID;

  private final GradleProjectImporter myImporter;

  public NewAndroidProjectImporter() {
    myImporter = new GradleProjectImporter();
  }

  @VisibleForTesting
  NewAndroidProjectImporter(GradleProjectImporter importer) {
    myImporter = importer;
  }

  /**
   * Imports and opens the newly created Android project.
   *
   * @param projectName    name of the project.
   * @param projectRootDir root directory of the project.
   * @param androidSdk     Android SDK to set.
   * @return the imported IDEA project.
   * @throws IOException            if any file I/O operation fails (e.g. creating the '.idea' directory.)
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public Project importProject(@NotNull String projectName, @NotNull File projectRootDir, @NotNull Sdk androidSdk)
    throws IOException, ConfigurationException {
    File projectFile = new File(projectRootDir, "build.gradle");
    FileUtilRt.createIfNotExists(projectFile);
    String projectFilePath = projectFile.getAbsolutePath();

    createIdeaProjectDir(projectRootDir);

    final Project newProject = createProject(projectName, projectFilePath);
    setUpProject(newProject, androidSdk);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    File localProperties = new File(projectRootDir, "local.properties");
    FileUtilRt.createIfNotExists(localProperties);
    FileUtil.writeToFile(localProperties, "sdk.dir=" + androidSdk.getHomePath());

    DataNode<ProjectData> projectInfo = myImporter.importProject(newProject, projectFilePath);
    populateProject(newProject, projectInfo);

    open(newProject, projectFilePath);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        CompilerManager.getInstance(newProject).make(null);
      }
    });
    return newProject;
  }

  private static void createIdeaProjectDir(@NotNull File projectRootDir) throws IOException {
    File ideaDir = new File(projectRootDir, Project.DIRECTORY_STORE_FOLDER);
    FileUtil.ensureExists(ideaDir);
  }

  @NotNull
  private static Project createProject(@NotNull String projectName, @NotNull String projectFilePath) throws ConfigurationException {
    GradleSettings defaultSettings = getDefaultGradleSettings();

    String gradleHomePath = defaultSettings.getGradleHome();
    if (Strings.isNullOrEmpty(gradleHomePath)) {
      throw new ConfigurationException("Please specify the path of your Gradle installation");
    }

    Project newProject = ProjectManager.getInstance().createProject(projectName, projectFilePath);
    if (newProject == null) {
      throw new NullPointerException("Failed to create a new IDEA project");
    }
    GradleSettings settings = GradleSettings.getInstance(newProject);
    settings.applySettings(projectFilePath, gradleHomePath, defaultSettings.isPreferLocalInstallationToWrapper(),
                           defaultSettings.isUseAutoImport(), defaultSettings.getServiceDirectoryPath());
    return newProject;
  }

  private static GradleSettings getDefaultGradleSettings() {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    return GradleSettings.getInstance(defaultProject);
  }

  private static void setUpProject(@NotNull final Project newProject, @NotNull final Sdk androidSdk) {
    CommandProcessor.getInstance().executeCommand(newProject, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            NewProjectUtil.applyJdkToProject(newProject, androidSdk);
            // In practice, it really does not matter where the compiler output folder is. Gradle handles that. This is done just to please
            // IDEA.
            String compileOutputUrl = VfsUtilCore.pathToUrl(newProject.getBasePath() + "/build/classes");
            CompilerProjectExtension.getInstance(newProject).setCompilerOutputUrl(compileOutputUrl);
          }
        });
      }
    }, null, null);
  }

  private static void populateProject(@NotNull final Project newProject, @NotNull final DataNode<ProjectData> projectInfo) {
    System.setProperty(ExternalSystemConstants.NEWLY_IMPORTED_PROJECT, Boolean.TRUE.toString());
    StartupManager.getInstance(newProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        ExternalSystemApiUtil.executeProjectChangeAction(newProject, SYSTEM_ID, newProject, new Runnable() {
          @Override
          public void run() {
            ProjectRootManagerEx.getInstanceEx(newProject).mergeRootsChangesDuring(new Runnable() {
              @Override
              public void run() {
                Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.findAll(projectInfo, ProjectKeys.MODULE);
                ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);
                dataManager.importData(ProjectKeys.MODULE, modules, newProject, true);
              }
            });
          }
        });
      }
    });
  }

  private static void open(@NotNull Project newProject, @NotNull String projectFilePath) {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    ProjectUtil.updateLastProjectLocation(projectFilePath);
    if (WindowManager.getInstance().isFullScreenSupportedInCurrentOS()) {
      IdeFocusManager instance = IdeFocusManager.findInstance();
      IdeFrame lastFocusedFrame = instance.getLastFocusedFrame();
      if (lastFocusedFrame instanceof IdeFrameEx) {
        boolean fullScreen = ((IdeFrameEx)lastFocusedFrame).isInFullScreen();
        if (fullScreen) {
          newProject.putUserData(IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN, Boolean.TRUE);
        }
      }
    }
    projectManager.openProject(newProject);
  }

  // Makes it possible to mock invocations to the Gradle Tooling API.
  static class GradleProjectImporter {
    @NotNull
    DataNode<ProjectData> importProject(@NotNull Project newProject, @NotNull String projectFilePath)
      throws ConfigurationException {
      String externalSystemName = ExternalSystemApiUtil.toReadableName(SYSTEM_ID);
      Ref<String> errorMessage = new Ref<String>();
      Ref<String> errorDetails = new Ref<String>();
      try {
        DataNode<ProjectData> projectInfo =
          ExternalSystemUtil.refreshProject(newProject, SYSTEM_ID, projectFilePath, errorMessage, errorDetails, true, true);
        if (projectInfo != null) {
          return projectInfo;
        }
      }
      catch (RuntimeException e) {
        throw new ConfigurationException(e.getMessage(), ExternalSystemBundle.message("error.cannot.parse.project", externalSystemName));
      }
      final String details = errorDetails.get();
      if (!Strings.isNullOrEmpty(details)) {
        LOG.warn(details);
      }
      String msg;
      String reason = errorMessage.get();
      if (reason == null) {
        msg = ExternalSystemBundle.message("error.resolve.generic.without.reason", externalSystemName, projectFilePath);
      }
      else {
        msg = ExternalSystemBundle.message("error.resolve.with.reason", reason);
      }
      throw new ConfigurationException(msg, ExternalSystemBundle.message("error.resolve.generic"));
    }
  }
}
