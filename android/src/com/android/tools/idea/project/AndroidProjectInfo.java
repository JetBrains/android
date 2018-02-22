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

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AndroidProjectInfo {
  @NotNull private final Project myProject;

  @NotNull
  public static AndroidProjectInfo getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidProjectInfo.class);
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
    return ContainerUtil.filter(ProjectFacetManager.getInstance(myProject).getModulesWithFacet(AndroidFacet.ID), module -> {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      return facet != null && facet.getProjectType() == projectType;
    });
  }

  /**
   * Indicates whether the given project has at least one module backed by an {@link AndroidProject}. To check if a project is a
   * "Gradle project," please use the method {@link GradleProjectInfo#isBuildWithGradle()}.
   *
   * @return {@code true} if the project has one or more modules backed by an {@link AndroidProject}; {@code false} otherwise.
   */
  public boolean requiresAndroidModel() {
    return ContainerUtil.exists(ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID), f -> f.requiresAndroidModel());
  }

  public boolean isApkProject() {
    // TODO revisit the self-imposed limitation of having only one module in a APK project.
    return ProjectFacetManager.getInstance(myProject).hasFacets(ApkFacet.getFacetTypeId());
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
    return ContainerUtil.exists(ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID), f -> f.requiresAndroidModel() && f.getAndroidModel() == null);
  }

  /**
   * Indicates whether the project is a legacy IDEA Android project (which is deprecated in Android Studio.)
   *
   * @return {@code true} if the given project is a legacy IDEA Android project; {@code false} otherwise.
   */
  public boolean isLegacyIdeaAndroidProject() {
    return ContainerUtil.exists(ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID), f -> !f.requiresAndroidModel());
  }

}
