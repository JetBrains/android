/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.flags.ExperimentalConfigurable.ApplyState;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.LightPlatformTestCase;
import org.mockito.Mock;

/**
 * Tests for {@link GradleExperimentalSettingsConfigurable}.
 */
public class GradleExperimentalSettingsConfigurableTest extends LightPlatformTestCase {
  @Mock private GradleExperimentalSettings mySettings;
  private GradleExperimentalSettingsConfigurable myConfigurable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myConfigurable = new GradleExperimentalSettingsConfigurable(mySettings);
  }

  public void testIsModified() {
    myConfigurable.enableUseMultiVariantExtraArtifacts(true);
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = false;
    assertTrue(myConfigurable.isModified());
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.enableConfigureAllGradleTasks(false);
    mySettings.SKIP_GRADLE_TASKS_LIST = false;
    assertTrue(myConfigurable.isModified());
    mySettings.SKIP_GRADLE_TASKS_LIST = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.enableParallelSync(true);
    mySettings.ENABLE_PARALLEL_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.ENABLE_PARALLEL_SYNC = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.enableGradleApiOptimization(true);
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = false;
    assertTrue(myConfigurable.isModified());
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.enableDeriveRuntimeClasspathsForLibraries(true);
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = false;
    assertTrue(myConfigurable.isModified());
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = true;
    assertFalse(myConfigurable.isModified());
  }

  public void testPreApplyCallback() {
    myConfigurable.enableUseMultiVariantExtraArtifacts(true);
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = false;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = true;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());

    myConfigurable.enableConfigureAllGradleTasks(false);
    mySettings.SKIP_GRADLE_TASKS_LIST = false;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());
    mySettings.SKIP_GRADLE_TASKS_LIST = true;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());

    myConfigurable.enableParallelSync(true);
    mySettings.ENABLE_PARALLEL_SYNC = false;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());
    mySettings.ENABLE_PARALLEL_SYNC = true;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());

    myConfigurable.enableGradleApiOptimization(true);
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = false;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = true;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());

    myConfigurable.enableDeriveRuntimeClasspathsForLibraries(true);
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = false;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = true;
    assertEquals(ApplyState.OK, myConfigurable.preApplyCallback());
  }

  public void testApply() throws ConfigurationException {
    myConfigurable.enableUseMultiVariantExtraArtifacts(true);
    myConfigurable.enableConfigureAllGradleTasks(false);
    myConfigurable.enableParallelSync(true);
    myConfigurable.enableGradleApiOptimization(true);
    myConfigurable.enableDeriveRuntimeClasspathsForLibraries(true);

    myConfigurable.apply();

    assertTrue(mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS);
    assertTrue(mySettings.SKIP_GRADLE_TASKS_LIST);
    assertTrue(mySettings.ENABLE_GRADLE_API_OPTIMIZATION);
    assertTrue(mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES);

    myConfigurable.enableUseMultiVariantExtraArtifacts(false);
    myConfigurable.enableConfigureAllGradleTasks(true);
    myConfigurable.enableParallelSync(false);
    myConfigurable.enableGradleApiOptimization(false);
    myConfigurable.enableDeriveRuntimeClasspathsForLibraries(false);

    myConfigurable.apply();

    assertFalse(mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS);
    assertFalse(mySettings.SKIP_GRADLE_TASKS_LIST);
    assertFalse(mySettings.ENABLE_PARALLEL_SYNC);
    assertFalse(mySettings.ENABLE_GRADLE_API_OPTIMIZATION);
    assertFalse(mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES);
  }

  public void testReset() {
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = true;
    mySettings.SKIP_GRADLE_TASKS_LIST = true;
    mySettings.ENABLE_PARALLEL_SYNC = true;
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = true;
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = true;

    myConfigurable.reset();

    assertTrue(myConfigurable.isUseMultiVariantExtraArtifact());
    assertFalse(myConfigurable.isConfigureAllGradleTasksEnabled());
    assertTrue(myConfigurable.isParallelSyncEnabled());
    assertTrue(myConfigurable.isGradleApiOptimizationEnabled());
    assertTrue(myConfigurable.isDeriveRuntimeClasspathsForLibraries());

    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = false;
    mySettings.SKIP_GRADLE_TASKS_LIST = false;
    mySettings.ENABLE_PARALLEL_SYNC = false;
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = false;
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = false;

    myConfigurable.reset();

    assertFalse(myConfigurable.isUseMultiVariantExtraArtifact());
    assertTrue(myConfigurable.isConfigureAllGradleTasksEnabled());
    assertFalse(myConfigurable.isParallelSyncEnabled());
    assertFalse(myConfigurable.isParallelSyncEnabled());
    assertFalse(myConfigurable.isGradleApiOptimizationEnabled());
    assertFalse(myConfigurable.isDeriveRuntimeClasspathsForLibraries());
  }
}
