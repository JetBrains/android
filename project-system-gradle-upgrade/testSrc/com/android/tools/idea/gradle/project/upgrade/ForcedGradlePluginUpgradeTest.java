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

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.ui.Messages.OK;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.PlatformTestCase;
import java.util.List;
import org.mockito.Mock;

/**
 * Tests for {@link GradlePluginUpgrade#performForcedPluginUpgrade(Project, GradleVersion, GradleVersion)}}.
 */
public class ForcedGradlePluginUpgradeTest extends PlatformTestCase {
  @Mock private AndroidPluginInfo myPluginInfo;
  @Mock private AndroidPluginVersionUpdater myVersionUpdater;

  private GradleSyncMessagesStub mySyncMessages;
  private TestDialog myOriginalTestDialog;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    when(myPluginInfo.getModule()).thenReturn(getModule());

    new IdeComponents(project).replaceProjectService(AndroidPluginVersionUpdater.class, myVersionUpdater);
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project);
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

  public void testPerformUpgradeWhenUpgradeNotNeeded() {
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");

    boolean upgraded = GradlePluginUpgrade.versionsShouldForcePluginUpgrade(GradleVersion.parse("3.0.0"), latestPluginVersion);
    assertFalse(upgraded);

    verify(myVersionUpdater, never()).updatePluginVersion(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION));
    assertThat(mySyncMessages.getReportedMessages()).isEmpty();
  }

  public void testPerformUpgradeWhenUserAcceptsUpgrade() {
    GradleVersion alphaPluginVersion = GradleVersion.parse("2.0.0-alpha9");
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");

    // Simulate user accepting the upgrade.
    //
    // TODO(b/159995302): if the upgrade assistant is turned on, there are more user interaction points within performForcedPluginUpgrade,
    //  which require overrides (e.g. of DialogWrapper.showAndGet()) just for testing purposes, and those tests end up not testing the
    //  production codepaths anyway.  On moving to an asynchronous handling of plugin upgrades, this test and others will need to be
    //  adapted, rewritten or removed.
    if (!StudioFlags.AGP_UPGRADE_ASSISTANT.get()) {
      myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(OK));

      boolean upgraded = GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), alphaPluginVersion, latestPluginVersion);
      assertTrue(upgraded);

      verify(myVersionUpdater).updatePluginVersion(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION), alphaPluginVersion);
      assertThat(mySyncMessages.getReportedMessages()).isEmpty();
    }
  }

  // See https://code.google.com/p/android/issues/detail?id=227927
  public void testPerformUpgradeWhenUserDeclinesUpgrade() {
    GradleVersion latestPluginVersion = GradleVersion.parse("2.0.0");
    GradleVersion currentPluginVersion = GradleVersion.parse("2.0.0-alpha9");

    // Simulate user canceling upgrade.
    myOriginalTestDialog = ForcedPluginPreviewVersionUpgradeDialog.setTestDialog(new TestMessagesDialog(Messages.CANCEL));

    boolean upgraded = GradlePluginUpgrade.performForcedPluginUpgrade(getProject(), currentPluginVersion, latestPluginVersion);
    assertFalse(upgraded);

    List<SyncMessage> messages = mySyncMessages.getReportedMessages();
    assertThat(messages).hasSize(1);
    String message = messages.get(0).getText()[1];
    assertThat(message).contains("Please update your project to use version 2.0.0.");

    verify(myVersionUpdater, never()).updatePluginVersion(latestPluginVersion, GradleVersion.parse(GRADLE_LATEST_VERSION));
  }
}
