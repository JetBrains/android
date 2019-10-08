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
package com.android.tools.idea.run;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.RecommendedPluginVersionUpgradeStep;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.File;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.android.testutils.TestUtils.getSdk;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PRE30;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class AndroidRunConfigurationGradleTest extends AndroidGradleTestCase {
  private AndroidRunConfiguration myRunConfiguration;

  @Override
  public void setUp() throws Exception {
    // Flag has to be overridden as early as possible, since the run configuration type is initialized
    // during test setup (see org.jetbrains.android.AndroidPlugin).
    StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.override(true);

    super.setUp();

    ConfigurationFactory configurationFactory = AndroidRunConfigurationType.getInstance().getFactory();
    myRunConfiguration = new AndroidRunConfiguration(getProject(), configurationFactory);

    // We override the default extension point to prevent the "Gradle Update" UI to show during the test
    PlatformTestUtil.maskExtensions(RecommendedPluginVersionUpgradeStep.EXTENSION_POINT_NAME, Collections.singletonList(new MyPluginVersionUpgradeStep()), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testNoErrorIfGradlePluginVersionIsUpToDate() throws Exception {
    loadProject(DYNAMIC_APP);
    myRunConfiguration.DEPLOY = true;
    myRunConfiguration.DEPLOY_APK_FROM_BUNDLE = true;
    List<ValidationError> errors = myRunConfiguration.checkConfiguration(myAndroidFacet);
    assertThat(errors).isEmpty();
  }

  public void testErrorIfGradlePluginVersionIsOutdated() throws Exception {
    File projectSourceRoot = resolveTestDataPath(SIMPLE_APPLICATION_PRE30);
    File projectRoot = new File(toSystemDependentName(getProject().getBasePath()));

    AndroidGradleTests.prepareProjectForImportCore(projectSourceRoot, projectRoot, root -> {
      AndroidGradleTests.updateLocalProperties(projectRoot, getSdk());
      AndroidGradleTests.createGradleWrapper(projectRoot, "4.5");
      AndroidGradleTests.updateGradleVersions(root, "3.0.0");
    });
    importProject();
    prepareProjectForTest(getProject(), "app");

    // Verifies there is a validation error (since bundle tasks are not available)
    myRunConfiguration.DEPLOY = true;
    myRunConfiguration.DEPLOY_APK_FROM_BUNDLE = true;
    List<ValidationError> errors = myRunConfiguration.checkConfiguration(myAndroidFacet);
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getMessage()).isEqualTo("This option requires a newer version of the Android Gradle Plugin");
  }

  private static class MyPluginVersionUpgradeStep extends RecommendedPluginVersionUpgradeStep {

    @Override
    public boolean checkUpgradable(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
      // Returning {@code false} means "project is all good, no update needed".
      return false;
    }

    @Override
    public boolean performUpgradeAndSync(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
      // Returning {@code false} means "project is all good, no update needed or performed".
      return false;
    }
  }
}
