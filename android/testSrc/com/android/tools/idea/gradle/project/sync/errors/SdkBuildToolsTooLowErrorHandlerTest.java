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

import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallBuildToolsHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link SdkBuildToolsTooLowErrorHandler}.
 */
public class SdkBuildToolsTooLowErrorHandlerTest extends AndroidGradleTestCase {
  private SyncMessages myOriginalSyncMessages;
  private SyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    myOriginalSyncMessages = SyncMessages.getInstance(project);
    mySyncMessagesStub = SyncMessagesStub.replaceSyncMessagesService(project);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalSyncMessages != null) {
        IdeComponents.replaceService(getProject(), SyncMessages.class, myOriginalSyncMessages);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testGetInstance() {
    assertNotNull(SdkBuildToolsTooLowErrorHandler.getInstance());
  }

  public void testHandleError() throws Exception {
    // SdkBuildToolsTooLowErrorHandler requires modules to be loaded, so do a full sync first
    loadSimpleApplication();
    requestSyncAndWait();

    registerSyncErrorToSimulate("The SDK Build Tools revision (1.0.0) is too low for project ':app'. Minimum required is 2.0.3");
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    SyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText())
      .contains("The SDK Build Tools revision (1.0.0) is too low for project ':app'. Minimum required is 2.0.3");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(2);

    assertThat(quickFixes.get(0)).isInstanceOf(InstallBuildToolsHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(OpenFileHyperlink.class);
  }
}