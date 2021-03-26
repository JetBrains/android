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
package com.android.tools.idea.project;

import static com.android.tools.idea.apk.debugging.ApkDebugging.isMarkedAsApkDebuggingProject;
import static com.android.tools.idea.apk.debugging.ApkDebugging.markAsApkDebuggingProject;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidProjectInfo {
  @NotNull private final Project myProject;

  @NotNull
  public static AndroidProjectInfo getInstance(@NotNull Project project) {
    return project.getService(AndroidProjectInfo.class);
  }

  public AndroidProjectInfo(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Returns all modules of a given type in the project
   *
   * @param projectType the Project type as an integer given in {@link AndroidProject}
   * @return An array of all Modules in the project of that type
   */
  @NotNull
  public List<Module> getAllModulesOfProjectType(int projectType) {
    List<Module> androidModules = ProjectFacetManager.getInstance(myProject).getModulesWithFacet(AndroidFacet.ID);
    return ContainerUtil.filter(androidModules, module -> {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && facet.getConfiguration().getProjectType() == projectType;
    });
  }

  /**
   * Indicates whether the given project has at least one module backed by an {@link AndroidProject}. To check if a project is a
   * "Gradle project," please use the method {@link GradleProjectInfo#isBuildWithGradle()}.
   *
   * @return {@code true} if the project has one or more modules backed by an {@link AndroidProject}; {@code false} otherwise.
   */
  public boolean requiresAndroidModel() {
    List<AndroidFacet> androidFacets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
    return ContainerUtil.exists(androidFacets, f -> AndroidModel.isRequired(f));
  }

  public boolean isApkProject() {
    if (isMarkedAsApkDebuggingProject(myProject)) {
      // When re-opening an 'APK Debugging' project, this method is checked before modules are loaded, making 'isApkProject' return false.
      // We need to mark the project as 'APK Debugging' project.
      // See http://b/64766060
      return true;
    }
    if (ProjectFacetManager.getInstance(myProject).hasFacets(ApkFacet.getFacetTypeId())) {
      // If we got here is because this "APK Debugging" project was opened with an older preview of Android Studio, and the project has
      // been marked yet.
      markAsApkDebuggingProject(myProject);
      return true;
    }
    return false;
  }

  /**
   * Indicates whether the project requires an Android model, but the model is {@code null}. Possible causes for this scenario to happen are:
   * <ul>
   * <li>the last sync with the build system failed</li>
   * <li>Studio just started up and it has not synced the project yet</li>
   * </ul>
   *
   * @return {@code true} if the project is an Android project that does not contain any build system-based model.
   */
  public boolean requiredAndroidModelMissing() {
    List<AndroidFacet> androidFacets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
    return ContainerUtil.exists(androidFacets, f -> AndroidModel.isRequired(f) && AndroidModel.get(f) == null);
  }

  /**
   * Indicates whether the project is a legacy IDEA Android project (which is deprecated in Android Studio.)
   *
   * @return {@code true} if the given project is a legacy IDEA Android project; {@code false} otherwise.
   */
  public boolean isLegacyIdeaAndroidProject() {
    // If a module has the Android facet, but it does not require a model from the build system, it is a legacy IDEA project.
    List<AndroidFacet> androidFacets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
    return ContainerUtil.exists(androidFacets, f -> !AndroidModel.isRequired(f));
  }
}
