/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.util.runsGradle;

import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.ThreeState.NO;
import static com.intellij.util.ThreeState.YES;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.IdeComponents;
import java.io.IOException;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link GradleVersions}.
 */
public class GradleVersionsTest {
  private GradleProjectSettingsFinder mySettingsFinder;
  private GradleVersions myGradleVersions;

  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Before
  public void setup() throws Exception {
    projectRule.loadProject(SIMPLE_APPLICATION);
    mySettingsFinder = mock(GradleProjectSettingsFinder.class);
    new IdeComponents(projectRule.getProject()).replaceApplicationService(GradleProjectSettingsFinder.class, mySettingsFinder);
    myGradleVersions = new GradleVersions();
  }

  @Test
  public void testReadGradleVersionFromGradleSyncState() throws Exception {
    String expected = getGradleVersionFromWrapper();

    GradleVersion gradleVersion = myGradleVersions.getGradleVersion(projectRule.getProject());
    assertThat(gradleVersion).isNotNull();
    assertThat(gradleVersion.getVersion()).isEqualTo(expected);

    // double-check GradleSyncState, just in case
    GradleVersion gradleVersionFromSync = GradleSyncState.getInstance(projectRule.getProject()).getLastSyncedGradleVersion();
    assertThat(gradleVersionFromSync).isNotNull();
    assertThat(GradleVersions.inferStableGradleVersion(gradleVersionFromSync.getVersion())).isEqualTo(expected);
  }

  @Test
  public void testReadGradleVersionFromWrapperWhenGradleSyncStateReturnsNullGradleVersion() throws Exception {
    GradleSyncState syncState = createMockGradleSyncState();
    when(syncState.isSyncNeeded()).thenReturn(NO);
    when(syncState.getLastSyncedGradleVersion()).thenReturn(null);

    simulateGettingGradleSettings();

    String expected = getGradleVersionFromWrapper();

    GradleVersion gradleVersion = myGradleVersions.getGradleVersion(projectRule.getProject());
    assertThat(gradleVersion.getVersion()).isEqualTo(expected);
  }

  @Test
  public void testReadGradleVersionFromWrapperWhenSyncIsNeeded() throws Exception {
    GradleSyncState syncState = createMockGradleSyncState();
    // Simulate Gradle Sync is needed.
    when(syncState.isSyncNeeded()).thenReturn(YES);

    simulateGettingGradleSettings();

    String expected = getGradleVersionFromWrapper();

    GradleVersion gradleVersion = myGradleVersions.getGradleVersion(projectRule.getProject());
    assertThat(gradleVersion.getVersion()).isEqualTo(expected);
  }

  @NotNull
  private GradleSyncState createMockGradleSyncState() {
    GradleSyncState syncState = mock(GradleSyncState.class);
    new IdeComponents(projectRule.getProject()).replaceProjectService(GradleSyncState.class, syncState);
    return syncState;
  }

  private void simulateGettingGradleSettings() {
    GradleProjectSettings settings = new GradleProjectSettings();
    settings.setDistributionType(DEFAULT_WRAPPED);
    when(mySettingsFinder.findGradleProjectSettings(any())).thenReturn(settings);
  }

  @NotNull
  private String getGradleVersionFromWrapper() throws IOException {
    GradleWrapper gradleWrapper = GradleWrapper.find(projectRule.getProject());
    assertThat(gradleWrapper).isNotNull();
    String expected = gradleWrapper.getGradleVersion();
    assertThat(expected).isNotNull();
    return GradleVersions.inferStableGradleVersion(expected);
  }
}
