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
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.SystemProperties.getUserName;
import static java.io.File.separatorChar;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FailedToParseSdkErrorHandler}.
 */
public class FailedToParseSdkErrorHandlerTest extends AndroidGradleTestCase {
  private IdeComponents myIdeComponents;
  private GradleSyncMessagesStub mySyncMessagesStub;
  private AndroidSdks myAndroidSdks;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(getProject());
    myAndroidSdks = myIdeComponents.mockService(AndroidSdks.class);
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
      myIdeComponents = null;
      myAndroidSdks = null;
      mySyncMessagesStub = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testHandleErrorWithoutBrokenSdk() throws Exception {
    when(myAndroidSdks.findPathOfSdkWithoutAddonsFolder(getProject())).thenReturn(null);

    Throwable cause = new RuntimeException("failed to parse SDK");
    registerSyncErrorToSimulate(cause);
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);
    assertThat(notificationUpdate.getText()).isEqualTo("failed to parse SDK");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(0);
  }

  public void testHandleErrorWithBrokenSdkAndNoWriteAccess() throws Exception {
    File sdkPath = new File("/path/to/sdk/home") {
      @Override
      public boolean canWrite() {
        return false;
      }
    };
    when(myAndroidSdks.findPathOfSdkWithoutAddonsFolder(getProject())).thenReturn(sdkPath);

    Throwable cause = new RuntimeException("failed to parse SDK");
    registerSyncErrorToSimulate(cause);
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).isEqualTo(
      "The directory 'add-ons', in the Android SDK at '/path/to/sdk/home', is either missing or empty\n\nCurrent user ("
        .replace('/', separatorChar) + getUserName() + ") does not have write access to the SDK directory.");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(0);
  }

  public void testHandleErrorWithBrokenSdkAndWithWriteAccess() throws Exception {
    File sdkPath = new File("/path/to/sdk/home") {
      @Override
      public boolean canWrite() {
        return true;
      }
    };
    when(myAndroidSdks.findPathOfSdkWithoutAddonsFolder(getProject())).thenReturn(sdkPath);

    Throwable cause = new RuntimeException("failed to parse SDK");
    registerSyncErrorToSimulate(cause);
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).isEqualTo(
      "The directory 'add-ons', in the Android SDK at '/path/to/sdk/home', is either missing or empty".replace('/', separatorChar));

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(0);
  }
}