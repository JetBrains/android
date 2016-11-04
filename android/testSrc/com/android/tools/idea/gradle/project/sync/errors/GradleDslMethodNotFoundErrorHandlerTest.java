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

import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleDslMethodNotFoundErrorHandler}.
 */
public class GradleDslMethodNotFoundErrorHandlerTest extends AndroidGradleTestCase {
  private SyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = SyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testHandleError() throws Exception {
    File buildFile = new File(getProject().getBasePath(), FN_BUILD_GRADLE);
    registerSyncErrorToSimulate(new GradleDslErrorMock("Could not find method myMethod "), buildFile);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    SyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains("Gradle DSL method not found: 'myMethod'");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(3);

    assertThat(quickFixes.get(0)).isInstanceOf(NotificationHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(NotificationHyperlink.class);
    assertThat(quickFixes.get(2)).isInstanceOf(FixAndroidGradlePluginVersionHyperlink.class);
  }

  // Simulate error with type org.gradle.api.internal.MissingMethodException
  protected class GradleDslErrorMock extends Throwable {
    public GradleDslErrorMock(String message) {
      super(message);
    }

    @Override
    public String toString() {
      String classname = "org.gradle.api.internal.MissingMethodException";
      String message = getLocalizedMessage();
      return (message != null) ? (classname + ": " + message) : classname;
    }
  }
}