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
import com.android.tools.idea.gradle.service.notification.hyperlink.DownloadJdk8Hyperlink;
import com.android.tools.idea.sdk.Jdks;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link Jdk8RequiredErrorHandler}.
 */
public class Jdk8RequiredErrorHandlerTest extends SyncErrorHandlerTestCase {
  @NotNull
  @Override
  protected SyncErrorHandler createErrorHandler() {
    return new Jdk8RequiredErrorHandler();
  }

  public void testHandleErrorWithNotificationData() {
    String expectedNotificationMessage = "Please use JDK 8 or newer.";
    UnsupportedClassVersionError myError =
      new UnsupportedClassVersionError("com/android/jack/api/ConfigNotSupportedException : Unsupported major.minor version 52.0");

    // Verify that the error handler was capable of handling the exception.
    boolean result = myErrorHandler.handleError(createExternalSystemException(myError), myNotification, myProject);
    assertTrue(result);

    // Verity that SyncMessages was invoked to update NotificationData.
    String message = myNotification.getMessage();
    assertThat(message).contains(expectedNotificationMessage);

    // Verify hyperlinks are correct.
    Map<String, QuickFixNotificationListener> listenersById = myNotification.getListenersById();
    String url = Jdks.DOWNLOAD_JDK_8_URL;
    assertThat(listenersById).containsKey(url);
    QuickFixNotificationListener listener = listenersById.get(url);
    assertThat(listener.getQuickFix()).isInstanceOf(DownloadJdk8Hyperlink.class);
  }
}
