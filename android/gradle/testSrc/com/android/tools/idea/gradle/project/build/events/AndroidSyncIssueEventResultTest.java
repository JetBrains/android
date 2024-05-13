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
package com.android.tools.idea.gradle.project.build.events;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.INFO;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.WARNING;

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.intellij.build.events.MessageEvent.Kind;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.testFramework.HeavyPlatformTestCase;

/**
 * Test class for {@link AndroidSyncIssueEventResult}.
 */
public class AndroidSyncIssueEventResultTest extends HeavyPlatformTestCase {
  private GradleSyncMessages mySyncMessages;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySyncMessages = GradleSyncMessages.getInstance(myProject);
  }

  public void testCreateWithError() {
    NotificationData data = mySyncMessages.createNotification("Error", "Error Text", ERROR, null);
    AndroidSyncIssueEventResult result = new AndroidSyncIssueEventResult(data);
    assertThat(result.getFailures()).hasSize(1);
    assertThat(result.getKind()).isEqualTo(Kind.ERROR);
    assertThat(result.getDetails()).isEqualTo("Error Text");
  }

  public void testCreateWithInfo() {
    NotificationData data = mySyncMessages.createNotification("Info", "Info Text", INFO, null);
    AndroidSyncIssueEventResult result = new AndroidSyncIssueEventResult(data);
    assertThat(result.getFailures()).isEmpty();
    assertThat(result.getKind()).isEqualTo(Kind.INFO);
    assertThat(result.getDetails()).isEqualTo("Info Text");
  }

  public void testCreateWithWarning() {
    NotificationData data = mySyncMessages.createNotification("Warning", "Warning Text", WARNING, null);
    AndroidSyncIssueEventResult result = new AndroidSyncIssueEventResult(data);
    assertThat(result.getFailures()).isEmpty();
    assertThat(result.getKind()).isEqualTo(Kind.WARNING);
    assertThat(result.getDetails()).isEqualTo("Warning Text");
  }
}
