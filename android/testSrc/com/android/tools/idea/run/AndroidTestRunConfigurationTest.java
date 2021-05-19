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

import static com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.google.common.truth.Truth.assertThat;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidGradleTestCase;

public class AndroidTestRunConfigurationTest extends AndroidGradleTestCase {

  private static final String TEST_APP_CLASS_NAME = "google.simpleapplication.ApplicationTest";
  private static final String DYNAMIC_FEATURE_INSTRUMENTED_TEST_CLASS_NAME = "com.example.instantapp.ExampleInstrumentedTest";

  @Override
  public void setUp() throws Exception {
    // Flag has to be overridden as early as possible, since the run configuration type is initialized
    // during test setup (see org.jetbrains.android.AndroidPlugin).
    StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.override(true);

    super.setUp();
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

  public void testApkProviderForPreLDevice() throws Exception {
    loadProject(DYNAMIC_APP);

    AndroidRunConfigurationBase androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), TEST_APP_CLASS_NAME);
    assertNotNull(androidTestRunConfiguration);

    ApkProvider provider = androidTestRunConfiguration.getApkProvider();
    assertThat(provider).isNotNull();
    assertThat(provider).isInstanceOf(GradleApkProvider.class);
    assertThat(((GradleApkProvider)provider).isTest()).isTrue();
    assertThat(((GradleApkProvider)provider).getOutputKind(new AndroidVersion(19)))
      .isEqualTo(GradleApkProvider.OutputKind.AppBundleOutputModel);
  }

  public void testApkProviderForPostLDevice() throws Exception {
    loadProject(DYNAMIC_APP);

    AndroidRunConfigurationBase androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), TEST_APP_CLASS_NAME);
    assertNotNull(androidTestRunConfiguration);

    ApkProvider provider = androidTestRunConfiguration.getApkProvider();
    assertThat(provider).isNotNull();
    assertThat(provider).isInstanceOf(GradleApkProvider.class);
    assertThat(((GradleApkProvider)provider).isTest()).isTrue();
    assertThat(((GradleApkProvider)provider).getOutputKind(new AndroidVersion(24))).isEqualTo(GradleApkProvider.OutputKind.Default);
  }

  public void testApkProviderForDynamicFeatureInstrumentedTest() throws Exception {
    loadProject(DYNAMIC_APP);

    AndroidRunConfigurationBase androidTestRunConfiguration =
      createAndroidTestConfigurationFromClass(getProject(), DYNAMIC_FEATURE_INSTRUMENTED_TEST_CLASS_NAME);
    assertNotNull(androidTestRunConfiguration);

    ApkProvider provider = androidTestRunConfiguration.getApkProvider();
    assertThat(provider).isNotNull();
    assertThat(provider).isInstanceOf(GradleApkProvider.class);
    assertThat(((GradleApkProvider)provider).isTest()).isTrue();
    assertThat(((GradleApkProvider)provider).getOutputKind(new AndroidVersion(24)))
      .isEqualTo(GradleApkProvider.OutputKind.AppBundleOutputModel);
  }
}
