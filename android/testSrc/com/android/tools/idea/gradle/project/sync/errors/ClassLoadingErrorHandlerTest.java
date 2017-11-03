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
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub.NotificationUpdate;
import static com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub.replaceSyncMessagesService;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link ClassLoadingErrorHandler}.
 */
public class ClassLoadingErrorHandlerTest extends AndroidGradleTestCase {

  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = replaceSyncMessagesService(getProject());
  }

  public void testHandleErrorWhenClassNotLoaded() throws Exception {
    assertErrorAndHyperlinksDisplayed(new ClassNotFoundException("java.util.List not found"));
  }

  public void testHandleErrorWhenMethodNotFound() throws Exception {
    assertErrorAndHyperlinksDisplayed(new NoSuchMethodError("org.slf4j.spi.LocationAwareLogger.log"));
  }

  public void testHandleErrorWhenClassCannotBeCast() throws Exception {
    assertErrorAndHyperlinksDisplayed(
      new Throwable("Cause: org.slf4j.impl.JDK14LoggerFactory cannot be cast to ch.qos.logback.classic.LoggerContext"));
  }

  private void assertErrorAndHyperlinksDisplayed(@NotNull Throwable cause) throws Exception {
    registerSyncErrorToSimulate(cause);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    String message = notificationUpdate.getText();
    assertThat(message).contains("Some versions of JDK 1.7 (e.g. 1.7.0_10) may cause class loading errors in Gradle");
    assertThat(message).contains("Re-download dependencies and sync project");
    assertThat(message)
      .contains("In the case of corrupt Gradle processes, you can also try closing the IDE and then killing all Java processes.");

    boolean restartCapable = ApplicationManager.getApplication().isRestartCapable();
    String quickFixText = restartCapable ? "Stop Gradle build processes (requires restart)" : "Open Gradle Daemon documentation";
    assertTrue(message.contains(quickFixText));

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(2);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(SyncProjectWithExtraCommandLineOptionsHyperlink.class);
    quickFix = quickFixes.get(1);
    assertThat(quickFix).isInstanceOf(OpenUrlHyperlink.class);
  }
}