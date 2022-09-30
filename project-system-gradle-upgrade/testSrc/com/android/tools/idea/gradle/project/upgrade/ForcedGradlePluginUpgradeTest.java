/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.ui.Messages.OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.repository.GradleVersion.AgpVersion;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.intellij.mock.MockDumbService;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.mockito.Mock;

/**
 * Tests for {@link GradlePluginUpgrade#performForcedPluginUpgrade(Project, AgpVersion, AgpVersion)}}.
 */
public class ForcedGradlePluginUpgradeTest extends PlatformTestCase {
  @Mock private RefactoringProcessorInstantiator myRefactoringProcessorInstantiator;
  @Mock private AgpUpgradeRefactoringProcessor myProcessor;

  private GradleSyncMessages mySyncMessages;
  private TestDialog myOriginalTestDialog;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();

    ServiceContainerUtil.replaceService(project, DumbService.class, new MockDumbService(project), project);
    ServiceContainerUtil.replaceService(project, RefactoringProcessorInstantiator.class, myRefactoringProcessorInstantiator, project);
    when(myRefactoringProcessorInstantiator.createProcessor(same(project), any(), any())).thenReturn(myProcessor);
    mySyncMessages = GradleSyncMessages.getInstance(project);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalTestDialog != null) {
        ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(myOriginalTestDialog);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testNewerThanLatestKnown() {
    AgpVersion latestPluginVersion = AgpVersion.parse("2.0.0");

    boolean incompatible = GradlePluginUpgrade.versionsAreIncompatible(AgpVersion.parse("3.0.0"), latestPluginVersion);
    assertTrue(incompatible);
    // Can't "upgrade" down from a newer version.
    verifyNoInteractions(myRefactoringProcessorInstantiator);
    verifyNoInteractions(myProcessor);
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  public void testUpgradeAccepted() {
    AgpVersion alphaPluginVersion = AgpVersion.parse("2.0.0-alpha9");
    AgpVersion latestPluginVersion = AgpVersion.parse("2.0.0");

    // Simulate user accepting the upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(OK));
    when(myRefactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(any())).thenReturn(true);
    GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), alphaPluginVersion, latestPluginVersion);
    verify(myRefactoringProcessorInstantiator).showAndGetAgpUpgradeDialog(any());
    verify(myProcessor).run();
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  public void testUpgradeAcceptedThenCancelled() {
    AgpVersion alphaPluginVersion = AgpVersion.parse("2.0.0-alpha9");
    AgpVersion latestPluginVersion = AgpVersion.parse("2.0.0");
    // Simulate user accepting then cancelling the upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(OK));
    when(myRefactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(any())).thenReturn(false);
    GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), alphaPluginVersion, latestPluginVersion);
    verify(myRefactoringProcessorInstantiator).showAndGetAgpUpgradeDialog(any());
    verify(myProcessor, never()).run();
  }

  // See https://code.google.com/p/android/issues/detail?id=227927
  public void testUpgradeDeclined() {
    AgpVersion latestPluginVersion = AgpVersion.parse("2.0.0");
    AgpVersion currentPluginVersion = AgpVersion.parse("2.0.0-alpha9");

    // Simulate user canceling upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(Messages.CANCEL));

    GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), currentPluginVersion, latestPluginVersion);

    verifyNoInteractions(myRefactoringProcessorInstantiator);
    verifyNoInteractions(myProcessor);
  }
}
