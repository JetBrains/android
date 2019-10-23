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
package com.android.tools.idea.gradle.npw.project;

import org.junit.Test;

import static com.android.ide.common.repository.GradleVersion.parse;
import static com.android.repository.Revision.parseRevision;
import static com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link GradleBuildSettings}.
 */
public class GradleBuildSettingsTest {
  private static final String CURRENT_BUILD_TOOLS = "27.0.3";

  @Test
  public void toolsBuildVersionMoreRecentThanCurrent() {
    assertTrue(needsExplicitBuildToolsVersion(parse("3.0.0"), parseRevision("27.0.4"), CURRENT_BUILD_TOOLS));
    assertTrue(needsExplicitBuildToolsVersion(parse("3.0.0"), parseRevision("28.0.0"), CURRENT_BUILD_TOOLS));
  }

  @Test
  public void toolsBuildVersionLessRecentOrSameThanCurrent() {
    assertFalse(needsExplicitBuildToolsVersion(parse("3.0.0"), parseRevision("27.0.0"), CURRENT_BUILD_TOOLS));
    assertFalse(needsExplicitBuildToolsVersion(parse("3.0.0"), parseRevision("27.0.3"), CURRENT_BUILD_TOOLS));
    assertFalse(needsExplicitBuildToolsVersion(parse("3.0.0"), parseRevision("26.0.0"), CURRENT_BUILD_TOOLS));
  }

  @Test
  public void oldGradlePluginVersion() {
    assertTrue(needsExplicitBuildToolsVersion(parse("2.0.0"), parseRevision("27.0.0"), CURRENT_BUILD_TOOLS));
    assertTrue(needsExplicitBuildToolsVersion(parse("2.0.0"), parseRevision("27.0.3"), CURRENT_BUILD_TOOLS));
    assertTrue(needsExplicitBuildToolsVersion(parse("2.0.0"), parseRevision("27.0.4"), CURRENT_BUILD_TOOLS));
    assertTrue(needsExplicitBuildToolsVersion(parse("2.0.0"), parseRevision("28.0.0"), CURRENT_BUILD_TOOLS));
  }
}