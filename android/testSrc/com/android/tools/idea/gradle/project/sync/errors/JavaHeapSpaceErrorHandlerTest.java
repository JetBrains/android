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
import com.android.tools.idea.testing.AndroidGradleTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link JavaHeapSpaceErrorHandler}.
 */
public class JavaHeapSpaceErrorHandlerTest extends AndroidGradleTestCase {

  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testHandleErrorWithLongMessage() throws Exception {
    String longMessage = "Unable to start the daemon process.\n" +
                         "This problem might be caused by incorrect configuration of the daemon.\n" +
                         "For example, an unrecognized jvm option is used.\n" +
                         "Please refer to the user guide chapter on the daemon at http://gradle.org/docs/1.12/userguide/gradle_daemon.html\n" +
                         "Please read below process output to find out more:\n" +
                         "-----------------------\n" +
                         "Error occurred during initialization of VM\n" +
                         "Could not reserve enough space for object heap\n" +
                         "Error: Could not create the Java Virtual Machine.\n" +
                         "Error: A fatal exception has occurred. Program will exit.";
    assertErrorAndHyperlinksDisplayed(longMessage, "Unable to start the daemon process: could not reserve enough space for object heap.");
  }

  public void testHandleErrorWithShortMessage() throws Exception {
    assertErrorAndHyperlinksDisplayed("Out of memory: Java heap space", "Out of memory: Java heap space");
  }

  private void assertErrorAndHyperlinksDisplayed(@NotNull String errorMessage, @NotNull String expectedMessage) throws Exception {
    registerSyncErrorToSimulate(errorMessage);

    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains(expectedMessage);

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(2);
    assertThat(quickFixes.get(0)).isInstanceOf(OpenUrlHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(OpenUrlHyperlink.class);
  }
}