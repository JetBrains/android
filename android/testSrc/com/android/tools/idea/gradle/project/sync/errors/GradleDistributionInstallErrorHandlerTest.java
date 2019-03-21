/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.hyperlink.DeleteFileAndSyncHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.StartParameter;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Tests for {@link GradleDistributionInstallErrorHandler}
 */
public class GradleDistributionInstallErrorHandlerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  /**
   * Confirm that error message is processed correctly by displaying the path of the corrupted zip file
   * @throws Exception
   */
  public void testHandleError() throws Exception {
    String expectedNotificationMessage = "Could not install Gradle distribution from 'https://example.org/distribution.zip'.";
    registerSyncErrorToSimulate(expectedNotificationMessage);
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    File zipFile = getDistributionZipFile();

    String notificationText = notificationUpdate.getText();
    assertThat(notificationText).contains(expectedNotificationMessage);
    assertThat(notificationText).contains(zipFile.getPath());

    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(1);

    // Verify hyperlinks are correct.
    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(DeleteFileAndSyncHyperlink.class);
    DeleteFileAndSyncHyperlink deleteHyperlink = (DeleteFileAndSyncHyperlink)quickFix;
    assertThat(deleteHyperlink.getFile()).isEquivalentAccordingToCompareTo(zipFile);
  }

  @NotNull
  private File getDistributionZipFile() {
    WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(getProject().getBasePath());
    PathAssembler.LocalDistribution localDistribution = new PathAssembler(
      StartParameter.DEFAULT_GRADLE_USER_HOME).getDistribution(wrapperConfiguration);
    File zip = localDistribution.getZipFile();
    try {
      zip = zip.getCanonicalFile();
    }
    catch (IOException ignored) {

    }
    return zip;
  }
}