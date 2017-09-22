/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.level2.GlobalLibraryMap;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GradleProjectModels implements Serializable {
  @NotNull private final Set<Class<?>> myAndroidModelTypes;
  @NotNull private final Set<Class<?>> myJavaModelTypes;

  // Key: module's Gradle path.
  @NotNull private final Map<String, GradleModuleModels> myModelsByModule = new HashMap<>();
  @Nullable private GlobalLibraryMap myGlobalLibraryMap;

  GradleProjectModels(@NotNull Set<Class<?>> androidModelTypes, @NotNull Set<Class<?>> javaModelTypes) {
    myAndroidModelTypes = androidModelTypes;
    myJavaModelTypes = javaModelTypes;
  }

  public void populate(@NotNull GradleBuild gradleBuild, @NotNull BuildController controller) {
    BasicGradleProject rootProject = gradleBuild.getRootProject();

    GradleProject root = controller.findModel(rootProject, GradleProject.class);
    populateModels(root, controller);

    // Request for GlobalLibraryMap, it can only be requested by android module.
    // For plugins prior to 3.0.0, controller.findModel returns null.
    for (GradleModuleModels moduleModels : myModelsByModule.values()) {
      AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
      if (androidProject != null) {
        myGlobalLibraryMap = controller.findModel(moduleModels.findModel(GradleProject.class), GlobalLibraryMap.class);
        break;
      }
    }
  }

  private void populateModels(@NotNull GradleProject project, @NotNull BuildController controller) {
    GradleModuleModels models = new GradleModuleModels(project, myAndroidModelTypes, myJavaModelTypes);
    models.populate(project, controller);
    myModelsByModule.put(project.getPath(), models);

    for (GradleProject child : project.getChildren()) {
      populateModels(child, controller);
    }
  }

  @NotNull
  public Collection<String> getProjectPaths() {
    return myModelsByModule.keySet();
  }

  @Nullable
  public GradleModuleModels getModels(@NotNull String gradlePath) {
    return myModelsByModule.get(gradlePath);
  }

  /**
   * @return {@link GlobalLibraryMap} retrieved from android plugin.
   * <br/>
   * The return value could be null in two cases:
   * <ol>
   * <li>The version of Android plugin doesn't support GlobalLibraryMap. i.e. pre 3.0.0 plugin.</li>
   * <li>There is no Android module in this project.</li>
   * </ol>
   */
  @Nullable
  public GlobalLibraryMap getGlobalLibraryMap() {
    return myGlobalLibraryMap;
  }
}
