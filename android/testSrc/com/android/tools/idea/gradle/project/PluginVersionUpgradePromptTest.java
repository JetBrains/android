/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleVersion;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link PostProjectSetupTasksExecutor#shouldRecommendUpgrade(GradleVersion, GradleVersion, GradleVersion)}.
 */
public class PluginVersionUpgradePromptTest {
  private GradleVersion myLatestPluginVersion;
  private GradleVersion myLatestGradleVersion;

  @Before
  public void setUp() {
    myLatestPluginVersion = GradleVersion.parse(SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION);
    myLatestGradleVersion = GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION);
  }

  @Test
  public void basedOnPluginVersion1() {
    GradleVersion current = GradleVersion.parse("2.1.2");
    boolean shouldRecommend = PostProjectSetupTasksExecutor.shouldRecommendUpgrade(myLatestPluginVersion, current, myLatestGradleVersion);
    assertTrue(shouldRecommend);
  }

  @Test
  public void basedOnPluginVersion2() {
    GradleVersion current = GradleVersion.parse("2.1.3");
    boolean shouldRecommend = PostProjectSetupTasksExecutor.shouldRecommendUpgrade(myLatestPluginVersion, current, myLatestGradleVersion);
    assertTrue(shouldRecommend);
  }

  @Test
  public void basedOnPluginVersion3() {
    GradleVersion current = myLatestPluginVersion;
    boolean shouldRecommend = PostProjectSetupTasksExecutor.shouldRecommendUpgrade(myLatestPluginVersion, current, myLatestGradleVersion);
    assertFalse(shouldRecommend);
  }

  @Test
  public void basedOnGradleVersion1() {
    GradleVersion current = GradleVersion.parse("2.11");
    boolean shouldRecommend = PostProjectSetupTasksExecutor.shouldRecommendUpgrade(myLatestPluginVersion, myLatestPluginVersion, current);
    assertTrue(shouldRecommend);
  }

  @Test
  public void basedOnGradleVersion2() {
    GradleVersion current = GradleVersion.parse("2.14");
    boolean shouldRecommend = PostProjectSetupTasksExecutor.shouldRecommendUpgrade(myLatestPluginVersion, myLatestPluginVersion, current);
    assertTrue(shouldRecommend);
  }

  @Test
  public void basedOnGradleVersion3() {
    GradleVersion current = GradleVersion.parse("2.14.1");
    boolean shouldRecommend = PostProjectSetupTasksExecutor.shouldRecommendUpgrade(myLatestPluginVersion, myLatestPluginVersion, current);
    assertFalse(shouldRecommend);
  }

  @Test
  public void basedOnGradleVersion4() {
    GradleVersion current = GradleVersion.parse("3.0");
    boolean shouldRecommend = PostProjectSetupTasksExecutor.shouldRecommendUpgrade(myLatestPluginVersion, myLatestPluginVersion, current);
    assertFalse(shouldRecommend);
  }
}