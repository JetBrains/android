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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandlerManager;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.testing.AndroidGradleTests.replaceRegexGroup;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH1_DOT5;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Tests for {@link SyncExecutorIntegration}.
 */
public class SyncExecutorIntegrationTest extends AndroidGradleTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    setUpIdeGradleSettings();
  }

  private void setUpIdeGradleSettings() {
    GradleProjectSettings settings = new GradleProjectSettings();
    settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    settings.setExternalProjectPath(getProjectFolderPath().getPath());

    GradleSettings.getInstance(getProject()).setLinkedProjectsSettings(Collections.singleton(settings));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSyncProjectWithSingleVariantSync() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(SIMPLE_APPLICATION);

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    SelectedVariantCollector selectedVariantCollector = simulateSelectedVariant("app", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), selectedVariantCollector);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getSyncModuleModels());
    assertThat(modelsByModule).hasSize(2);

    SyncModuleModels appModels = modelsByModule.get("app");
    AndroidProject androidProject = appModels.findModel(AndroidProject.class);
    assertNotNull(androidProject);
    Collection<Variant> variants = androidProject.getVariants();
    assertThat(variants).isEmpty();

    Variant variant = appModels.findModel(Variant.class);
    assertNotNull(variant);
    assertEquals("release", variant.getName());
  }

  public void testSingleVariantSyncWithOldGradleVersion() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    // Use plugin 1.5.0 and Gradle 2.4.0
    prepareProjectForImport(PROJECT_WITH1_DOT5);
    File projectFolderPath = getProjectFolderPath();
    createGradleWrapper(projectFolderPath, "2.4");

    File topBuildFilePath = new File(projectFolderPath, "build.gradle");
    String contents = Files.toString(topBuildFilePath, Charsets.UTF_8);

    contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]", "1.5.0");
    // Remove constraint-layout, which was not supported by old plugins.
    contents = replaceRegexGroup(contents, "(compile 'com.android.support.constraint:constraint-layout:\\+')", "");
    write(contents, topBuildFilePath, Charsets.UTF_8);

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    SelectedVariantCollector selectedVariantCollector = simulateSelectedVariant("app", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), selectedVariantCollector);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();
    SyncProjectModels models = syncListener.getModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getSyncModuleModels());
    assertThat(modelsByModule).hasSize(2);

    SyncModuleModels appModels = modelsByModule.get("app");
    AndroidProject androidProject = appModels.findModel(AndroidProject.class);
    assertNotNull(androidProject);
    Collection<Variant> variants = androidProject.getVariants();
    assertThat(variants).isNotEmpty();
  }

  @NotNull
  private SelectedVariantCollector simulateSelectedVariant(@NotNull String moduleName, @NotNull String selectedVariant) {
    File moduleFolderPath = new File(getProjectFolderPath(), moduleName);
    assertTrue("Path for module '" + moduleName + "' not found", moduleFolderPath.isDirectory());
    return new SelectedVariantCollector(getProject()) {
      @Override
      @NotNull
      SelectedVariants collectSelectedVariants() {
        SelectedVariants selectedVariants = new SelectedVariants();
        String moduleId = createUniqueModuleId(moduleFolderPath, ":" + moduleName);
        selectedVariants.addSelectedVariant(moduleId, selectedVariant);
        return selectedVariants;
      }
    };
  }

  @NotNull
  private static Map<String, SyncModuleModels> indexByModuleName(@NotNull List<SyncModuleModels> allModuleModels) {
    Map<String, SyncModuleModels> modelsByModuleName = new HashMap<>();
    for (SyncModuleModels moduleModels : allModuleModels) {
      modelsByModuleName.put(moduleModels.getModuleName(), moduleModels);
    }
    return modelsByModuleName;
  }

  private static class SyncListener extends SyncExecutionCallback {
    @NotNull private final CountDownLatch myCountDownLatch = new CountDownLatch(1);
    @NotNull private final AtomicBoolean myFailed = new AtomicBoolean();

    SyncListener() {
      doWhenDone(() -> myCountDownLatch.countDown());

      AtomicBoolean failed = new AtomicBoolean();
      doWhenRejected(s -> {
        failed.set(true);
        myCountDownLatch.countDown();
      });
    }

    void await() throws InterruptedException {
      myCountDownLatch.await(5, MINUTES);
    }

    void propagateFailureIfAny() throws Throwable {
      if (myFailed.get()) {
        Throwable error = getSyncError();
        if (error != null) {
          throw error;
        }
        throw new AssertionError("Sync failed - unknown cause");
      }
    }
  }
}