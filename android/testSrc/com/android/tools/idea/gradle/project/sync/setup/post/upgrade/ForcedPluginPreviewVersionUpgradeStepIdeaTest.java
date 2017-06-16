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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.List;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.ui.Messages.OK;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tets for {@link ForcedPluginPreviewVersionUpgradeStep}.
 */
public class ForcedPluginPreviewVersionUpgradeStepIdeaTest extends IdeaTestCase {
  @Mock private AndroidPluginInfo myPluginInfo;
  @Mock private AndroidPluginGeneration myPluginGeneration;
  @Mock private AndroidPluginVersionUpdater myVersionUpdater;
  @Mock private GradleSyncState mySyncState;

  private GradleSyncMessagesStub mySyncMessages;
  private TestDialog myOriginalTestDialog;

  private ForcedPluginPreviewVersionUpgradeStep myVersionUpgrade;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(myPluginInfo.getPluginGeneration()).thenReturn(myPluginGeneration);

    Project project = getProject();
    IdeComponents.replaceService(project, GradleSyncState.class, mySyncState);
    IdeComponents.replaceService(project, AndroidPluginVersionUpdater.class, myVersionUpdater);
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);

    myVersionUpgrade = new ForcedPluginPreviewVersionUpgradeStep();
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

  public void testCheckAndPerformUpgradeWhenUpgradeNotNeeded() {
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn(latestPluginVersion.toString());
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("3.0.0"));

    boolean upgraded = myVersionUpgrade.checkAndPerformUpgrade(getProject(), myPluginInfo);
    assertFalse(upgraded);

    verify(mySyncState, never()).syncEnded();
    verify(myVersionUpdater, never()).updatePluginVersionAndSync(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION), true);
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  public void testCheckAndPerformUpgradeWhenUserAcceptsUpgrade() {
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn(latestPluginVersion.toString());
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.0.0-alpha9"));

    // Simulate user accepting the upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(OK));

    boolean upgraded = myVersionUpgrade.checkAndPerformUpgrade(getProject(), myPluginInfo);
    assertTrue(upgraded);

    verify(mySyncState).syncEnded();
    verify(myVersionUpdater).updatePluginVersionAndSync(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION), true);
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  // See https://code.google.com/p/android/issues/detail?id=227927
  public void testCheckAndPerformUpgradeWhenUserDeclinesUpgrade() {
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");
    when(myPluginGeneration.getLatestKnownVersion()).thenReturn(latestPluginVersion.toString());
    when(myPluginInfo.getPluginVersion()).thenReturn(GradleVersion.parse("2.0.0-alpha9"));

    // Simulate user canceling upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(Messages.CANCEL));

    boolean upgraded = myVersionUpgrade.checkAndPerformUpgrade(getProject(), myPluginInfo);
    assertTrue(upgraded);

    List<SyncMessage> messages = mySyncMessages.getReportedMessages();
    assertThat(messages).hasSize(1);
    String message = messages.get(0).getText()[1];
    assertThat(message).contains("Please update your project to use version 2.0.0.");

    verify(mySyncState).syncEnded();
    verify(myVersionUpdater, never()).updatePluginVersionAndSync(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION), true);
  }
}