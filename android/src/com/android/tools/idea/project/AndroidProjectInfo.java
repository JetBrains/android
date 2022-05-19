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

//import static com.android.tools.idea.apk.debugging.ApkDebugging.isMarkedAsApkDebuggingProject;
//import static com.android.tools.idea.apk.debugging.ApkDebugging.markAsApkDebuggingProject;

import com.android.AndroidProjectTypes;
//import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.facet.Facet;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import java.util.stream.Collectors;
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
   * @param projectType the Project type as an integer given in {@link AndroidProjectTypes}
   * @return An array of all Modules in the project of that type
   */
  @NotNull
  public List<Module> getAllModulesOfProjectType(int projectType) {
    return ProjectSystemUtil.getAndroidFacets(myProject).stream()
      .filter(f -> f.getConfiguration().getProjectType() == projectType)
      .map(Facet::getModule)
      .collect(Collectors.toList());
  }

  public boolean isApkProject() {/*
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
    }*/
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
    List<AndroidFacet> androidFacets = ProjectSystemUtil.getAndroidFacets(myProject);
    return ContainerUtil.exists(androidFacets, f -> AndroidModel.isRequired(f) && AndroidModel.get(f) == null);
  }

  /**
   * Indicates whether the project is a legacy IDEA Android project (which is deprecated in Android Studio.)
   *
   * @return {@code true} if the given project is a legacy IDEA Android project; {@code false} otherwise.
   */
  public boolean isLegacyIdeaAndroidProject() {
    // If a module has the Android facet, but it does not require a model from the build system, it is a legacy IDEA project.
    List<AndroidFacet> androidFacets = ProjectSystemUtil.getAndroidFacets(myProject);
    return ContainerUtil.exists(androidFacets, f -> !AndroidModel.isRequired(f));
  }
}
