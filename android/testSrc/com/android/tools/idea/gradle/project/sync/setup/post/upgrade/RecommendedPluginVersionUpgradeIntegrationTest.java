/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.UpdateResult;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link GradlePluginUpgrade#performRecommendedPluginUpgrade(Project)} and
 * {@link GradlePluginUpgrade#shouldRecommendPluginUpgrade(Project)}.
 */
public class RecommendedPluginVersionUpgradeIntegrationTest extends PlatformTestCase {
  @Mock private AndroidPluginInfo myPluginInfo;
  @Mock private LatestKnownPluginVersionProvider myLatestKnownPluginVersionProvider;
  @Mock private RecommendedPluginVersionUpgradeDialog.Factory myUpgradeDialogFactory;
  @Mock private RecommendedPluginVersionUpgradeDialog myUpgradeDialog;
  @Mock private RecommendedUpgradeReminder myUpgradeReminder;
  private AndroidPluginVersionUpdater myVersionUpdater;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myVersionUpdater = spy(AndroidPluginVersionUpdater.getInstance(project));
    new IdeComponents(project).replaceProjectService(AndroidPluginVersionUpdater.class, myVersionUpdater);

    when(myUpgradeDialogFactory.create(same(project), any(), any())).thenReturn(myUpgradeDialog);
    when(myPluginInfo.getModule()).thenReturn(getModule());
  }

  public void testCheckUpgradeWhenUpgradeReminderIsNotDue() {
    Project project = getProject();
    // Simulate that a day has not passed since the user clicked "Remind me tomorrow".
    when(myUpgradeReminder.shouldAsk()).thenReturn(false);

    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(project));
  }

  public void testCheckUpgradeWhenCurrentVersionIsEqualToRecommended() {
    simulateUpgradeReminderIsDue();

    String pluginVersion = "2.2.0";
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse(pluginVersion));
    when(myPluginInfo.getLatestKnownPluginVersionProvider()).thenReturn(myLatestKnownPluginVersionProvider);
    when(myLatestKnownPluginVersionProvider.get()).thenReturn(pluginVersion);

    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), myPluginInfo));
  }

  public void testCheckUpgradeWhenCurrentVersionIsGreaterRecommended() {
    simulateUpgradeReminderIsDue();

    // Simulate project's plugin version is lower than latest.
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.3.0"));
    when(myPluginInfo.getLatestKnownPluginVersionProvider()).thenReturn(myLatestKnownPluginVersionProvider);
    when(myLatestKnownPluginVersionProvider.get()).thenReturn("2.2.0");

    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), myPluginInfo));
  }

  public void testPerformUpgradeWhenCurrentIsPreviewRecommendedIsSnapshot() {
    simulateUpgradeReminderIsDue();

    // Current version is a preview
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.3.0-alpha1"));
    // Recommended version is same major version, but "snapshot"
    when(myPluginInfo.getLatestKnownPluginVersionProvider()).thenReturn(myLatestKnownPluginVersionProvider);
    when(myLatestKnownPluginVersionProvider.get()).thenReturn("2.3.0-dev");

    // For this combination of plugin versions, the IDE should not ask for upgrade.
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), myPluginInfo));
  }

  public void testPerformUpgradeWhenUserDeclinesUpgrade() {
    simulateUpgradeReminderIsDue();

    // Simulate project's plugin version is lower than latest.
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.2.0"));
    when(myPluginInfo.getLatestKnownPluginVersionProvider()).thenReturn(myLatestKnownPluginVersionProvider);
    when(myLatestKnownPluginVersionProvider.get()).thenReturn("2.3.0");

    // Simulate user declined upgrade.
    when(myUpgradeDialog.showAndGet()).thenReturn(false);

    assertFalse(GradlePluginUpgrade.performRecommendedPluginUpgrade(getProject(), myPluginInfo, myUpgradeDialogFactory));

    verifyPluginVersionWasNotUpdated();
  }

  private void verifyPluginVersionWasNotUpdated() {
    verify(myVersionUpdater, never()).updatePluginVersionAndSync(any(), any());
  }

  public void testCheckAndPerformUpgradeWhenVersionUpdateFails() {
    simulateUpgradeReminderIsDue();

    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.2.0"));
    GradleVersion latestPluginVersion = GradleVersion.parse("2.3.0");
    when(myPluginInfo.getLatestKnownPluginVersionProvider()).thenReturn(myLatestKnownPluginVersionProvider);
    when(myLatestKnownPluginVersionProvider.get()).thenReturn(latestPluginVersion.toString());

    // Simulate user accepted upgrade.
    when(myUpgradeDialog.showAndGet()).thenReturn(true);

    // Simulate updating plugin version failed.
    simulatePluginVersionUpdate(latestPluginVersion, false /* update failed */);

    assertFalse(GradlePluginUpgrade.performRecommendedPluginUpgrade(getProject(), myPluginInfo, myUpgradeDialogFactory));
  }

  public void testCheckAndPerformUpgradeWhenVersionSucceeds() {
    simulateUpgradeReminderIsDue();

    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.2.0"));
    GradleVersion latestPluginVersion = GradleVersion.parse("2.3.0");
    when(myPluginInfo.getLatestKnownPluginVersionProvider()).thenReturn(myLatestKnownPluginVersionProvider);
    when(myLatestKnownPluginVersionProvider.get()).thenReturn(latestPluginVersion.toString());

    // Simulate user accepted upgrade.
    when(myUpgradeDialog.showAndGet()).thenReturn(true);

    // Simulate updating plugin version succeeded.
    simulatePluginVersionUpdate(latestPluginVersion, true /* update successful */);

    assertTrue(GradlePluginUpgrade.performRecommendedPluginUpgrade(getProject(), myPluginInfo, myUpgradeDialogFactory));
  }

  private void simulateUpgradeReminderIsDue() {
    when(myUpgradeReminder.shouldAsk()).thenReturn(true);
  }

  private void simulatePluginVersionUpdate(@NotNull GradleVersion pluginVersion, boolean success) {
    UpdateResult result = mock(UpdateResult.class);
    when(result.versionUpdateSuccess()).thenReturn(success);
    GradleVersion gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
    doReturn(result).when(myVersionUpdater).updatePluginVersionAndSync(eq(pluginVersion), eq(gradleVersion), any());
  }
}
