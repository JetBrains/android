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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.WARNING;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UnhandledIssuesReporter}.
 */
public class UnhandledIssueMessageReporterTest extends AndroidGradleTestCase {
  private IdeSyncIssue mySyncIssue;
  private GradleSyncMessagesStub mySyncMessagesStub;
  private UnhandledIssuesReporter myReporter;
  private TestSyncIssueUsageReporter myUsageReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(IdeSyncIssue.class);
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
    myReporter = new UnhandledIssuesReporter();
    myUsageReporter = new TestSyncIssueUsageReporter();
  }

  public void testGetSupportedIssueType() {
    assertEquals(-1, myReporter.getSupportedIssueType());
  }

  public void testReportWithBuildFile() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    Module appModule = TestModuleUtil.findAppModule(getProject());

    String text = "Hello World!";
    String expectedText =
      text + "\nAffected Modules:";
    when(mySyncIssue.getMessage()).thenReturn(text);
    when(mySyncIssue.getSeverity()).thenReturn(IdeSyncIssue.SEVERITY_ERROR);

    VirtualFile buildFile = getGradleBuildFile(appModule);
    myReporter.report(mySyncIssue, appModule, buildFile, myUsageReporter);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals(WARNING, message.getNotificationCategory());
    assertThat(message.getMessage()).contains(expectedText);

    assertThat(message.getNavigatable()).isInstanceOf(OpenFileDescriptor.class);
    OpenFileDescriptor navigatable = (OpenFileDescriptor)message.getNavigatable();
    assertEquals(buildFile, navigatable.getFile());

    VirtualFile file = ((OpenFileDescriptor)message.getNavigatable()).getFile();
    assertSame(buildFile, file);

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE).build()),
      myUsageReporter.getCollectedIssue());
  }

  public void testReportWithoutBuildFile() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    Module appModule = TestModuleUtil.findAppModule(getProject());

    String text = "Hello World!";
    String expectedText = text + "\nAffected Modules: app";
    when(mySyncIssue.getMessage()).thenReturn(text);
    when(mySyncIssue.getSeverity()).thenReturn(IdeSyncIssue.SEVERITY_ERROR);

    myReporter.report(mySyncIssue, appModule, null, myUsageReporter);


    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals(WARNING, message.getNotificationCategory());
    assertEquals(expectedText, message.getMessage());

    assertNull(message.getNavigatable());

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE).build()),
      myUsageReporter.getCollectedIssue());
  }
}