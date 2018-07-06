/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.mock.MockProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;

public abstract class GradleSyncTestCase extends AndroidGradleTestCase {
  protected GradleSync myGradleSync;


  @Override
  public void setUp() throws Exception {
    super.setUp();
    myGradleSync = createGradleSync();
  }

  @NotNull
  protected abstract GradleSync createGradleSync();

  public void testFetchGradleModelsWithSimpleApplication() throws Exception {
    loadSimpleApplication();

    List<GradleModuleModels> models = myGradleSync.fetchGradleModels(new MockProgressIndicator());
    Map<String, GradleModuleModels> modulesByModuleName = indexByModuleName(models);

    GradleModuleModels app = modulesByModuleName.get("app");
    assertNotNull(app);
    assertContainsAndroidModels(app);
  }

  public void testFetchGradleModelsWithTransitiveDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    List<GradleModuleModels> models = myGradleSync.fetchGradleModels(new MockProgressIndicator());
    Map<String, GradleModuleModels> modulesByModuleName = indexByModuleName(models);

    GradleModuleModels app = modulesByModuleName.get("app");
    assertNotNull(app);
    assertContainsAndroidModels(app);

    GradleModuleModels javalib1 = modulesByModuleName.get("javalib1");
    assertNotNull(javalib1);
    assertContainsJavaModels(javalib1);
  }

  @NotNull
  private static Map<String, GradleModuleModels> indexByModuleName(List<? extends GradleModuleModels> models) {
    Map<String, GradleModuleModels> modelsByName = new HashMap<>();
    for (GradleModuleModels model : models) {
      String name = model.getModuleName();
      modelsByName.put(name, model);
    }
    return modelsByName;
  }

  private static void assertContainsAndroidModels(@NotNull GradleModuleModels models) {
    assertModelsPresent(models, AndroidModuleModel.class, GradleModuleModel.class);
  }

  private static void assertContainsJavaModels(@NotNull GradleModuleModels models) {
    assertModelsPresent(models, JavaModuleModel.class, GradleModuleModel.class);
  }

  private static void assertModelsPresent(@NotNull GradleModuleModels models, @NotNull Class<?>... expectedModelTypes) {
    for (Class<?> type : expectedModelTypes) {
      assertNotNull("Failed to find model of type " + type.getSimpleName(), models.findModel(type));
    }
  }
}