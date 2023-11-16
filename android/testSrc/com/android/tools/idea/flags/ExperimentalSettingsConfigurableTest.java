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
package com.android.tools.idea.flags;

import static com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem.DEFAULT;
import static com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem.SPECIFIED_LOCATION;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.rendering.RenderSettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.LightPlatformTestCase;
import org.mockito.Mock;

/**
 * Tests for {@link ExperimentalSettingsConfigurable}.
 */
public class ExperimentalSettingsConfigurableTest extends LightPlatformTestCase {
  @Mock private GradleExperimentalSettings mySettings;
  private ExperimentalSettingsConfigurable myConfigurable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySettings.TRACE_PROFILE_LOCATION = "";
    myConfigurable = new ExperimentalSettingsConfigurable(mySettings, new RenderSettings());
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

    myConfigurable.enableTraceGradleSync(true);
    mySettings.TRACE_GRADLE_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.TRACE_GRADLE_SYNC = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setTraceProfileLocation("/tmp/text1.profile");
    mySettings.TRACE_PROFILE_LOCATION = "/tmp/text2.profile";
    assertTrue(myConfigurable.isModified());
    mySettings.TRACE_PROFILE_LOCATION = "/tmp/text1.profile";
    assertFalse(myConfigurable.isModified());

    myConfigurable.setTraceProfileSelection(DEFAULT);
    mySettings.TRACE_PROFILE_SELECTION = SPECIFIED_LOCATION;
    assertTrue(myConfigurable.isModified());
    mySettings.TRACE_PROFILE_SELECTION = DEFAULT;
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

  public void testApply() throws ConfigurationException {
    myConfigurable.enableUseMultiVariantExtraArtifacts(true);
    myConfigurable.enableConfigureAllGradleTasks(false);
    myConfigurable.enableTraceGradleSync(true);
    myConfigurable.setTraceProfileLocation("/tmp/text1.profile");
    myConfigurable.setTraceProfileSelection(DEFAULT);
    myConfigurable.enableParallelSync(true);
    myConfigurable.enableGradleApiOptimization(true);
    myConfigurable.enableDeriveRuntimeClasspathsForLibraries(true);

    myConfigurable.apply();

    assertTrue(mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS);
    assertTrue(mySettings.SKIP_GRADLE_TASKS_LIST);
    assertTrue(mySettings.TRACE_GRADLE_SYNC);
    assertEquals("/tmp/text1.profile", mySettings.TRACE_PROFILE_LOCATION);
    assertEquals(DEFAULT, mySettings.TRACE_PROFILE_SELECTION);
    assertTrue(mySettings.SKIP_GRADLE_TASKS_LIST);
    assertTrue(mySettings.ENABLE_GRADLE_API_OPTIMIZATION);
    assertTrue(mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES);

    myConfigurable.enableUseMultiVariantExtraArtifacts(false);
    myConfigurable.enableConfigureAllGradleTasks(true);
    myConfigurable.enableTraceGradleSync(false);
    myConfigurable.setTraceProfileLocation("/tmp/text2.profile");
    myConfigurable.setTraceProfileSelection(SPECIFIED_LOCATION);
    myConfigurable.enableParallelSync(false);
    myConfigurable.enableGradleApiOptimization(false);
    myConfigurable.enableDeriveRuntimeClasspathsForLibraries(false);

    myConfigurable.apply();

    assertFalse(mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS);
    assertFalse(mySettings.SKIP_GRADLE_TASKS_LIST);
    assertFalse(mySettings.TRACE_GRADLE_SYNC);
    assertEquals("/tmp/text2.profile", mySettings.TRACE_PROFILE_LOCATION);
    assertEquals(SPECIFIED_LOCATION, mySettings.TRACE_PROFILE_SELECTION);
    assertFalse(mySettings.ENABLE_PARALLEL_SYNC);
    assertFalse(mySettings.ENABLE_GRADLE_API_OPTIMIZATION);
    assertFalse(mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES);
  }

  public void testReset() {
    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = true;
    mySettings.SKIP_GRADLE_TASKS_LIST = true;
    mySettings.TRACE_GRADLE_SYNC = true;
    mySettings.TRACE_PROFILE_LOCATION = "/tmp/text1.profile";
    mySettings.TRACE_PROFILE_SELECTION = DEFAULT;
    mySettings.ENABLE_PARALLEL_SYNC = true;
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = true;
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = true;

    myConfigurable.reset();

    assertTrue(myConfigurable.isUseMultiVariantExtraArtifact());
    assertFalse(myConfigurable.isConfigureAllGradleTasksEnabled());
    assertTrue(myConfigurable.isTraceGradleSyncEnabled());
    assertEquals("/tmp/text1.profile", myConfigurable.getTraceProfileLocation());
    assertEquals(DEFAULT, myConfigurable.getTraceProfileSelection());
    assertTrue(myConfigurable.isParallelSyncEnabled());
    assertTrue(myConfigurable.isGradleApiOptimizationEnabled());
    assertTrue(myConfigurable.isDeriveRuntimeClasspathsForLibraries());

    mySettings.USE_MULTI_VARIANT_EXTRA_ARTIFACTS = false;
    mySettings.SKIP_GRADLE_TASKS_LIST = false;
    mySettings.TRACE_GRADLE_SYNC = false;
    mySettings.TRACE_PROFILE_LOCATION = "/tmp/text2.profile";
    mySettings.TRACE_PROFILE_SELECTION = SPECIFIED_LOCATION;
    mySettings.ENABLE_PARALLEL_SYNC = false;
    mySettings.ENABLE_GRADLE_API_OPTIMIZATION = false;
    mySettings.DERIVE_RUNTIME_CLASSPATHS_FOR_LIBRARIES = false;

    myConfigurable.reset();

    assertFalse(myConfigurable.isUseMultiVariantExtraArtifact());
    assertTrue(myConfigurable.isConfigureAllGradleTasksEnabled());
    assertFalse(myConfigurable.isTraceGradleSyncEnabled());
    assertEquals("/tmp/text2.profile", myConfigurable.getTraceProfileLocation());
    assertEquals(SPECIFIED_LOCATION, myConfigurable.getTraceProfileSelection());
    assertFalse(myConfigurable.isParallelSyncEnabled());
    assertFalse(myConfigurable.isGradleApiOptimizationEnabled());
    assertFalse(myConfigurable.isDeriveRuntimeClasspathsForLibraries());
  }

  public void testIsTraceProfileValid() {
    myConfigurable.enableTraceGradleSync(true);
    myConfigurable.setTraceProfileLocation("");
    myConfigurable.setTraceProfileSelection(DEFAULT);
    assertFalse(myConfigurable.isTraceProfileInvalid());

    myConfigurable.setTraceProfileLocation("");
    myConfigurable.setTraceProfileSelection(SPECIFIED_LOCATION);
    assertTrue(myConfigurable.isTraceProfileInvalid());
  }
}
