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

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.errors.SdkBuildToolsTooLowErrorHandler;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.JavaProjectTestCase;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.builder.model.SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildToolsTooLowReporter}.
 */
public class BuildToolsTooLowReporterTest extends JavaProjectTestCase {
  @Mock private SyncIssue mySyncIssue;
  @Mock private SdkBuildToolsTooLowErrorHandler myErrorHandler;
  private GradleSyncMessagesStub mySyncMessages;
  private BuildToolsTooLowReporter myIssueReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    initMocks(this);
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());
    myIssueReporter = new BuildToolsTooLowReporter(myErrorHandler);
  }

  public void testGetSupportedIssueType() {
    assertSame(TYPE_BUILD_TOOLS_TOO_LOW, myIssueReporter.getSupportedIssueType());
  }

  public void testReport() {
    String text = "Upgrade Build Tools!";
    when(mySyncIssue.getMessage()).thenReturn(text);

    String minVersion = "25";
    when(mySyncIssue.getData()).thenReturn(minVersion);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    Module module = getModule();
    List<NotificationHyperlink> quickFixes = new ArrayList<>();
    quickFixes.add(mock(NotificationHyperlink.class));

    when(myErrorHandler.getQuickFixHyperlinks(minVersion, ImmutableList.of(module), ImmutableMap.of()))
      .thenReturn(quickFixes);

    myIssueReporter.report(mySyncIssue, module, null);

    List<NotificationData> messages = mySyncMessages.getNotifications();
    assertThat(messages).hasSize(1);

    NotificationData message = messages.get(0);

    assertEquals(NotificationCategory.ERROR, message.getNotificationCategory());
    assertEquals("Upgrade Build Tools!\nAffected Modules: testReport", message.getMessage());

    assertEquals(quickFixes, mySyncMessages.getNotificationUpdate().getFixes());
  }
}