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

import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.GradleBuildListener;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.projectsystem.SyncWithSourceGenerationListener;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

public class CompoundSyncIntegrationTest extends SingleVariantSyncIntegrationTest {

  @Override
  protected boolean useCompoundSyncInfrastructure() {
    return true;
  }

  public void testCompoundSync() throws Exception {
    loadSimpleApplication();

    // Register a sync listener to guarantee all sync steps are run and results are correct
    AtomicBoolean syncStarted = new AtomicBoolean(false);
    AtomicBoolean setupStarted = new AtomicBoolean(false);
    AtomicBoolean syncSucceeded = new AtomicBoolean(false);
    AtomicBoolean sourceGenerationFinished = new AtomicBoolean(false);
    AtomicBoolean syncFinished = new AtomicBoolean(false);
    GradleSyncState.subscribe(getProject(), new GradleSyncListener() {
      @Override
      public void syncStarted(@NotNull Project project, boolean sourceGenerationRequested) {
        assertSame(getProject(), project);
        assertTrue(sourceGenerationRequested);
        syncStarted.set(true);
      }

      @Override
      public void setupStarted(@NotNull Project project) {
        setupStarted.set(true);
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        assertNotNull(AndroidModuleModel.get(getModule("app")));
        syncSucceeded.set(true);
      }

      @Override
      public void sourceGenerationFinished(@NotNull Project project) {
        assertSourcesGenerated("app");
        sourceGenerationFinished.set(true);
      }
    });

    // Register a build listener to guarantee build is not run
    AtomicBoolean buildStarted = new AtomicBoolean(false);
    GradleBuildState.subscribe(getProject(), new GradleBuildListener.Adapter() {
      @Override
      public void buildStarted(@NotNull BuildContext context) {
        buildStarted.set(true);
      }
    });

    // Register a SyncWithSourceGenerationListener to guarantee sync finished is invoked
    GradleSyncState.subscribe(getProject(), new SyncWithSourceGenerationListener() {
      @Override
      public void syncFinished(boolean sourceGenerationRequested, @NotNull ProjectSystemSyncManager.SyncResult result) {
        syncFinished.set(true);
      }
    });

    assertSourcesNotGenerated("app");

    // Invoke sync with source generation
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(getProject(), GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED);

    // All required sync phases were run and verified
    assertTrue(syncStarted.get());
    assertTrue(setupStarted.get());
    assertTrue(syncSucceeded.get());
    assertTrue(sourceGenerationFinished.get());
    assertTrue(syncFinished.get());
    // Gradle build was not invoked
    assertFalse(buildStarted.get());
  }

  private void assertSourcesNotGenerated(@NotNull String moduleName) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(getModule(moduleName));
    assertNotNull(androidModel);
    for (File generatedSourcesFolder : androidModel.getSelectedVariant().getMainArtifact().getGeneratedSourceFolders()) {
      assertFalse(generatedSourcesFolder.exists());
    }
  }

  private void assertSourcesGenerated(@NotNull String moduleName) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(getModule(moduleName));
    assertNotNull(androidModel);
    boolean existsAtLeastOne = false;
    for (File generatedSourcesFolder : androidModel.getSelectedVariant().getMainArtifact().getGeneratedSourceFolders()) {
      existsAtLeastOne = existsAtLeastOne || generatedSourcesFolder.exists();
    }
    assertTrue(existsAtLeastOne);
  }
}
