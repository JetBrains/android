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
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ToggleOfflineModeHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link MissingDependencyErrorHandler}.
 */
public class MissingDependencyErrorHandlerTest extends AndroidGradleTestCase {

  private GradleSyncMessagesStub mySyncMessagesStub;
  private Boolean myOriginalOfflineSetting;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
    myOriginalOfflineSetting = GradleSettings.getInstance(getProject()).isOfflineWork();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      GradleSettings.getInstance(getProject()).setOfflineWork(myOriginalOfflineSetting); // Set back to default value.
    }
    finally {
      super.tearDown();
    }
  }

  public void testHandleErrorWithoutLocation() throws Exception {
    GradleSettings.getInstance(getProject()).setOfflineWork(true); // Set "offline mode" on to force the IDE to show quick-fix.
    String errMsg = "Could not find any version that matches 1.0.0.";
    registerSyncErrorToSimulate(errMsg);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);
    assertThat(notificationUpdate.getText()).isEqualTo(errMsg);

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(2);
    assertThat(quickFixes.get(0)).isInstanceOf(ToggleOfflineModeHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(SearchInBuildFilesHyperlink.class);
  }

  public void testHandleErrorWithBuildFileLocation() throws Exception {
    GradleSettings.getInstance(getProject()).setOfflineWork(true); // Set "offline mode" on to force the IDE to show quick-fix.
    String errMsg = "Could not find myLib.\nRequired by: app\nBuild file '/xyz/build.gradle' line: 3";
    registerSyncErrorToSimulate(errMsg);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);
    assertThat(notificationUpdate.getText()).isEqualTo(errMsg);

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(3);
    assertThat(quickFixes.get(0)).isInstanceOf(ToggleOfflineModeHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(OpenFileHyperlink.class);
    assertThat(quickFixes.get(2)).isInstanceOf(SearchInBuildFilesHyperlink.class);
  }
}