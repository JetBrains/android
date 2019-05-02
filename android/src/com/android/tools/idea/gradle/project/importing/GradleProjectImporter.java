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

import static com.android.tools.idea.util.ToolWindows.activateProjectView;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_NEW;
import static com.intellij.ide.impl.ProjectUtil.focusProjectWindow;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath;
import static com.intellij.openapi.fileChooser.impl.FileChooserUtil.setLastOpenedFile;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.ExceptionUtil.rethrowUnchecked;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.ContainerUtilRt;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Imports an Android-Gradle project without showing the "Import Project" Wizard UI.
 */
public class GradleProjectImporter {
  // A copy of a private constant from GradleJvmStartupActivity.
  @NonNls private static final String SHOW_UNLINKED_GRADLE_POPUP = "show.inlinked.gradle.project.popup";
  @NotNull private final SdkSync mySdkSync;
  @NotNull private final GradleSyncInvoker myGradleSyncInvoker;
  @NotNull private final NewProjectSetup myNewProjectSetup;
  @NotNull private final ProjectFolder.Factory myProjectFolderFactory;

  /**
   * Flag used by unit tests to selectively disable code which requires an open project or UI updates; this is used
   * by unit tests that do not run all of IntelliJ (e.g. do not extend the IdeaTestCase base)
   */
  @SuppressWarnings("StaticNonFinalField") public static boolean ourSkipSetupFromTest;

  @NotNull
  public static GradleProjectImporter getInstance() {
    return ServiceManager.getService(GradleProjectImporter.class);
  }

  public GradleProjectImporter(@NotNull SdkSync sdkSync, @NotNull GradleSyncInvoker gradleSyncInvoker) {
    this(sdkSync, gradleSyncInvoker, new NewProjectSetup(), new ProjectFolder.Factory());
  }

  @VisibleForTesting
  GradleProjectImporter(@NotNull SdkSync sdkSync,
                        @NotNull GradleSyncInvoker gradleSyncInvoker,
                        @NotNull NewProjectSetup newProjectSetup,
                        @NotNull ProjectFolder.Factory projectFolderFactory) {
    mySdkSync = sdkSync;
    myGradleSyncInvoker = gradleSyncInvoker;
    myNewProjectSetup = newProjectSetup;
    myProjectFolderFactory = projectFolderFactory;
  }

  /**
   * Imports the given Gradle project.
   *
   * @param selectedFile the selected build.gradle or the project's root directory.
   */
  @Nullable
  public Project importProject(@NotNull VirtualFile selectedFile) {
    VirtualFile projectFolder = findProjectFolder(selectedFile);
    Project newProject = importProjectCore(projectFolder);
    if (newProject != null) {
      GradleProjectInfo.getInstance(newProject).setSkipStartupActivity(true);
      myGradleSyncInvoker.requestProjectSyncAndSourceGeneration(newProject, TRIGGER_PROJECT_NEW, createNewProjectListener(projectFolder));
    }
    return newProject;
  }

  /**
   * Ensures presence of the top level Gradle build file and the .idea directory and, additionally, performs cleanup of the libraries
   * storage to force their re-import.
   */
  @Nullable
  public Project importProjectCore(@NotNull VirtualFile projectFolder) {
    Project newProject;
    File projectFolderPath = virtualToIoFile(projectFolder);
    try {
      setUpLocalProperties(projectFolderPath);
      String projectName = projectFolder.getName();
      newProject = importProjectNoSync(projectName, projectFolderPath, new Request());
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

  @NotNull
  private static VirtualFile findProjectFolder(@NotNull VirtualFile selectedFile) {
    return selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
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

  @NotNull
  private static GradleSyncListener createNewProjectListener(@NotNull VirtualFile projectFolder) {
    return new GradleSyncListener() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        TransactionGuard.getInstance().submitTransactionLater(project, () -> {
          setLastOpenedFile(project, projectFolder);
          focusProjectWindow(project, false);
          activateProjectView(project);
        });
      }
    };
  }

  @NotNull
  public Project importProjectNoSync(@NotNull String projectName,
                                      @NotNull File projectFolderPath,
                                      @NotNull Request request) throws IOException {
    ProjectFolder projectFolder = myProjectFolderFactory.create(projectFolderPath);
    projectFolder.createTopLevelBuildFile();
    projectFolder.createIdeaProjectFolder();

    Project newProject = request.project;

    if (newProject == null) {
      newProject = myNewProjectSetup.createProject(projectName, projectFolderPath.getPath());
      GradleSettings gradleSettings = GradleSettings.getInstance(newProject);
      gradleSettings.setGradleVmOptions("");

      String externalProjectPath = toCanonicalPath(projectFolderPath.getPath());
      GradleProjectSettings projectSettings = gradleSettings.getLinkedProjectSettings(externalProjectPath);
      if (projectSettings == null) {
        Set<GradleProjectSettings> projects = ContainerUtilRt.newHashSet(gradleSettings.getLinkedProjectsSettings());
        projectSettings = new GradleProjectSettings();
        projectSettings.setExternalProjectPath(externalProjectPath);
        projects.add(projectSettings);
        gradleSettings.setLinkedProjectsSettings(projects);
      }
    }

    GradleProjectInfo projectInfo = GradleProjectInfo.getInstance(newProject);
    projectInfo.setNewProject(request.isNewProject);
    projectInfo.setImportedProject(true);
    silenceUnlinkedGradleProjectNotificationIfNecessary(newProject);

    myNewProjectSetup.prepareProjectForImport(newProject, request.javaLanguageLevel);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }
    return newProject;
  }

  private void silenceUnlinkedGradleProjectNotificationIfNecessary(Project newProject) {
    GradleSettings gradleSettings = GradleSettings.getInstance(newProject);
    if (gradleSettings.getLinkedProjectsSettings().isEmpty()) {
      PropertiesComponent.getInstance(newProject).setValue(SHOW_UNLINKED_GRADLE_POPUP, false, true);
    }
  }

  public static class Request {
    @Nullable public final Project project;
    @Nullable public LanguageLevel javaLanguageLevel;
    public boolean isNewProject;

    public Request() {
      this.project = null;
    }

    public Request(@Nullable Project project) {
      this.project = project;
    }
  }
}
