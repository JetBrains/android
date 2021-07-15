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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.flags.StudioFlags;
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
  @Mock private RecommendedPluginVersionUpgradeDialog.Factory myUpgradeDialogFactory;
  @Mock private RecommendedPluginVersionUpgradeDialog myUpgradeDialog;
  @Mock private RecommendedUpgradeReminder myUpgradeReminder;
  private ContentManager myContentManager;
  @Mock private AssistantInvoker myAssistantInvoker;
  @Mock private AgpUpgradeRefactoringProcessor myProcessor;
  @Mock private AgpClasspathDependencyRefactoringProcessor myAgpClasspathDependencyRefactoringProcessor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myContentManager = spy(new ContentManager(project));
    ServiceContainerUtil.replaceService(project, ContentManager.class, myContentManager, project);
    ServiceContainerUtil.replaceService(project, AssistantInvoker.class, myAssistantInvoker, project);
    when(myAssistantInvoker.createProcessor(same(project), any(), any())).thenReturn(myProcessor);
    // TODO(xof): this is a clear leak of implementation details.  Figure out how to remove it.
    when(myProcessor.getClasspathRefactoringProcessor()).thenReturn(myAgpClasspathDependencyRefactoringProcessor);
    ServiceContainerUtil.replaceService(project, DumbService.class, new MockDumbService(project), project);
    when(myUpgradeDialogFactory.create(same(project), any(), any())).thenReturn(myUpgradeDialog);
    when(myPluginInfo.getModule()).thenReturn(getModule());
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("4.0.0"));
  }

  public void testCheckUpgradeWhenUpgradeReminderIsNotDue() {
    Project project = getProject();
    // Simulate that a day has not passed since the user clicked "Remind me tomorrow".
    when(myUpgradeReminder.shouldAsk()).thenReturn(false);

    // TODO(xof): this fails with a leaked SDK for me.  Why?  And does it matter?
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(project));
  }

  public void testCheckUpgradeWhenCurrentVersionIsEqualToRecommended() {
    simulateUpgradeReminderIsDue();

    GradleVersion pluginVersion = GradleVersion.parse("2.2.0");
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), pluginVersion, pluginVersion));
  }

  public void testCheckUpgradeWhenCurrentVersionIsGreaterRecommended() {
    simulateUpgradeReminderIsDue();

    GradleVersion current = GradleVersion.parse("2.3.0");
    GradleVersion recommended = GradleVersion.parse("2.2.0");
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), current, recommended));
  }

  public void testPerformUpgradeWhenCurrentIsPreviewRecommendedIsSnapshot() {
    simulateUpgradeReminderIsDue();

    // Current version is a preview
    GradleVersion current = GradleVersion.parse("2.3.0-alpha1");
    // Recommended version is same major version, but "snapshot"
    GradleVersion recommended = GradleVersion.parse("2.3.0-dev");
    // For this combination of plugin versions, the IDE should not ask for upgrade.
    assertFalse(GradlePluginUpgrade.shouldRecommendPluginUpgrade(getProject(), current, recommended));
  }

  public void testDoNotInvokeUpgradeAssistantWhenUserDeclinesUpgrade() {
    simulateUpgradeReminderIsDue();

    // Simulate project's plugin version is lower than latest.
    GradleVersion current = GradleVersion.parse("2.2.0");
    GradleVersion recommended = GradleVersion.parse("2.3.0");

    // Simulate user declined upgrade.
    when(myUpgradeDialog.showAndGet()).thenReturn(false);
    assertFalse(GradlePluginUpgrade.performRecommendedPluginUpgrade(getProject(), current, recommended, myUpgradeDialogFactory));

    verifyUpgradeAssistantWasNotInvoked();
  }

  private void verifyUpgradeAssistantWasNotInvoked() {
    verifyNoInteractions(myContentManager);
    verifyNoInteractions(myAssistantInvoker);
    verifyNoInteractions(myProcessor);
  }

  public void testInvokeUpgradeAssistantWhenUserAcceptsUpgrade() {
    simulateUpgradeReminderIsDue();

    GradleVersion current = GradleVersion.parse("2.2.0");
    GradleVersion recommended = GradleVersion.parse("2.3.0");

    // Simulate user accepted upgrade.
    when(myUpgradeDialog.showAndGet()).thenReturn(true);
    when(myAssistantInvoker.showAndGetAgpUpgradeDialog(any(), same(false))).thenReturn(true);
    try (MockedStatic<AndroidPluginInfo> androidPluginInfoMock = mockStatic(AndroidPluginInfo.class)) {
      androidPluginInfoMock.when(() -> AndroidPluginInfo.find(myProject)).thenReturn(myPluginInfo);
      assertFalse(GradlePluginUpgrade.performRecommendedPluginUpgrade(getProject(), current, recommended, myUpgradeDialogFactory));
      verifyUpgradeAssistantWasInvoked();
    }
  }

  private void verifyUpgradeAssistantWasInvoked() {
    if (StudioFlags.AGP_UPGRADE_ASSISTANT_TOOL_WINDOW.get()) {
      verify(myContentManager).showContent();
      // need more user action to check that myProcessor.run() is executed, but we can punt to ContentManager tests.
    }
    else {
      verify(myAssistantInvoker).showAndGetAgpUpgradeDialog(same(myProcessor), same(false));
      verify(myProcessor).run();
    }
    verify(myAssistantInvoker).createProcessor(same(myProject), any(), any());
  }

  private void simulateUpgradeReminderIsDue() {
    when(myUpgradeReminder.shouldAsk()).thenReturn(true);
  }
}
