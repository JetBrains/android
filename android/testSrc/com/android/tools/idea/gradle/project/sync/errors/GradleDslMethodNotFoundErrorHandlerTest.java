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
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.util.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.loadFile;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.util.SystemProperties.getLineSeparator;

/**
 * Tests for {@link GradleDslMethodNotFoundErrorHandler}.
 */
public class GradleDslMethodNotFoundErrorHandlerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testHandleErrorWithMethodNotFoundInSettingsFile() throws Exception {
    loadSimpleApplication();

    File settingsFile = new File(getBaseDirPath(getProject()), FN_SETTINGS_GRADLE);
    assertAbout(file()).that(settingsFile).isFile();
    writeToFile(settingsFile, "incude ':app'");

    requestSyncAndGetExpectedFailure();

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains("Gradle DSL method not found: 'incude()'");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(1);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(OpenFileHyperlink.class);

    // Ensure the error message contains the location of the error.
    OpenFileHyperlink openFileQuickFix = (OpenFileHyperlink)quickFix;
    assertEquals(toSystemIndependentName(settingsFile.getPath()), openFileQuickFix.getFilePath());
    assertEquals(0, openFileQuickFix.getLineNumber());
  }

  public void testHandleErrorWithMethodNotFoundInBuildFile() throws Exception {
    loadSimpleApplication();

    File topLevelBuildFile = new File(getBaseDirPath(getProject()), FN_BUILD_GRADLE);
    assertAbout(file()).that(topLevelBuildFile).isFile();
    String content = "asdf()" + getLineSeparator() + loadFile(topLevelBuildFile);
    writeToFile(topLevelBuildFile, content);

    requestSyncAndGetExpectedFailure();

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains("Gradle DSL method not found: 'asdf()'");

    // Verify hyperlinks are correct.
    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(3);

    assertThat(quickFixes.get(0)).isInstanceOf(NotificationHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(NotificationHyperlink.class);
    assertThat(quickFixes.get(2)).isInstanceOf(FixAndroidGradlePluginVersionHyperlink.class);
  }
}