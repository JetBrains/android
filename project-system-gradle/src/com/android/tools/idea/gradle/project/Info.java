/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.findGradleBuildFile;
import static com.android.tools.idea.gradle.util.GradleUtil.findGradleSettingsFile;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Arrays;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class Info {
  @NotNull public final Project myProject;
  @NotNull public final ProjectFacetManager myFacetManager;

  public Info(@NotNull Project project) {
    myProject = project;
    myFacetManager = ProjectFacetManager.getInstance(project);
  }

  public static Info getInstance(@NotNull Project project) {
    return project.getService(Info.class);
  }

  /**
   * Indicates whether Gradle is used to build at least one module in this project.
   * Note: {@link ProjectSystemUtil#requiresAndroidModel(Project)} indicates whether a project requires an {@link AndroidModel}.
   * That method should be preferred in almost all cases. Use this method only if you explicitly need to check whether the model is
   * Gradle-specific.
   */
  public boolean isBuildWithGradle() {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed()) {
        return false;
      }
      if (isBeingInitializedAsGradleProject(myProject)) {
        return true;
      }
      if (Arrays.stream(ModuleManager.getInstance(myProject).getModules())
        .anyMatch(it -> ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, it))) {
        return true;
      }
      if (myFacetManager.hasFacets(GradleFacet.getFacetTypeId())) {
        return true;
      }
      // See https://code.google.com/p/android/issues/detail?id=203384
      // This could be a project without modules. Check that at least it synced with Gradle.
      if (GradleSyncState.getInstance(myProject).getLastSyncFinishedTimeStamp() != -1L) {
        return true;
      }
      if (!GradleSettings.getInstance(myProject).getLinkedProjectsSettings().isEmpty()){
        return true;
      }
      return hasTopLevelGradleFile();
    });
  }

  private static boolean isBeingInitializedAsGradleProject(@NotNull Project project) {
    String basePath = project.getBasePath();
    if (basePath == null) return false;
    Set<File> projectsBeingInitialized = ApplicationManager.getApplication().getUserData(PROJECTS_BEING_INITIALIZED);
    if (projectsBeingInitialized == null) return false;
    return projectsBeingInitialized.contains(new File(basePath));
  }

  private static final Key<Set<File>> PROJECTS_BEING_INITIALIZED = Key.create("PROJECTS_BEING_INITIALIZED");

  /**
   * Registers the given location as the location of a new Gradle project which is being initialized.
   *
   * <p>This makes {@link #isBuildWithGradle()} return true for projects at this location.
   *
   * @return an access token which should be finalized when the project is initialized.
   */
  public static AccessToken beginInitializingGradleProjectAt(@NotNull File projectFolderPath) {
    UserDataHolderEx userData = (UserDataHolderEx)ApplicationManager.getApplication();
    Set<File> projectsBeingInitialized = userData.putUserDataIfAbsent(PROJECTS_BEING_INITIALIZED,
                                                                      ConcurrentCollectionFactory.createConcurrentSet());
    if (!projectsBeingInitialized.add(projectFolderPath)) {
      throw new IllegalStateException(
        "Cannot initialize two projects at the same location at the same time. Project location: " + projectFolderPath);
    }
    return new AccessToken() {
      @Override
      public void finish() {
        projectsBeingInitialized.remove(projectFolderPath);
      }
    };
  }

  /**
   * Indicates whether the project has a file which gradle could use to perform initialization, either of a "single project" or a
   * "multi-project" build.
   *
   * @return {@code true} if the project has a Gradle build or settings file in the project's root folder; {@code false} otherwise.
   */
  public boolean hasTopLevelGradleFile() {
    if (myProject.isDefault()) {
      return false;
    }
    VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir != null) {
      return ((findGradleBuildFile(baseDir) != null) || (findGradleSettingsFile(baseDir) != null));
    }
    return false;
  }
}
