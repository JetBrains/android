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

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.FAILED_TO_INSTALL_NDK_BUNDLE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.NDK_NOT_CONFIGURED;

import com.android.tools.idea.gradle.project.sync.hyperlink.FixNdkVersionHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.SetNdkDirHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.TestSyncIssueUsageReporter;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link MissingNdkErrorHandler}.
 */
public class MissingNdkErrorHandlerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;
  private TestSyncIssueUsageReporter myUsageReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
    myUsageReporter = TestSyncIssueUsageReporter.replaceSyncMessagesService(getProject());
  }

  public void testHandleErrorWithNdkLicenceMissing() throws Exception {
    String errMsg =
      "Failed to install the following Android SDK packages as some licences have not been accepted. blah blah ndk-bundle NDK blah blah";
    registerSyncErrorToSimulate(errMsg);
    loadProjectAndExpectMissingNdkError(
      "Failed to install the following Android SDK packages as some licences have not been accepted. blah blah ndk-bundle NDK blah blah",
      ImmutableList.of(), FAILED_TO_INSTALL_NDK_BUNDLE);
  }

  public void testHandleErrorWithNdkInstallFailed() throws Exception {
    String errMsg = "Failed to install the following SDK components: blah blah ndk-bundle NDK blah blah";
    registerSyncErrorToSimulate(errMsg);
    loadProjectAndExpectMissingNdkError("Failed to install the following SDK components: blah blah ndk-bundle NDK blah blah",
                                        ImmutableList.of(), FAILED_TO_INSTALL_NDK_BUNDLE);
  }

  public void testHandleErrorWithNdkNotConfigured() throws Exception {
    registerSyncErrorToSimulate("NDK not configured. /some/path");
    loadProjectAndExpectMissingNdkError("NDK not configured.", ImmutableList.of(), NDK_NOT_CONFIGURED);
  }

  public void testHandleErrorWithNdkLocationNotFound() throws Exception {
    registerSyncErrorToSimulate("NDK location not found. Define location with ndk.dir in the local.properties file " +
                                "or with an ANDROID_NDK_HOME environment variable.");
    loadProjectAndExpectMissingNdkError("NDK not configured.", ImmutableList.of(), NDK_NOT_CONFIGURED);
  }

  private void loadProjectAndExpectMissingNdkError(@NotNull String expected,
                                                   @NotNull Collection<AndroidStudioEvent.GradleSyncQuickFix> syncQuickFixes,
                                                   @NotNull AndroidStudioEvent.GradleSyncFailure syncFailure) throws Exception {
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).isEqualTo(expected);

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(1);
    assertThat(quickFixes.get(0)).isInstanceOf(FixNdkVersionHyperlink.class);

    assertEquals(syncFailure, myUsageReporter.getCollectedFailure());
    assertEquals(syncQuickFixes, myUsageReporter.getCollectedQuickFixes());
  }

}