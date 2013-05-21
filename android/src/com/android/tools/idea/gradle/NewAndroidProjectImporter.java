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

import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.intellij.ProjectTopics;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
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
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
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
   * @param callback       called after the project has been imported.
   * @throws IOException            if any file I/O operation fails (e.g. creating the '.idea' directory.)
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public void importProject(@NotNull String projectName,
                            @NotNull File projectRootDir,
                            @NotNull Sdk androidSdk,
                            @Nullable final Callback callback) throws IOException, ConfigurationException {
    File projectFile = createTopLevelBuildFile(projectRootDir);
    final String projectFilePath = projectFile.getAbsolutePath();

    createIdeaProjectDir(projectRootDir);

    final Project newProject = createProject(projectName, projectFilePath);
    setUpProject(newProject, projectFilePath, androidSdk);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    createLocalPropertiesFile(projectRootDir, androidSdk);

    final Ref<ConfigurationException> error = new Ref<ConfigurationException>();

    myImporter.importProject(newProject, projectFilePath, new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(final @Nullable DataNode<ProjectData> projectInfo) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            populateProject(newProject, projectInfo);
            open(newProject, projectFilePath);

            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              newProject.save();
            }
          }
        });
      }

      @Override
      public void onFailure(@NotNull final String errorMessage, @Nullable String errorDetails) {
        if (errorDetails != null) {
          LOG.warn(errorDetails);
        }
        String reason = "Failed to import new Gradle project: " + errorMessage;
        error.set(new ConfigurationException(ExternalSystemBundle.message("error.resolve.with.reason", reason),
                                             ExternalSystemBundle.message("error.resolve.generic")));
      }
    });

    if (error.get() != null) {
      throw error.get();
    }

    // We need to compile and call Callback when we have a module in the new project.
    final MessageBusConnection connection = newProject.getMessageBus().connect();
    final String projectRootDirPath = projectRootDir.getAbsolutePath();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        Module[] modules = ModuleManager.getInstance(newProject).getModules();
        if (modules.length > 0) {
          connection.disconnect();
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            Projects.compile(newProject, projectRootDirPath);
          }
          if (callback != null) {
            callback.projectImported(newProject);
          }
        }
      }
    });
  }

  private static File createTopLevelBuildFile(File projectRootDir) throws IOException {
    File projectFile = new File(projectRootDir, "build.gradle");
    FileUtilRt.createIfNotExists(projectFile);
    String contents = "// Top-level build file where you can add configuration options common to all sub-projects/modules." +
                      SystemProperties.getLineSeparator();
    FileUtil.writeToFile(projectFile, contents);
    return projectFile;
  }

  private static void createIdeaProjectDir(@NotNull File projectRootDir) throws IOException {
    File ideaDir = new File(projectRootDir, Project.DIRECTORY_STORE_FOLDER);
    FileUtil.ensureExists(ideaDir);
  }

  @NotNull
  private static Project createProject(@NotNull String projectName, @NotNull String projectFilePath) throws ConfigurationException {
    ProjectManager projectManager = ProjectManager.getInstance();
    Project newProject = projectManager.createProject(projectName, projectFilePath);
    if (newProject == null) {
      throw new NullPointerException("Failed to create a new IDEA project");
    }
    return newProject;
  }

  private static void setUpProject(@NotNull final Project newProject,
                                   @NotNull final String projectFilePath,
                                   @NotNull final Sdk androidSdk) {
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
            setUpGradleSettings(newProject, projectFilePath);
          }
        });
      }
    }, null, null);
  }

  private static void setUpGradleSettings(@NotNull Project newProject, @NotNull String projectFilePath) {
    GradleSettings gradleSettings = GradleSettings.getInstance(newProject);
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setPreferLocalInstallationToWrapper(false);
    projectSettings.setExternalProjectPath(projectFilePath);
    projectSettings.setUseAutoImport(true);
    gradleSettings.setLinkedProjectsSettings(ImmutableList.of(projectSettings));
  }

  private static void createLocalPropertiesFile(@NotNull File projectRootDir, @NotNull Sdk androidSdk) throws IOException {
    File localProperties = new File(projectRootDir, "local.properties");
    FileUtilRt.createIfNotExists(localProperties);
    // TODO: create this file using a template and just populate the path of Android SDK.
    String[] lines = {
      "# This file is automatically generated by Android Studio.",
      "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!",
      "#",
      "# This file must *NOT* be checked into Version Control Systems,",
      "# as it contains information specific to your local configuration.",
      "",
      "# Location of the SDK. This is only used by Gradle.",
      "# For customization when using a Version Control System, please read the",
      "# header note.",
      "sdk.dir=" + androidSdk.getHomePath()
    };
    String contents = Joiner.on(SystemProperties.getLineSeparator()).join(lines);
    FileUtil.writeToFile(localProperties, contents);
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

  private static void open(@NotNull final Project newProject, @NotNull String projectFilePath) {
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
    void importProject(@NotNull Project newProject, @NotNull String projectFilePath, @NotNull ExternalProjectRefreshCallback callback)
      throws ConfigurationException {
      try {
        ExternalSystemUtil.refreshProject(newProject, SYSTEM_ID, projectFilePath, callback, true, true);
      }
      catch (RuntimeException e) {
        String externalSystemName = ExternalSystemApiUtil.toReadableName(SYSTEM_ID);
        throw new ConfigurationException(e.getMessage(), ExternalSystemBundle.message("error.cannot.parse.project", externalSystemName));
      }
    }
  }

  public interface Callback {
    /**
     * Invoked when a Gradle project has been imported. It is not guaranteed that the created IDEA project has been compiled.
     *
     * @param project the IDEA project created from the Gradle one.
     */
    void projectImported(@NotNull Project project);
  }
}
