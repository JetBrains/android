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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.AndroidGradleTests.updateGradleVersions;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link IdeAndroidProjectImpl}.
 */
public class IdeAndroidProjectIntegrationTest extends AndroidGradleTestCase {
  public void testSyncFromCachedModel() throws Exception {
    loadSimpleApplication();

    AndroidProject androidProject = getAndroidProjectInApp();
    // Verify AndroidProject was copied.
    assertThat(androidProject).isInstanceOf(IdeAndroidProjectImpl.class);

    SyncListener syncListener = requestSync(new GradleSyncInvoker.Request().setUseCachedGradleModels(true));
    assertTrue(syncListener.isSyncSkipped());

    AndroidProject cached = getAndroidProjectInApp();
    // Verify AndroidProject was deserialized.
    assertThat(cached).isInstanceOf(IdeAndroidProjectImpl.class);

    assertEquals(androidProject, cached);
  }

  public void testSyncWithGradle2Dot2() throws Exception {
    loadSimpleApplication();
    Project project = getProject();
    updateGradleVersions(getBaseDirPath(project), "2.2.0");
    GradleWrapper wrapper = GradleWrapper.find(project);
    wrapper.updateDistributionUrl("3.5");

    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request();
    request.setGenerateSourcesOnSuccess(false).setSkipAndroidPluginUpgrade();
    requestSyncAndWait(request);

    AndroidProject androidProject = getAndroidProjectInApp();
    // Verify AndroidProject was copied.
    assertThat(androidProject).isInstanceOf(IdeAndroidProjectImpl.class);
  }

  @Nullable
  private AndroidProject getAndroidProjectInApp() {
    Module appModule = myModules.getAppModule();
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    return androidModel != null ? androidModel.getAndroidProject() : null;
  }
}
