/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.importing;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.GradleProjectInfo.beginInitializingGradleProjectAt;
import static com.android.tools.idea.gradle.util.GradleUtil.BUILD_DIR_DEFAULT_NAME;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.invokeLater;
import static com.intellij.openapi.project.ProjectTypeService.setProjectType;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ExceptionUtil.rethrowUnchecked;
import static org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.util.ToolWindows;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serviceContainer.NonInjectable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Imports an Android-Gradle project without showing the "Import Project" Wizard UI.
 */
public class GradleProjectImporter {
  public static final ProjectType ANDROID_PROJECT_TYPE = new ProjectType("Android");
  // A copy of a private constant from GradleJvmStartupActivity.
  @NonNls private static final String SHOW_UNLINKED_GRADLE_POPUP = "show.inlinked.gradle.project.popup";
  @NotNull private final SdkSync mySdkSync;
  @NotNull private final ProjectFolder.Factory myProjectFolderFactory;
  @NotNull private final TopLevelModuleFactory myTopLevelModuleFactory;

  @NotNull
  public static GradleProjectImporter getInstance() {
    return ApplicationManager.getApplication().getService(GradleProjectImporter.class);
  }

  public GradleProjectImporter() {
    this(SdkSync.getInstance(), new TopLevelModuleFactory(), new ProjectFolder.Factory());
  }

  @NonInjectable
  @VisibleForTesting
  GradleProjectImporter(@NotNull SdkSync sdkSync,
                        @NotNull TopLevelModuleFactory topLevelModuleFactory,
                        @NotNull ProjectFolder.Factory projectFolderFactory) {
    mySdkSync = sdkSync;
    myTopLevelModuleFactory = topLevelModuleFactory;
    myProjectFolderFactory = projectFolderFactory;
  }

  /**
   * Ensures presence of the top level Gradle build file and the .idea directory and, additionally, performs cleanup of the libraries
   * storage to force their re-import.
   */
  @Nullable
  public Project importAndOpenProjectCore(@Nullable Project projectToClose,
                                          boolean forceOpenInNewFrame,
                                          @NotNull VirtualFile projectFolder) {
    Project newProject;
    File projectFolderPath = virtualToIoFile(projectFolder);
    try {
      setUpLocalProperties(projectFolderPath);
      String projectName = projectFolder.getName();
      newProject = createProject(projectName, projectFolderPath);
      importProjectNoSync(new Request(newProject));
      ProjectManagerEx.getInstanceEx().openProject(
        projectFolderPath.toPath(),
        new OpenProjectTask(
          forceOpenInNewFrame,
          projectToClose,
          false,
          false,
          newProject,
          null,
          true,
          null,
          null,
          -1,
          -1, true, false, true, null, false, true, null, null, null
          ));
    }
    catch (Throwable e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        rethrowUnchecked(e);
      }
      showErrorDialog(e.getMessage(), "Project Import");
      getLogger().error(e);
      newProject = null;
    }
    return newProject;
  }

  private void setUpLocalProperties(@NotNull File projectFolderPath) throws IOException {
    try {
      LocalProperties localProperties = new LocalProperties(projectFolderPath);
      if (IdeInfo.getInstance().isAndroidStudio()) {
        mySdkSync.syncIdeAndProjectAndroidSdks(localProperties);
      }
    }
    catch (IOException e) {
      getLogger().info("Failed to sync SDKs", e);
      showErrorDialog(e.getMessage(), "Project Import");
      throw e;
    }
  }

  @NotNull
  private Logger getLogger() {
    return Logger.getInstance(getClass());
  }

  public void importProjectNoSync(@NotNull Request request) throws IOException {
    File projectFolderPath = getBaseDirPath(request.project).getAbsoluteFile();

    ProjectFolder projectFolder = myProjectFolderFactory.create(projectFolderPath);
    projectFolder.createTopLevelBuildFile();
    projectFolder.createIdeaProjectFolder();

    Project newProject = request.project;

    GradleProjectInfo projectInfo = GradleProjectInfo.getInstance(newProject);
    projectInfo.setNewProject(request.isNewProject);
    projectInfo.setImportedProject(true);
    silenceUnlinkedGradleProjectNotificationIfNecessary(newProject);

    WriteAction.runAndWait(() -> {
      Sdk jdk = IdeSdks.getInstance().getJdk();
      if (jdk != null) {
        ProjectRootManager.getInstance(newProject).setProjectSdk(jdk);
      }

      if (request.javaLanguageLevel != null) {
        LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(newProject);
        if (extension != null) {
          extension.setLanguageLevel(request.javaLanguageLevel);
        }
      }

      // In practice, it really does not matter where the compiler output folder is. Gradle handles that. This is done just to please
      // IDEA.
      File compilerOutputFolderPath = new File(getBaseDirPath(newProject), join(BUILD_DIR_DEFAULT_NAME, "classes"));
      String compilerOutputFolderUrl = pathToIdeaUrl(compilerOutputFolderPath);
      CompilerProjectExtension compilerProjectExt = CompilerProjectExtension.getInstance(newProject);
      assert compilerProjectExt != null;
      compilerProjectExt.setCompilerOutputUrl(compilerOutputFolderUrl);

      // This allows to customize UI when android project is opened inside IDEA with android plugin.
      setProjectType(newProject, ANDROID_PROJECT_TYPE);
      myTopLevelModuleFactory.createTopLevelModule(newProject);
    });
    invokeLater(newProject, () -> ToolWindows.activateProjectView(newProject));
  }

  /**
   * Creates a new not configured project in a given location.
   */
  @NotNull
  public Project createProject(@NotNull String projectName, @NotNull File projectFolderPath) {
    try (AccessToken ignored = beginInitializingGradleProjectAt(projectFolderPath)) {
      ProjectManager projectManager = ProjectManager.getInstance();
      Project newProject = projectManager.createProject(projectName, projectFolderPath.getPath());
      if (newProject == null) {
        throw new NullPointerException("Failed to create a new project");
      }
      configureNewProject(newProject);
      return newProject;
    }
  }

  @VisibleForTesting
  public static void configureNewProject(Project newProject) {
    GradleSettings gradleSettings = GradleSettings.getInstance(newProject);
    String externalProjectPath = toCanonicalPath(newProject.getBasePath());
    if (!gradleSettings.getLinkedProjectsSettings().isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        throw new IllegalStateException("configureNewProject should be used with new projects only");
      }
      for (GradleProjectSettings setting : gradleSettings.getLinkedProjectsSettings()) {
        gradleSettings.unlinkExternalProject(setting.getExternalProjectPath());
      }
    }

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    GradleProjectImportUtil.setupGradleSettings(gradleSettings);
    GradleProjectImportUtil.setupGradleProjectSettings(projectSettings, newProject, Paths.get(externalProjectPath));
    gradleSettings.setStoreProjectFilesExternally(false);
    //noinspection unchecked
    ExternalSystemApiUtil.getSettings(newProject, SYSTEM_ID).linkProject(projectSettings);
  }

  private static void silenceUnlinkedGradleProjectNotificationIfNecessary(Project newProject) {
    GradleSettings gradleSettings = GradleSettings.getInstance(newProject);
    if (gradleSettings.getLinkedProjectsSettings().isEmpty()) {
      PropertiesComponent.getInstance(newProject).setValue(SHOW_UNLINKED_GRADLE_POPUP, false, true);
    }
  }

  public static class Request {
    @NotNull public final Project project;
    @Nullable public LanguageLevel javaLanguageLevel;
    public boolean isNewProject;

    public Request(@NotNull Project project) {
      this.project = project;
    }
  }
}
