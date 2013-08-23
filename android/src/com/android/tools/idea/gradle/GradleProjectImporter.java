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

import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.ProjectTopics;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Imports an Android-Gradle project without showing the "Import Project" Wizard UI.
 */
public class GradleProjectImporter {
  private static final Logger LOG = Logger.getInstance(GradleProjectImporter.class);
  private static final ProjectSystemId SYSTEM_ID = GradleConstants.SYSTEM_ID;

  private final ImporterDelegate myDelegate;

  @NotNull
  public static GradleProjectImporter getInstance() {
    return ServiceManager.getService(GradleProjectImporter.class);
  }

  public GradleProjectImporter() {
    myDelegate = new ImporterDelegate();
  }

  @VisibleForTesting
  GradleProjectImporter(ImporterDelegate delegate) {
    myDelegate = delegate;
  }

  /**
   * Re-imports an existing Android-Gradle project.
   *
   * @param project the given project. This method does nothing if the project is not an Android-Gradle project.
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public void reImportProject(@NotNull Project project) throws ConfigurationException {
    String gradleProjectFilePath = findGradleProjectFilePath(project);
    if (gradleProjectFilePath != null) {
      doImport(project, gradleProjectFilePath, null);
    }
  }

  @Nullable
  private static String findGradleProjectFilePath(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet androidFacet = Facets.getFirstFacet(module, AndroidFacet.ID);
      if (androidFacet == null || androidFacet.getIdeaAndroidProject() == null) {
        continue;
      }
      return androidFacet.getIdeaAndroidProject().getRootGradleProjectFilePath();
    }
    return null;
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
    GradleImportNotificationListener.attachToManager();
    File projectFile = createTopLevelBuildFile(projectRootDir);
    String projectFilePath = projectFile.getAbsolutePath();

    createIdeaProjectDir(projectRootDir);

    Project newProject = createProject(projectName, projectFilePath);
    setUpProject(newProject, projectFilePath, androidSdk);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    LocalProperties.createFile(newProject, androidSdk);

    doImport(newProject, projectFilePath, callback);
  }

  @NotNull
  private static File createTopLevelBuildFile(@NotNull File projectRootDir) throws IOException {
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
            CompilerProjectExtension compilerProjectExt = CompilerProjectExtension.getInstance(newProject);
            assert compilerProjectExt != null;
            compilerProjectExt.setCompilerOutputUrl(compileOutputUrl);
            setUpGradleSettings(newProject, projectFilePath);
          }
        });
      }
    }, null, null);
  }

  private static void setUpGradleSettings(@NotNull Project newProject, @NotNull String projectFilePath) {
    GradleSettings gradleSettings = GradleSettings.getInstance(newProject);
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DistributionType.WRAPPED);
    projectSettings.setExternalProjectPath(projectFilePath);
    projectSettings.setUseAutoImport(true);
    gradleSettings.setLinkedProjectsSettings(ImmutableList.of(projectSettings));
  }

  private void doImport(@NotNull final Project project,
                        @NotNull final String projectFilePath,
                        @Nullable final Callback callback) throws ConfigurationException {
    Projects.setBuildAction(project, Projects.BuildAction.REBUILD);

    final Ref<ConfigurationException> errorRef = new Ref<ConfigurationException>();

    myDelegate.importProject(project, projectFilePath, new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(@Nullable final DataNode<ProjectData> projectInfo) {
        assert projectInfo != null;
        Runnable runnable = new Runnable() {
          @Override
          public void run() {
            populateProject(project, projectInfo);
            open(project, projectFilePath);

            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              project.save();
            }
          }
        };
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          runnable.run();
        }
        else {
          ApplicationManager.getApplication().invokeLater(runnable);
        }
      }

      @Override
      public void onFailure(@NotNull final String errorMessage, @Nullable String errorDetails) {
        ConfigurationException error = handleImportFailure(errorMessage, errorDetails);
        errorRef.set(error);
      }
    });

    ConfigurationException errorCause = errorRef.get();
    if (errorCause != null) {
      throw errorCause;
    }

    // Since importing is synchronous we should have modules now. Notify callback.
    if (notifyCallback(project, callback)) {
      return;
    }

    // If we got here, there is some bad timing and the module creation got delayed somehow. Notify callback as soon as the project roots
    // are created.
    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length > 0) {
          connection.disconnect();
          // TODO: Consider moving callback to AndroidProjectDataService. It can reliably notify when a project has modules.
          notifyCallback(project, callback);
        }
      }
    });
  }

  @NotNull
  private static ConfigurationException handleImportFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
    if (errorDetails != null) {
      LOG.warn(errorDetails);
    }
    String reason = "Failed to import Gradle project: " + errorMessage;
    return new ConfigurationException(ExternalSystemBundle.message("error.resolve.with.reason", reason),
                                      ExternalSystemBundle.message("error.resolve.generic"));
  }

  private static void populateProject(@NotNull final Project newProject, @NotNull final DataNode<ProjectData> projectInfo) {
    newProject.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);
    StartupManager.getInstance(newProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        ExternalSystemApiUtil.executeProjectChangeAction(new Runnable() {
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

  private static boolean notifyCallback(@NotNull Project newProject, @Nullable Callback callback) {
    Module[] modules = ModuleManager.getInstance(newProject).getModules();
    if (modules.length == 0) {
      return false;
    }
    if (callback != null) {
      callback.projectImported(newProject);
    }
    return true;
  }

  // Makes it possible to mock invocations to the Gradle Tooling API.
  static class ImporterDelegate {
    void importProject(@NotNull Project newProject, @NotNull String projectFilePath, @NotNull ExternalProjectRefreshCallback callback)
      throws ConfigurationException {
      try {
        ExternalSystemUtil.refreshProject(newProject, SYSTEM_ID, projectFilePath, callback, true, true);
      }
      catch (RuntimeException e) {
        String externalSystemName = SYSTEM_ID.getReadableName();
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
