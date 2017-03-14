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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.android.tools.idea.project.AndroidNotificationStub;
import com.android.tools.idea.project.AndroidNotificationStub.NotificationMessage;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.Calendar;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.notification.NotificationType.INFORMATION;
import static java.util.Calendar.MONTH;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests {@link ExpiredPreviewBuildSetupStep}.
 */
public class ExpiredPreviewBuildSetupStepIdeaTest extends IdeaTestCase {
  @Mock private ApplicationInfo myApplicationInfo;

  private AndroidNotificationStub myNotification;
  private ExpiredPreviewBuildSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myNotification = AndroidNotificationStub.replaceSyncMessagesService(getProject());
    mySetupStep = new ExpiredPreviewBuildSetupStep(myApplicationInfo);
  }

  public void testSetUpProjectWithExpiredPreview() {
    when(myApplicationInfo.getFullVersion()).thenReturn("1.2 Preview");
    when(myApplicationInfo.getBuildDate()).thenReturn(simulateExpiredBuildDate());

    mySetupStep.setUpProject(getProject(), null);

    List<NotificationMessage> messages = myNotification.getMessages();
    assertThat(messages).hasSize(1);

    NotificationMessage message = messages.get(0);
    assertEquals("Old Preview Build", message.getTitle());
    assertEquals("This preview build (1.2 Preview) is old; please update to a newer preview or a stable version.", message.getText());
    assertEquals(INFORMATION, message.getType());

    NotificationHyperlink[] hyperlinks = message.getHyperlinks();
    assertThat(hyperlinks).hasLength(1);

    NotificationHyperlink hyperlink = hyperlinks[0];
    assertThat(hyperlink).isInstanceOf(OpenUrlHyperlink.class);

    assertTrue(mySetupStep.isExpirationChecked());
  }

  public void testSetUpProjectWithNotExpiredPreview() {
    when(myApplicationInfo.getFullVersion()).thenReturn("1.2 Preview");
    // Not expired yet.
    when(myApplicationInfo.getBuildDate()).thenReturn(Calendar.getInstance());

    mySetupStep.setUpProject(getProject(), null);

    List<NotificationMessage> messages = myNotification.getMessages();
    assertThat(messages).isEmpty();

    assertFalse(mySetupStep.isExpirationChecked());
  }

  public void testSetUpProjectWithAlreadyCheckedExpiredPreview() {
    when(myApplicationInfo.getFullVersion()).thenReturn("1.2 Preview");
    when(myApplicationInfo.getBuildDate()).thenReturn(simulateExpiredBuildDate());

    mySetupStep.setUpProject(getProject(), null);
    mySetupStep.setUpProject(getProject(), null);

    // should be checked once only.
    verify(myApplicationInfo, times(1)).getFullVersion();
  }

  @NotNull
  private static Calendar simulateExpiredBuildDate() {
    Calendar expirationDate = Calendar.getInstance();
    expirationDate.add(MONTH, -3);
    return expirationDate;
  }

  public void testSetUpProjectWithNonPreview() {
    when(myApplicationInfo.getFullVersion()).thenReturn("1.2");

    mySetupStep.setUpProject(getProject(), null);

    List<NotificationMessage> messages = myNotification.getMessages();
    assertThat(messages).isEmpty();

    assertFalse(mySetupStep.isExpirationChecked());
  }

  public void testInvokeOnFailedSync() {
    assertTrue(mySetupStep.invokeOnFailedSync());
  }
}