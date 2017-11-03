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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.UpdateResult;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link RecommendedPluginVersionUpgradeStep}.
 */
public class RecommendedPluginVersionUpgradeStepIntegrationTest extends IdeaTestCase {
  @Mock private AndroidPluginInfo myPluginInfo;
  @Mock private AndroidPluginGeneration myPluginGeneration;
  @Mock private RecommendedPluginVersionUpgradeDialog.Factory myUpgradeDialogFactory;
  @Mock private RecommendedPluginVersionUpgradeDialog myUpgradeDialog;
  @Mock private TimeBasedUpgradeReminder myUpgradeReminder;
  @Mock private AndroidPluginVersionUpdater myVersionUpdater;

  private RecommendedPluginVersionUpgradeStep myUpgradeStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    IdeComponents.replaceService(project, AndroidPluginVersionUpdater.class, myVersionUpdater);

    when(myPluginInfo.getPluginGeneration()).thenReturn(myPluginGeneration);
    when(myUpgradeDialogFactory.create(same(project), any(), any())).thenReturn(myUpgradeDialog);

    myUpgradeStep = new RecommendedPluginVersionUpgradeStep(myUpgradeDialogFactory, myUpgradeReminder);
  }

  public void testCheckAndPerformUpgradeWhenUpgradeReminderIsNotDue() {
    Project project = getProject();
    // Simulate that a day has not passed since the user clicked "Remind me tomorrow".
    when(myUpgradeReminder.shouldRecommendUpgrade(project)).thenReturn(false);

    assertFalse(myUpgradeStep.checkAndPerformUpgrade(project, myPluginInfo));

    verifyUpgradeDialogWasNotDisplayed();
    verifyPluginVersionWasNotUpdated();
  }

  public void testCheckAndPerformUpgradeWhenCurrentVersionIsEqualToRecommended() {
    simulateUpgradeReminderIsDue();

    String pluginVersion = "2.2.0";
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse(pluginVersion));
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn(pluginVersion);

    assertFalse(myUpgradeStep.checkAndPerformUpgrade(getProject(), myPluginInfo));

    verifyUpgradeDialogWasNotDisplayed();
    verifyPluginVersionWasNotUpdated();
  }

  public void testCheckAndPerformUpgradeWhenCurrentVersionIsGreaterRecommended() {
    simulateUpgradeReminderIsDue();

    // Simulate project's plugin version is lower than latest.
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.3.0"));
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn("2.2.0");

    assertFalse(myUpgradeStep.checkAndPerformUpgrade(getProject(), myPluginInfo));

    verifyUpgradeDialogWasNotDisplayed();
    verifyPluginVersionWasNotUpdated();
  }

  public void testCheckAndPerformUpgradeWhenCurrentVersionIsPreviewAndRecommendedIsSnapshot() {
    simulateUpgradeReminderIsDue();

    // Current version is a preview
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.3.0-alpha1"));
    // Recommended version is same major version, but "snapshot"
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn("2.3.0-dev");

    // For this combination of plugin versions, the IDE should not ask for upgrade.
    assertFalse(myUpgradeStep.checkAndPerformUpgrade(getProject(), myPluginInfo));

    verifyUpgradeDialogWasNotDisplayed();
    verifyPluginVersionWasNotUpdated();
  }

  private void verifyUpgradeDialogWasNotDisplayed() {
    verify(myUpgradeDialogFactory, never()).create(same(getProject()), any(), any());
    verify(myUpgradeDialog, never()).showAndGet();
  }

  public void testCheckAndPerformUpgradeWhenUserDeclinesUpgrade() {
    simulateUpgradeReminderIsDue();

    // Simulate project's plugin version is lower than latest.
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.2.0"));
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn("2.3.0");

    // Simulate user declined upgrade.
    when(myUpgradeDialog.showAndGet()).thenReturn(false);

    assertFalse(myUpgradeStep.checkAndPerformUpgrade(getProject(), myPluginInfo));

    verifyPluginVersionWasNotUpdated();
  }

  private void verifyPluginVersionWasNotUpdated() {
    verify(myVersionUpdater, never()).updatePluginVersionAndSync(any(), any(), anyBoolean());
  }

  public void testCheckAndPerformUpgradeWhenVersionUpdateFails() {
    simulateUpgradeReminderIsDue();

    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.2.0"));
    GradleVersion latestPluginVersion = GradleVersion.parse("2.3.0");
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn(latestPluginVersion.toString());

    // Simulate user accepted upgrade.
    when(myUpgradeDialog.showAndGet()).thenReturn(true);

    // Simulate updating plugin version failed.
    simulatePluginVersionUpdate(latestPluginVersion, false /* update failed */);

    assertFalse(myUpgradeStep.checkAndPerformUpgrade(getProject(), myPluginInfo));
  }

  public void testCheckAndPerformUpgradeWhenVersionSucceeds() {
    simulateUpgradeReminderIsDue();

    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.2.0"));
    GradleVersion latestPluginVersion = GradleVersion.parse("2.3.0");
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn(latestPluginVersion.toString());

    // Simulate user accepted upgrade.
    when(myUpgradeDialog.showAndGet()).thenReturn(true);

    // Simulate updating plugin version succeeded.
    simulatePluginVersionUpdate(latestPluginVersion, true /* update successful */);

    assertTrue(myUpgradeStep.checkAndPerformUpgrade(getProject(), myPluginInfo));
  }

  private void simulateUpgradeReminderIsDue() {
    when(myUpgradeReminder.shouldRecommendUpgrade(getProject())).thenReturn(true);
  }

  private void simulatePluginVersionUpdate(@NotNull GradleVersion pluginVersion, boolean success) {
    UpdateResult result = mock(UpdateResult.class);
    when(result.versionUpdateSuccess()).thenReturn(success);
    GradleVersion gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
    when(myVersionUpdater.updatePluginVersionAndSync(eq(pluginVersion), eq(gradleVersion), anyBoolean())).thenReturn(result);
  }
}