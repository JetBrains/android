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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ToggleOfflineModeHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link CachedDependencyNotFoundErrorHandler}.
 */
public class CachedDependencyNotFoundErrorHandlerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      GradleSettings.getInstance(getProject()).setOfflineWork(false); // back to default value.
    }
    finally {
      super.tearDown();
    }
  }

  public void testHandleError() throws Exception {
    GradleSettings settings = GradleSettings.getInstance(getProject());
    // Set "offline mode" on to force the IDE to show quick-fix.
    settings.setOfflineWork(true);

    String expectedNotificationMessage = "No cached version of dependency, available for offline mode.";
    String error = expectedNotificationMessage + "\nExtra error message.";

    registerSyncErrorToSimulate(error);
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains(expectedNotificationMessage);

    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(1);

    // Verify hyperlinks are correct.
    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(ToggleOfflineModeHyperlink.class);

    ToggleOfflineModeHyperlink toggleOfflineModeQuickFix = (ToggleOfflineModeHyperlink)quickFix;
    assertFalse(toggleOfflineModeQuickFix.isEnableOfflineMode());
  }
}
