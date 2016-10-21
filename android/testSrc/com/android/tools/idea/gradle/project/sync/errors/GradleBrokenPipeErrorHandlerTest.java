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

import com.android.tools.idea.gradle.notification.QuickFixNotificationListener;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenUrlHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleBrokenPipeErrorHandlerTest}.
 */
public class GradleBrokenPipeErrorHandlerTest extends SyncErrorHandlerTestCase {
  @NotNull
  @Override
  protected SyncErrorHandler createErrorHandler() {
    return new GradleBrokenPipeErrorHandler();
  }

  public void testHandleErrorWithNotificationData() {
    String expectedNotificationMessage = "Broken pipe.\nThe Gradle daemon may be trying to use ipv4 instead of ipv6.";
    ExternalSystemException cause = new ExternalSystemException("Broken pipe");

    // Verify that the error handler was capable of handling the exception.
    boolean result = myErrorHandler.handleError(createExternalSystemException(cause), myNotification, myProject);
    assertTrue(result);

    // Verity that SyncMessages was invoked to update NotificationData.
    String message = myNotification.getMessage();
    assertThat(message).contains(expectedNotificationMessage);

    // Verify hyperlinks are correct.
    Map<String, QuickFixNotificationListener> listenersById = myNotification.getListenersById();
    String url = "https://sites.google.com/a/android.com/tools/knownissues?pli=1#TOC-Gradle-Sync-Failed:-Broken-Pipe";
    assertThat(listenersById).containsKey(url);
    QuickFixNotificationListener listener = listenersById.get(url);
    assertThat(listener.getQuickFix()).isInstanceOf(OpenUrlHyperlink.class);
  }
}
