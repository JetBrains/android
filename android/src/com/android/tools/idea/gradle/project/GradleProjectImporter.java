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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.ProjectImportEventMessage;
import com.android.tools.idea.gradle.customizer.CompilerOutputPathModuleCustomizer;
import com.android.tools.idea.gradle.customizer.ContentRootModuleCustomizer;
import com.android.tools.idea.gradle.customizer.DependenciesModuleCustomizer;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.SystemProperties;
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

  private final ModuleCustomizer[] myModuleCustomizers =
    {new ContentRootModuleCustomizer(), new DependenciesModuleCustomizer(), new CompilerOutputPathModuleCustomizer()};

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
   * @param project  the given project. This method does nothing if the project is not an Android-Gradle project.
   * @param callback called after the project has been imported.
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public void reImportProject(@NotNull final Project project, @Nullable Callback callback) throws ConfigurationException {
    if (Projects.isGradleProject(project) || hasTopLevelGradleBuildFile(project)) {
      FileDocumentManager.getInstance().saveAllDocuments();
      doImport(project, false /* existing project */, false /* asynchronous import */, callback);
    }
    else {
      Runnable notificationTask = new Runnable() {
        @Override
        public void run() {
          String msg = String.format("The project '%s' is not a Gradle-based project", project.getName());
          AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);

          NotificationListener notificationListener = new CustomNotificationListener(project, new OpenMigrationToGradleUrlHyperlink());
          notification.showBalloon("Project Sync", msg, NotificationType.ERROR, notificationListener);
        }
      };
      Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread()) {
        notificationTask.run();
      }
      else {
        application.invokeLater(notificationTask);
      }
    }
  }

  private static boolean hasTopLevelGradleBuildFile(@NotNull Project project) {
    VirtualFile baseDir = project.getBaseDir();
    VirtualFile gradleBuildFile = baseDir.findChild(SdkConstants.FN_BUILD_GRADLE);
    return gradleBuildFile != null && gradleBuildFile.exists() && !gradleBuildFile.isDirectory();
  }

  /**
   * Imports and opens the newly created Android project.
   *
   * @param projectName    name of the project.
   * @param projectRootDir root directory of the project.
   * @param callback       called after the project has been imported.
   * @throws IOException            if any file I/O operation fails (e.g. creating the '.idea' directory.)
   * @throws ConfigurationException if any required configuration option is missing (e.g. Gradle home directory path.)
   */
  public void importProject(@NotNull String projectName, @NotNull File projectRootDir, @Nullable Callback callback)
    throws IOException, ConfigurationException {
    GradleImportNotificationListener.attachToManager();

    createTopLevelBuildFileIfNotExisting(projectRootDir);
    createIdeaProjectDir(projectRootDir);

    final Project newProject = createProject(projectName, projectRootDir.getPath());
    setUpProject(newProject);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    Projects.setProjectBuildMode(newProject, BuildMode.REBUILD);

    doImport(newProject, true /* new project */, true /* synchronous import */, callback);
  }

  private static void createTopLevelBuildFileIfNotExisting(@NotNull File projectRootDir) throws IOException {
    File projectFile = new File(projectRootDir, SdkConstants.FN_BUILD_GRADLE);
    if (projectFile.isFile()) {
      return;
    }
    FileUtilRt.createIfNotExists(projectFile);
    String contents = "// Top-level build file where you can add configuration options common to all sub-projects/modules." +
                      SystemProperties.getLineSeparator();
    FileUtil.writeToFile(projectFile, contents);
  }

  private static void createIdeaProjectDir(@NotNull File projectRootDir) throws IOException {
    File ideaDir = new File(projectRootDir, Project.DIRECTORY_STORE_FOLDER);
    FileUtil.ensureExists(ideaDir);
  }

  @NotNull
  private static Project createProject(@NotNull String projectName, @NotNull String projectPath) throws ConfigurationException {
    ProjectManager projectManager = ProjectManager.getInstance();
    Project newProject = projectManager.createProject(projectName, projectPath);
    if (newProject == null) {
      throw new NullPointerException("Failed to create a new IDEA project");
    }
    return newProject;
  }

  private static void setUpProject(@NotNull final Project newProject) {
    CommandProcessor.getInstance().executeCommand(newProject, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            // In practice, it really does not matter where the compiler output folder is. Gradle handles that. This is done just to please
            // IDEA.
            String compileOutputUrl = VfsUtilCore.pathToUrl(newProject.getBasePath() + "/build/classes");
            CompilerProjectExtension compilerProjectExt = CompilerProjectExtension.getInstance(newProject);
            assert compilerProjectExt != null;
            compilerProjectExt.setCompilerOutputUrl(compileOutputUrl);
            setUpGradleSettings(newProject);
          }
        });
      }
    }, null, null);
  }

  private static void setUpGradleSettings(@NotNull Project newProject) {
    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    projectSettings.setExternalProjectPath(newProject.getBasePath());
    projectSettings.setUseAutoImport(true);

    GradleSettings gradleSettings = GradleSettings.getInstance(newProject);
    gradleSettings.setLinkedProjectsSettings(ImmutableList.of(projectSettings));
  }

  private void doImport(@NotNull final Project project, final boolean newProject, boolean modal, @Nullable final Callback callback)
    throws ConfigurationException {
    myDelegate.importProject(project, new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(@Nullable final DataNode<ProjectData> projectInfo) {
        assert projectInfo != null;
        final Application application = ApplicationManager.getApplication();
        Runnable runnable = new Runnable() {
          @Override
          public void run() {
            populateProject(project, projectInfo);
            if (newProject) {
              open(project);
            }
            else {
              updateStructureAccordingToBuildVariants(project);
            }

            if (!application.isUnitTestMode()) {
              project.save();
            }
            if (newProject) {
              configureGradleProject(project);
            }
            if (callback != null) {
              callback.projectImported(project);
            }
          }
        };
        if (application.isUnitTestMode()) {
          runnable.run();
        }
        else {
          application.invokeLater(runnable);
        }
      }

      @Override
      public void onFailure(@NotNull final String errorMessage, @Nullable String errorDetails) {
        if (errorDetails != null) {
          LOG.warn(errorDetails);
        }
        String newMessage = ExternalSystemBundle.message("error.resolve.with.reason", errorMessage);
        LOG.info(newMessage);
        if (callback != null) {
          callback.importFailed(project, newMessage);
        }
      }
    }, modal);
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
                boolean synchronous = true;
                ProjectDataManager dataManager = ServiceManager.getService(ProjectDataManager.class);

                Collection<DataNode<ModuleData>> modules = ExternalSystemApiUtil.findAll(projectInfo, ProjectKeys.MODULE);
                dataManager.importData(ProjectKeys.MODULE, modules, newProject, synchronous);

                Collection<DataNode<ProjectImportEventMessage>> eventMessages =
                  ExternalSystemApiUtil.findAll(projectInfo, AndroidProjectKeys.IMPORT_EVENT_MSG);
                dataManager.importData(AndroidProjectKeys.IMPORT_EVENT_MSG, eventMessages, newProject, synchronous);
              }
            });
          }
        });
      }
    });
  }

  private static void open(@NotNull final Project newProject) {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    ProjectUtil.updateLastProjectLocation(newProject.getBasePath());
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

  private void updateStructureAccordingToBuildVariants(final Project project) {
    // Update module dependencies, content roots and output paths. This needs to be done in case the selected variant is not
    // the same one as the default (an by "default" we mean the first in the drop-down.)
    ExternalSystemApiUtil.executeProjectChangeAction(true, new Runnable() {
      @Override
      public void run() {
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
          AndroidFacet facet = Facets.getFirstFacetOfType(module, AndroidFacet.ID);
          if (facet != null) {
            IdeaAndroidProject ideaAndroidProject = facet.getIdeaAndroidProject();
            for (ModuleCustomizer customizer : myModuleCustomizers) {
              customizer.customizeModule(module, project, ideaAndroidProject);
            }
          }
        }
      }
    });
  }

  private static void configureGradleProject(@NotNull Project project) {
    // We need to do this because AndroidGradleProjectComponent#projectOpened is being called when the project is created, instead of when
    // the project is opened. When 'projectOpened' is called, the project is not fully configured, and it does not looks like it is
    // Gradle-based, resulting in listeners (e.g. modules added events) not being registered. Here we force the listeners to be registered.
    AndroidGradleProjectComponent projectComponent = ServiceManager.getService(project, AndroidGradleProjectComponent.class);
    projectComponent.configureGradleProject(false);
  }

  // Makes it possible to mock invocations to the Gradle Tooling API.
  static class ImporterDelegate {
    void importProject(@NotNull Project project, @NotNull ExternalProjectRefreshCallback callback, boolean modal)
      throws ConfigurationException {
      try {
        ExternalSystemUtil.refreshProject(project, SYSTEM_ID, project.getBasePath(), callback, false, modal, true);
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

    void importFailed(@NotNull Project project, @NotNull String errorMessage);
  }
}
