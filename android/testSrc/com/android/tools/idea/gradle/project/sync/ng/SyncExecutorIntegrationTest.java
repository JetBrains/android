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
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Tests for {@link SyncExecutorIntegration}.
 */
public class SyncExecutorIntegrationTest extends AndroidGradleTestCase {
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
    setUpIdeGradleSettings();

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    File appModuleFolderPath = new File(getProjectFolderPath(), "app");
    assertTrue("Path for module 'app' not found", appModuleFolderPath.isDirectory());
    SelectedVariantCollector selectedVariantCollector = new SelectedVariantCollector(project) {
      @Override
      @NotNull
      SelectedVariants collectSelectedVariants() {
        SelectedVariants selectedVariants = new SelectedVariants();
        String moduleId = createUniqueModuleId(appModuleFolderPath, ":app");
        selectedVariants.addSelectedVariant(moduleId, "release");
        return selectedVariants;
      }
    };

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), selectedVariantCollector);

    CountDownLatch countDownLatch = new CountDownLatch(1);

    SyncExecutionCallback callback = new SyncExecutionCallback();
    callback.doWhenDone(() -> countDownLatch.countDown());

    AtomicBoolean failed = new AtomicBoolean();
    callback.doWhenRejected(s -> {
      failed.set(true);
      countDownLatch.countDown();
    });
    syncExecutor.syncProject(new MockProgressIndicator(), callback);
    countDownLatch.await(5, MINUTES);

    if (failed.get()) {
      Throwable error = callback.getSyncError();
      if (error != null) {
        throw error;
      }
      throw new AssertionError("Sync failed - unknown cause");
    }

    SyncProjectModels models = callback.getModels();
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

  private void setUpIdeGradleSettings() {
    GradleProjectSettings settings = new GradleProjectSettings();
    settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    settings.setExternalProjectPath(getProjectFolderPath().getPath());

    GradleSettings.getInstance(getProject()).setLinkedProjectsSettings(Collections.singleton(settings));
  }

  @NotNull
  private Map<String, SyncModuleModels> indexByModuleName(List<SyncModuleModels> allModuleModels) {
    Map<String, SyncModuleModels> modelsByModuleName = new HashMap<>();
    for (SyncModuleModels moduleModels : allModuleModels) {
      modelsByModuleName.put(moduleModels.getModuleName(), moduleModels);
    }

    return modelsByModuleName;
  }
}