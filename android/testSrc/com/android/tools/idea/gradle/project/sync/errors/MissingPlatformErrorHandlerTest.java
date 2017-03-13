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
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallPlatformHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link MissingPlatformErrorHandler}.
 */
public class MissingPlatformErrorHandlerTest extends AndroidGradleTestCase {

  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testHandleError() throws Exception {
    Throwable cause = new IllegalStateException("Failed to find target android-23");

    registerSyncErrorToSimulate(cause);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains("Failed to find target android-23");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(1);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(InstallPlatformHyperlink.class);
  }

  public void testGetMissingPlatform() throws Exception {
    MissingPlatformErrorHandler handler = new MissingPlatformErrorHandler();
    assertEquals("android-21", handler.getMissingPlatform("Failed to find target with hash string 'android-21' in: /pat/tp/sdk"));
    assertEquals("android-21", handler.getMissingPlatform("failed to find target with hash string 'android-21' in: /pat/tp/sdk"));
    assertEquals("android-21", handler.getMissingPlatform("Cause: Failed to find target with hash string 'android-21' in: /pat/tp/sdk"));
    assertEquals("android-21", handler.getMissingPlatform("Cause: failed to find target with hash string 'android-21' in: /pat/tp/sdk"));
    assertEquals("android-21", handler.getMissingPlatform("Failed to find target android-21 : /pat/tp/sdk"));
    assertEquals("android-21", handler.getMissingPlatform("failed to find target android-21 : /pat/tp/sdk"));
    assertEquals("android-21", handler.getMissingPlatform("Cause: Failed to find target android-21 : /pat/tp/sdk"));
    assertEquals("android-21", handler.getMissingPlatform("Cause: failed to find target android-21 : /pat/tp/sdk"));
    assertEquals("android-21", handler.getMissingPlatform("Failed to find target android-21"));
    assertEquals("android-21", handler.getMissingPlatform("failed to find target android-21"));
    assertEquals("android-21", handler.getMissingPlatform("Cause: Failed to find target android-21"));
    assertEquals("android-21", handler.getMissingPlatform("Cause: failed to find target android-21"));
  }
}