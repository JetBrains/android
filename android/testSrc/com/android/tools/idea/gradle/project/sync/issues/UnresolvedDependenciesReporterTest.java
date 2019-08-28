/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.hyperlink.DisableOfflineModeHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowSyncIssuesDetailsHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub.NotificationUpdate;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link UnresolvedDependenciesReporter}.
 */
public class UnresolvedDependenciesReporterTest extends JavaProjectTestCase {
  @Mock private SyncIssue mySyncIssue;
  @Mock private GradleSettings myGradleSettings;

  private GradleSyncMessagesStub mySyncMessages;
  private UnresolvedDependenciesReporter myReporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());
    ServiceContainerUtil.replaceService(getProject(), GradleSettings.class, myGradleSettings, getTestRootDisposable());
    myReporter = new UnresolvedDependenciesReporter();
  }

  public void testReportWithoutDependencyAndExtraInfo() {
    String text = "Hello!";
    String expected = text + "\nAffected Modules: testReportWithoutDependencyAndExtraInfo";
    when(mySyncIssue.getMessage()).thenReturn(text);

    List<String> extraInfo = Arrays.asList("line1", "line2");
    when(mySyncIssue.getMultiLineMessage()).thenReturn(extraInfo);

    myReporter.report(mySyncIssue, getModule(), null);

    List<NotificationData> messages = mySyncMessages.getNotifications();
    assertSize(1, messages);
    NotificationData message = messages.get(0);
    assertEquals(expected, message.getMessage());


    NotificationUpdate update = mySyncMessages.getNotificationUpdate();
    assertSize(1, update.getFixes());
    assertInstanceOf(update.getFixes().get(0), ShowSyncIssuesDetailsHyperlink.class);
  }

  /**
   * Disable offline mode quickfix should be offered when missing dependencies on offline mode
   */
  public void testReportOfflineMode() {
    String text = "Hello!";
    String expected = text + "\nAffected Modules: testReportOfflineMode";
    when(mySyncIssue.getMessage()).thenReturn(text);
    when(mySyncIssue.getMultiLineMessage()).thenReturn(null);
    when(myGradleSettings.isOfflineWork()).thenReturn(true);
    myReporter.report(mySyncIssue, getModule(), null);

    List<NotificationData> messages = mySyncMessages.getNotifications();
    assertSize(1, messages);
    NotificationData message = messages.get(0);
    assertEquals(expected, message.getMessage());

    NotificationUpdate update = mySyncMessages.getNotificationUpdate();
    assertSize(1, update.getFixes());
    assertInstanceOf(update.getFixes().get(0), DisableOfflineModeHyperlink.class);
  }

  /**
   * Disable offline mode quickfix should *NOT* be offered when offline mode is not enabled
   */
  public void testReportNoOfflineMode() {
    String text = "Hello!";
    String expected = text + "\nAffected Modules: testReportNoOfflineMode";
    when(mySyncIssue.getMessage()).thenReturn(text);
    when(mySyncIssue.getMultiLineMessage()).thenReturn(null);
    when(myGradleSettings.isOfflineWork()).thenReturn(false);
    myReporter.report(mySyncIssue, getModule(), null);

    List<NotificationData> messages = mySyncMessages.getNotifications();
    assertSize(1, messages);
    NotificationData message = messages.get(0);
    assertEquals(expected, message.getMessage());

    assertSize(0, message.getRegisteredListenerIds());
  }
}