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
import com.android.tools.idea.gradle.project.sync.setup.post.project.SdkToolsVersionSetupStep.InstallSdkToolsHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.sdk.VersionCheck.MIN_TOOLS_REV;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.notification.NotificationType.INFORMATION;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SdkToolsVersionSetupStep}.
 */
public class SdkToolsVersionSetupStepTest extends IdeaTestCase {
  @Mock private IdeSdks myIdeSdks;

  private AndroidNotificationStub myNotification;
  private SdkToolsVersionSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myNotification = AndroidNotificationStub.replaceSyncMessagesService(getProject());
    mySetupStep = new SdkToolsVersionSetupStep(myIdeSdks);
  }

  public void testSetUpProject() {
    when(myIdeSdks.getAndroidSdkPath()).thenReturn(new File("fakePath"));

    mySetupStep.setUpProject(getProject(), null);

    List<NotificationMessage> messages = myNotification.getMessages();
    assertThat(messages).hasSize(1);

    NotificationMessage message = messages.get(0);
    assertEquals("Android SDK Tools", message.getTitle());
    assertEquals("Version " + MIN_TOOLS_REV + " or later is required.", message.getText());
    assertEquals(INFORMATION, message.getType());

    NotificationHyperlink[] hyperlinks = message.getHyperlinks();
    assertThat(hyperlinks).hasLength(1);

    NotificationHyperlink hyperlink = hyperlinks[0];
    assertThat(hyperlink).isInstanceOf(InstallSdkToolsHyperlink.class);
    assertEquals(MIN_TOOLS_REV, ((InstallSdkToolsHyperlink)hyperlink).getVersion());

    assertTrue(mySetupStep.isNewSdkVersionToolsInfoAlreadyShown());
  }

  public void testInvokeOnFailedSync() {
    assertTrue(mySetupStep.invokeOnFailedSync());
  }
}