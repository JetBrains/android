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

import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenPluginBuildFileHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.PLUGIN_IN_APP;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OldAndroidPluginErrorHandler}.
 */
public class OldAndroidPluginErrorHandlerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testIsMatching() {
    // See https://code.google.com/p/android/issues/detail?id=231559
    String text = "The android gradle plugin version 2.3.0-alpha1 is too old, please update to the latest version.";
    assertTrue(OldAndroidPluginErrorHandler.isMatching(text));
  }

  public void testHandleError() throws Exception {
    runTestOnProject(SIMPLE_APPLICATION, new File(getProjectFolderPath(), FN_BUILD_GRADLE));
  }

  public void testHandleErrorPluginSetInApp() throws Exception {
    runTestOnProject(PLUGIN_IN_APP, new File(new File(getProjectFolderPath(), "app"), FN_BUILD_GRADLE));
  }

  private void runTestOnProject(@NotNull String projectPath, @NotNull File expectedHyperlinkValue) throws Exception {
    loadProject(projectPath);

    String expectedNotificationMessage = "Plugin is too old, please update to a more recent version";
    registerSyncErrorToSimulate(expectedNotificationMessage);

    requestSyncAndGetExpectedFailure();

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).contains(expectedNotificationMessage);

    List<NotificationHyperlink> quickFixes = notificationUpdate.getFixes();
    assertThat(quickFixes).hasSize(2);

    // Verify hyperlinks are correct.
    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(FixAndroidGradlePluginVersionHyperlink.class);

    NotificationHyperlink gotoFile = quickFixes.get(1);
    assertThat(gotoFile).isInstanceOf(OpenFileHyperlink.class);
    assertThat(new File(((OpenFileHyperlink)gotoFile).getFilePath())).isEqualTo(expectedHyperlinkValue);
  }

  public void testHandleErrorNotInitialized() throws Exception {
    OldAndroidPluginErrorHandler errorHandler = new OldAndroidPluginErrorHandler();
    loadProject(SIMPLE_APPLICATION);
    Project spyProject = spy(getProject());
    when(spyProject.isInitialized()).thenReturn(false);
    List<NotificationHyperlink> quickFixes = errorHandler.getQuickFixHyperlinks(spyProject, "Error text");
    assertSize(2, quickFixes);
    assertThat(quickFixes.get(0)).isInstanceOf(FixAndroidGradlePluginVersionHyperlink.class);
    assertThat(quickFixes.get(1)).isInstanceOf(OpenPluginBuildFileHyperlink.class);
  }
}
