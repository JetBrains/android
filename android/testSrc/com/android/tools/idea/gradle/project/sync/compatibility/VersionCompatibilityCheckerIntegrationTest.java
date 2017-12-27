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
package com.android.tools.idea.gradle.project.sync.compatibility;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;

import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;

/**
 * Tests for {@link VersionCompatibilityChecker}.
 */
public class VersionCompatibilityCheckerIntegrationTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testDummy() {
  }

  // Disabled. It runs locally but in CI fails with the error:
  // "No cached version of com.android.tools.build:gradle:2.1.2 available for offline mode."
  // This is something that needs to be fixed in the CI server.
  public void /*test*/CheckGradle2_14_1AndPlugin2_1_2() throws Exception {
    loadSimpleApplication();

    Project project = getProject();
    AndroidPluginVersionUpdater.UpdateResult updateResult =
      AndroidPluginVersionUpdater.getInstance(project).updatePluginVersion(GradleVersion.parse("2.1.2"), null);

    assertTrue(updateResult.isPluginVersionUpdated());
    assertTrue(updateResult.versionUpdateSuccess());

    requestSyncAndWait();

    String expectedError = "Gradle 2.14.1 requires Android Gradle plugin 2.1.3 (or newer) but project is using version 2.1.2.";
    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasType(ERROR)
                                            .hasMessageLine(expectedError, 0);
    // @formatter:on

    GradleSyncSummary summary = GradleSyncState.getInstance(project).getSummary();
    assertTrue(summary.hasSyncErrors());
  }
}