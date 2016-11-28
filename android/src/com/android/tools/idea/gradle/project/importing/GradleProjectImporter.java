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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.project.NewProjects.activateProjectView;
import static com.intellij.ide.impl.ProjectUtil.focusProjectWindow;
import static com.intellij.openapi.fileChooser.impl.FileChooserUtil.setLastOpenedFile;
import static com.intellij.openapi.ui.Messages.showErrorDialog;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Imports an Android-Gradle project without showing the "Import Project" Wizard UI.
 */
public class GradleProjectImporter {
  @NotNull private final SdkSync mySdkSync;
  @NotNull private final GradleSyncInvoker myGradleSyncInvoker;
  @NotNull private final NewProjectSetup myNewProjectSetup;
  @NotNull private final ProjectFolder.Factory myProjectFolderFactory;

  /**
   * Flag used by unit tests to selectively disable code which requires an open project or UI updates; this is used
   * by unit tests that do not run all of IntelliJ (e.g. do not extend the IdeaTestCase base)
   */
  public static boolean ourSkipSetupFromTest;

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
  public void importProject(@NotNull VirtualFile selectedFile) {
    VirtualFile projectFolder = selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
    File projectFolderPath = virtualToIoFile(projectFolder);

    // Sync Android SDKs paths *before* importing project. Studio will freeze if the project has a local.properties file pointing to a SDK
    // path that does not exist. The cause is that having 2 dialogs: one modal (the "Project Import" one) and another from
    // Messages.showErrorDialog (indicating the Android SDK path does not exist) produce a deadlock.
    try {
      LocalProperties localProperties = new LocalProperties(projectFolderPath);
      if (IdeInfo.getInstance().isAndroidStudio()) {
        mySdkSync.syncIdeAndProjectAndroidSdks(localProperties);
      }
    }
    catch (IOException e) {
      Logger.getInstance(getClass()).info("Failed to sync SDKs", e);
      showErrorDialog(e.getMessage(), "Project Import");
      return;
    }

    try {
      String projectName = projectFolder.getName();

      importProject(projectName, projectFolderPath, new NewProjectImportGradleSyncListener() {
        @Override
        public void syncSucceeded(@NotNull Project project) {
          setLastOpenedFile(project, projectFolder);
          focusProjectWindow(project, false);
          activateProjectView(project);
        }
      });
    }
    catch (Throwable e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new RuntimeException(e);
      }
      showErrorDialog(e.getMessage(), "Project Import");
      Logger.getInstance(getClass()).error(e);
    }
  }

  public void importProject(@NotNull String projectName, @NotNull File projectFolderPath, @Nullable GradleSyncListener listener)
    throws IOException, ConfigurationException {
    importProject(projectName, projectFolderPath, Request.EMPTY_REQUEST, listener);
  }

  public void importProject(@NotNull String projectName,
                            @NotNull File projectFolderPath,
                            @NotNull Request request,
                            @Nullable GradleSyncListener listener) throws IOException, ConfigurationException {
    ProjectFolder projectFolder = myProjectFolderFactory.create(projectFolderPath);
    projectFolder.createTopLevelBuildFile();
    projectFolder.createIdeaProjectFolder();

    Project project = request.getProject();
    Project newProject = project == null ? myNewProjectSetup.createProject(projectName, projectFolderPath.getPath()) : project;

    if (project == null) {
      GradleSettings gradleSettings = GradleSettings.getInstance(newProject);
      gradleSettings.setGradleVmOptions("");
    }

    myNewProjectSetup.prepareProjectForImport(newProject, request.getLanguageLevel());

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      newProject.save();
    }

    myGradleSyncInvoker.requestProjectSync(newProject, createSyncRequestSettings(request), listener);
  }

  @VisibleForTesting
  @NotNull
  static GradleSyncInvoker.Request createSyncRequestSettings(@NotNull Request importProjectSettings) {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request();
    // @formatter:off
    request.setGenerateSourcesOnSuccess(importProjectSettings.isGenerateSourcesOnSuccess())
           .setRunInBackground(false)
           .setUseCachedGradleModels(false)
           .setNewProject(true);
    // @formatter:on
    return request;
  }

  public static class Request {
    @NotNull private static final Request EMPTY_REQUEST = new Request();

    @Nullable private Project myProject;
    @Nullable private LanguageLevel myLanguageLevel;

    private boolean myGenerateSourcesOnSuccess = true;

    @Nullable
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public Request setProject(@Nullable Project project) {
      myProject = project;
      return this;
    }

    @Nullable
    public LanguageLevel getLanguageLevel() {
      return myLanguageLevel;
    }

    @NotNull
    public Request setLanguageLevel(@Nullable LanguageLevel languageLevel) {
      myLanguageLevel = languageLevel;
      return this;
    }

    public boolean isGenerateSourcesOnSuccess() {
      return myGenerateSourcesOnSuccess;
    }

    @NotNull
    public Request setGenerateSourcesOnSuccess(boolean generateSourcesOnSuccess) {
      myGenerateSourcesOnSuccess = generateSourcesOnSuccess;
      return this;
    }
  }
}
