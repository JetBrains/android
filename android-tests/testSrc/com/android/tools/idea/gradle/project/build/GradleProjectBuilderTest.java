/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import junit.framework.TestCase;

/**
 * Tests for {@link GradleProjectBuilder}.
 */
public class GradleProjectBuilderTest extends TestCase {
  public void testIsSourceGenerationEnabled() throws Exception {
    GradleExperimentalSettings settings = new GradleExperimentalSettings();

    // The default value of settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN is 5.
    settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
    assertTrue(GradleProjectBuilder.isSourceGenerationEnabled(settings, 4));
    assertFalse(GradleProjectBuilder.isSourceGenerationEnabled(settings, 10));

    // The default value of settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC is false and settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN has no effect
    // when the settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC is not set.
    settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 100;
    assertTrue(GradleProjectBuilder.isSourceGenerationEnabled(settings, 10));

    // settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN has no effect when the settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC is not set.
    settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;
    settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 2;
    assertTrue(GradleProjectBuilder.isSourceGenerationEnabled(settings, 3));
    assertTrue(GradleProjectBuilder.isSourceGenerationEnabled(settings, 1));

    settings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
    settings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 2;
    assertFalse(GradleProjectBuilder.isSourceGenerationEnabled(settings, 3));
    assertTrue(GradleProjectBuilder.isSourceGenerationEnabled(settings, 1));
  }
}