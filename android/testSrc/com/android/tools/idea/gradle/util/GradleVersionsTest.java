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
package com.android.tools.idea.gradle.util;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.IOException;

import static com.intellij.util.ThreeState.NO;
import static com.intellij.util.ThreeState.YES;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

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
    myGradleVersions = new GradleVersions(mySettingsFinder);
    myIdeComponents = new IdeComponents(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testReadGradleVersionFromGradleSyncState() throws Exception {
    loadSimpleApplication();
    Project project = getProject();

    String expected = getGradleVersionFromWrapper();

    GradleVersion gradleVersion = myGradleVersions.getGradleVersion(project);
    assertNotNull(gradleVersion);
    assertEquals(expected, gradleVersion.toString());

    // double-check GradleSyncState, just in case
    GradleVersion gradleVersionFromSync = GradleSyncState.getInstance(project).getSummary().getGradleVersion();
    assertNotNull(gradleVersionFromSync);
    assertEquals(expected, GradleVersions.removeTimestampFromGradleVersion(gradleVersionFromSync.toString()));
  }

  public void testReadGradleVersionFromWrapperWhenGradleSyncStateReturnsNullGradleVersion() throws Exception {
    loadSimpleApplication();
    Project project = getProject();

    GradleSyncState syncState = createMockGradleSyncState();
    when(syncState.isSyncNeeded()).thenReturn(NO);
    GradleSyncSummary summary = mock(GradleSyncSummary.class);
    when(syncState.getSummary()).thenReturn(summary);

    // Simulate Gradle SyncState returns null.
    when(summary.getGradleVersion()).thenReturn(null);

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

  public void testIsGradle4OrNewer() throws Exception {
    loadSimpleApplication();
    Project project = getProject();

    // Check exactly 4
    GradleVersions spyVersions = spy(myGradleVersions);
    myIdeComponents.replaceService(GradleVersions.class, spyVersions);
    when(spyVersions.getGradleVersion(project)).thenReturn(new GradleVersion(4,0,0));
    assertTrue(GradleVersions.getInstance().isGradle4OrNewer(project));

    // Check by component
    when(spyVersions.getGradleVersion(project)).thenReturn(new GradleVersion(5,0,0));
    assertTrue(GradleVersions.getInstance().isGradle4OrNewer(project));
    when(spyVersions.getGradleVersion(project)).thenReturn(new GradleVersion(4,1,0));
    assertTrue(GradleVersions.getInstance().isGradle4OrNewer(project));
    when(spyVersions.getGradleVersion(project)).thenReturn(new GradleVersion(4,0,1));
    assertTrue(GradleVersions.getInstance().isGradle4OrNewer(project));

    // lower
    when(spyVersions.getGradleVersion(project)).thenReturn(new GradleVersion(3,5));
    assertFalse(GradleVersions.getInstance().isGradle4OrNewer(project));

    // Null
    when(spyVersions.getGradleVersion(project)).thenReturn(null);
    assertFalse(GradleVersions.getInstance().isGradle4OrNewer(project));
  }

  @NotNull
  private GradleSyncState createMockGradleSyncState() {
    GradleSyncState syncState = mock(GradleSyncState.class);
    IdeComponents.replaceService(getProject(), GradleSyncState.class, syncState);
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
    return expected;
  }
}