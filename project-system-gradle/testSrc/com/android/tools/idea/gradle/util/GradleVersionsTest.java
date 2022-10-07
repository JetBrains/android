/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.intellij.util.ThreeState.NO;
import static com.intellij.util.ThreeState.YES;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

/**
 * Tests for {@link GradleVersions}.
 */
public class GradleVersionsTest extends AndroidGradleTestCase {
  private GradleProjectSettingsFinder mySettingsFinder;
  private GradleVersions myGradleVersions;
  private IdeComponents myIdeComponents;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySettingsFinder = mock(GradleProjectSettingsFinder.class);
    new IdeComponents(getProject()).replaceApplicationService(GradleProjectSettingsFinder.class, mySettingsFinder);
    myGradleVersions = new GradleVersions();
    myIdeComponents = new IdeComponents(getProject());
  }

  public void testReadGradleVersionFromGradleSyncState() throws Exception {
    loadSimpleApplication();
    Project project = getProject();

    String expected = getGradleVersionFromWrapper();

    GradleVersion gradleVersion = myGradleVersions.getGradleVersion(project);
    assertNotNull(gradleVersion);
    assertEquals(expected, gradleVersion.toString());

    // double-check GradleSyncState, just in case
    GradleVersion gradleVersionFromSync = GradleSyncState.getInstance(project).getLastSyncedGradleVersion();
    assertNotNull(gradleVersionFromSync);
    assertEquals(expected, GradleVersions.inferStableGradleVersion(gradleVersionFromSync.toString()));
  }

  public void testReadGradleVersionFromWrapperWhenGradleSyncStateReturnsNullGradleVersion() throws Exception {
    loadSimpleApplication();
    Project project = getProject();

    GradleSyncState syncState = createMockGradleSyncState();
    when(syncState.isSyncNeeded()).thenReturn(NO);
    when(syncState.getLastSyncedGradleVersion()).thenReturn(null);

    simulateGettingGradleSettings();

    String expected = getGradleVersionFromWrapper();

    GradleVersion gradleVersion = myGradleVersions.getGradleVersion(project);
    assertEquals(expected, gradleVersion.toString());
  }

  public void testReadGradleVersionFromWrapperWhenSyncIsNeeded() throws Exception {
    loadSimpleApplication();
    Project project = getProject();

    GradleSyncState syncState = createMockGradleSyncState();
    // Simulate Gradle Sync is needed.
    when(syncState.isSyncNeeded()).thenReturn(YES);

    simulateGettingGradleSettings();

    String expected = getGradleVersionFromWrapper();

    GradleVersion gradleVersion = myGradleVersions.getGradleVersion(project);
    assertEquals(expected, gradleVersion.toString());
  }

  public void testInferStableGradleVersion() {
    assertEquals("7.0", GradleVersions.inferStableGradleVersion("7.0"));
    assertEquals("7.0", GradleVersions.inferStableGradleVersion("7.0-rc-1"));
    assertEquals("7.0", GradleVersions.inferStableGradleVersion("7.0-20210328000045+0000"));
  }

  @NotNull
  private GradleSyncState createMockGradleSyncState() {
    GradleSyncState syncState = mock(GradleSyncState.class);
    new IdeComponents(getProject()).replaceProjectService(GradleSyncState.class, syncState);
    return syncState;
  }

  private void simulateGettingGradleSettings() {
    GradleProjectSettings settings = new GradleProjectSettings();
    settings.setDistributionType(DEFAULT_WRAPPED);
    when(mySettingsFinder.findGradleProjectSettings(any())).thenReturn(settings);
  }

  @NotNull
  private String getGradleVersionFromWrapper() throws IOException {
    GradleWrapper gradleWrapper = GradleWrapper.find(getProject());
    assertNotNull(gradleWrapper);
    String expected = gradleWrapper.getGradleVersion();
    assertNotNull(expected);
    return GradleVersions.inferStableGradleVersion(expected);
  }
}
