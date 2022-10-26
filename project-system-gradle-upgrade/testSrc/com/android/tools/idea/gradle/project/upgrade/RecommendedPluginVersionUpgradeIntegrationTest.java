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
package com.android.tools.idea.gradle.project.upgrade;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.intellij.mock.MockDumbService;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.mockito.Mock;
import org.mockito.MockedStatic;

/**
 * Tests for {@link GradlePluginUpgrade#performRecommendedPluginUpgrade(Project)} and
 * {@link GradlePluginUpgrade#shouldRecommendPluginUpgrade(Project)}.
 */
public class RecommendedPluginVersionUpgradeIntegrationTest extends PlatformTestCase {
  @Mock private AndroidPluginInfo myPluginInfo;
  @Mock private RecommendedUpgradeReminder myUpgradeReminder;
  private ContentManager myContentManager;
  @Mock private RefactoringProcessorInstantiator myRefactoringProcessorInstantiator;
  @Mock private AgpUpgradeRefactoringProcessor myProcessor;
  @Mock private AgpVersionRefactoringProcessor myAgpVersionRefactoringProcessor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myContentManager = spy(new ContentManagerImpl(project));
    ServiceContainerUtil.replaceService(project, ContentManager.class, myContentManager, project);
    ServiceContainerUtil.replaceService(project, RefactoringProcessorInstantiator.class, myRefactoringProcessorInstantiator, project);
    when(myRefactoringProcessorInstantiator.createProcessor(same(project), any(), any())).thenReturn(myProcessor);
    // TODO(xof): this is a clear leak of implementation details.  Figure out how to remove it.
    when(myProcessor.getAgpVersionRefactoringProcessor()).thenReturn(myAgpVersionRefactoringProcessor);
    ServiceContainerUtil.replaceService(project, DumbService.class, new MockDumbService(project), project);
    when(myPluginInfo.getModule()).thenReturn(getModule());
    when(myPluginInfo.getPluginVersion()).thenReturn(AgpVersion.parse("4.0.0"));
  }

  public void testCheckUpgradeWhenUpgradeReminderIsNotDue() {
    Project project = getProject();
    // Simulate that a day has not passed since the user clicked "Remind me tomorrow".
    when(myUpgradeReminder.shouldAsk()).thenReturn(false);

    // TODO(xof): this fails with a leaked SDK for me.  Why?  And does it matter?
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(project).getUpgrade());
  }

  public void testCheckUpgradeWhenCurrentVersionIsEqualToRecommended() {
    simulateUpgradeReminderIsDue();

    AgpVersion pluginVersion = AgpVersion.parse("2.2.0");
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), pluginVersion, pluginVersion).getUpgrade());
  }

  public void testCheckUpgradeWhenCurrentVersionIsGreaterRecommended() {
    simulateUpgradeReminderIsDue();

    AgpVersion current = AgpVersion.parse("2.3.0");
    AgpVersion recommended = AgpVersion.parse("2.2.0");
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), current, recommended).getUpgrade());
  }

  public void testPerformUpgradeWhenCurrentIsPreviewRecommendedIsSnapshot() {
    simulateUpgradeReminderIsDue();

    // Current version is a preview
    AgpVersion current = AgpVersion.parse("2.3.0-alpha1");
    // Recommended version is same major version, but "snapshot"
    AgpVersion recommended = AgpVersion.parse("2.3.0-dev");
    // For this combination of plugin versions, the IDE should not ask for upgrade.
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), current, recommended).getUpgrade());
  }

  public void testInvokeUpgradeAssistantWhenUserAcceptsUpgrade() {
    simulateUpgradeReminderIsDue();

    AgpVersion current = AgpVersion.parse("4.0.0");
    AgpVersion recommended = AgpVersion.parse("4.1.0");

    // Simulate user accepted upgrade.
    try (MockedStatic<AndroidPluginInfo> androidPluginInfoMock = mockStatic(AndroidPluginInfo.class)) {
      androidPluginInfoMock.when(() -> AndroidPluginInfo.find(myProject)).thenReturn(myPluginInfo);
      GradlePluginUpgrade.performRecommendedPluginUpgrade(myProject, current, recommended);
      verifyUpgradeAssistantWasInvoked();
    }
  }

  private void verifyUpgradeAssistantWasInvoked() {
    verify(myContentManager).showContent(any());
    verify(myRefactoringProcessorInstantiator).createProcessor(same(myProject), any(), any());
  }

  private void simulateUpgradeReminderIsDue() {
    when(myUpgradeReminder.shouldAsk()).thenReturn(true);
  }
}
