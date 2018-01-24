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
import com.google.common.collect.ImmutableList;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public class SyncProjectModels implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final Set<Class<?>> myExtraAndroidModelTypes;
  @NotNull private final Set<Class<?>> myExtraJavaModelTypes;

  // List of SyncModuleModels for modules in root build and included builds.
  @NotNull private final List<SyncModuleModels> mySyncModuleModels = new ArrayList<>();
  @Nullable private GlobalLibraryMap myGlobalLibraryMap;

  public SyncProjectModels(@NotNull Set<Class<?>> extraAndroidModelTypes, @NotNull Set<Class<?>> extraJavaModelTypes) {
    myExtraAndroidModelTypes = extraAndroidModelTypes;
    myExtraJavaModelTypes = extraJavaModelTypes;
  }

  public void populate(@NotNull BuildController controller) {
    GradleBuild rootBuild = controller.getBuildModel();
    List<GradleBuild> gradleBuilds = new ArrayList<>();
    // add the root builds.
    gradleBuilds.add(rootBuild);
    // add the included builds.
    gradleBuilds.addAll(rootBuild.getIncludedBuilds());

    for (GradleBuild gradleBuild : gradleBuilds) {
      GradleProject gradleProject = controller.findModel(gradleBuild.getRootProject(), GradleProject.class);
      populateModels(gradleProject, controller, gradleBuild.getBuildIdentifier());
    }

    // Ensure unique module names.
    deduplicateModuleNames();

    // Request for GlobalLibraryMap, it can only be requested by android module.
    // For plugins prior to 3.0.0, controller.findModel returns null.
    for (SyncModuleModels moduleModels : mySyncModuleModels) {
      AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
      if (androidProject != null) {
        myGlobalLibraryMap = controller.findModel(moduleModels.findModel(GradleProject.class), GlobalLibraryMap.class);
        break;
      }
    }
  }

  private void populateModels(@Nullable GradleProject project,
                              @NotNull BuildController controller,
                              @NotNull BuildIdentifier buildId) {
    if (project == null) {
      return;
    }
    SyncModuleModels models = new SyncModuleModels(project, buildId, myExtraAndroidModelTypes, myExtraJavaModelTypes);
    models.populate(project, controller);
    mySyncModuleModels.add(models);

    for (GradleProject child : project.getChildren()) {
      populateModels(child, controller, buildId);
    }
  }

  // If there are duplicated module names, update module name to include project name.
  private void deduplicateModuleNames() {
    List<SyncModuleModels> syncModuleModels = getSyncModuleModels();
    Map<String, Long> nameCount = syncModuleModels.stream().collect(groupingBy(m -> m.getModuleName(), counting()));
    for (SyncModuleModels moduleModel : syncModuleModels) {
      if (nameCount.get(moduleModel.getModuleName()) > 1) {
        moduleModel.deduplicateModuleName();
      }
    }
  }

  @NotNull
  public List<SyncModuleModels> getSyncModuleModels() {
    return ImmutableList.copyOf(mySyncModuleModels);
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
