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

import static com.android.builder.model.SyncIssue.TYPE_MISSING_SDK_PACKAGE;
import static com.android.builder.model.SyncIssue.TYPE_SDK_NOT_SET;
import static com.android.tools.idea.testing.TestProjectPaths.APP_WITH_BUILDSRC;
import static com.android.tools.idea.testing.TestProjectPaths.NEW_SYNC_KOTLIN_TEST;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtil.loadText;
import static com.intellij.openapi.vfs.VfsUtil.saveText;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalProject;

public class GradleSyncExecutorTest extends GradleSyncIntegrationTestCase {
  protected GradleSyncExecutor mySyncExecutor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncExecutor = new GradleSyncExecutor(getProject());
  }

  public void testFetchGradleModelsWithSimpleApplication() throws Exception {
    loadSimpleApplication();

    @NotNull GradleProjectModels models = mySyncExecutor.fetchGradleModels();
    Map<String, GradleModuleModels> modulesByModuleName = indexByModuleName(models.getModules());

    GradleModuleModels app = modulesByModuleName.get("app");
    assertNotNull(app);
    assertContainsAndroidModels(app);
  }

  public void testFetchGradleModelsWithTransitiveDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    @NotNull GradleProjectModels models = mySyncExecutor.fetchGradleModels();
    Map<String, GradleModuleModels> modulesByModuleName = indexByModuleName(models.getModules());

    GradleModuleModels app = modulesByModuleName.get("app");
    assertNotNull(app);
    assertContainsAndroidModels(app);

    GradleModuleModels javalib1 = modulesByModuleName.get("javalib1");
    assertNotNull(javalib1);
    assertContainsJavaModels(javalib1);
  }

  public void testFetchModelsWithBuildSrc() throws Exception {
    loadProject(APP_WITH_BUILDSRC);
    @NotNull GradleProjectModels models = mySyncExecutor.fetchGradleModels();
    Map<String, GradleModuleModels> modulesByModuleName = indexByModuleName(models.getModules());

    // buildSrc modules are not fetched by fetchGradleModels
    assertThat(modulesByModuleName).hasSize(1);
    GradleModuleModels app = modulesByModuleName.get("app");
    assertNotNull(app);
    assertContainsAndroidModels(app);
  }

  // Ignored until ag/129043402 is fixed. This causes a IllegalStateException in AndroidUnitTest.java within the AndroidGradlePlugin.
  public void /*test*/MissingSdkPackageGiveCorrectError() throws Exception {
    prepareProjectForImport(NEW_SYNC_KOTLIN_TEST);

    // Set an invalid SDK location
    LocalProperties localProperties = new LocalProperties(getProjectFolderPath());
    localProperties.setAndroidSdkPath(getProjectFolderPath());
    localProperties.save();

    String failure = requestSyncAndGetExpectedFailure(
      request -> new GradleSyncInvoker.Request(request.getTrigger()));
    assertThat(failure).contains("Sync issues found!");

    Collection<IdeSyncIssue> syncIssues = SyncIssues.forModule(getModule("app"));
    assertThat(syncIssues).hasSize(2);
    IdeSyncIssue syncIssue = syncIssues.iterator().next();
    assertThat(syncIssue.getType()).isAnyOf(TYPE_SDK_NOT_SET, TYPE_MISSING_SDK_PACKAGE);
    syncIssue = syncIssues.iterator().next();
    assertThat(syncIssue.getType()).isAnyOf(TYPE_SDK_NOT_SET, TYPE_MISSING_SDK_PACKAGE);
  }

  public void testNoVariantsGiveCorrectError() throws Exception {
    prepareProjectForImport(NEW_SYNC_KOTLIN_TEST);

    // Add a variant filter that will remove every variant.
    VirtualFile buildFile = GradleUtil.getGradleBuildFile(new File(getProjectFolderPath(), "app"));
    String content = loadText(buildFile);
    final String newContent = content.replace("android {", "android { \nvariantFilter { variant -> setIgnore(true) }\n");
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        saveText(buildFile, newContent);
      }
      catch (IOException e) {
        fail();
      }
    });

    String failure = requestSyncAndGetExpectedFailure(
      request -> new GradleSyncInvoker.Request(request.getTrigger()));
    assertThat(failure).contains("No variants found for ':app'. Check build files to ensure at least one variant exists.");
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
    assertModelsPresent(models, GradleAndroidModelData.class, GradleModuleModel.class, ExternalProject.class);
  }

  private static void assertContainsJavaModels(@NotNull GradleModuleModels models) {
    assertModelsPresent(models, ExternalProject.class, GradleModuleModel.class);
  }

  private static void assertModelsPresent(@NotNull GradleModuleModels models, @NotNull Class<?>... expectedModelTypes) {
    for (Class<?> type : expectedModelTypes) {
      assertNotNull("Failed to find model of type " + type.getSimpleName(), models.findModel(type));
    }
  }
}