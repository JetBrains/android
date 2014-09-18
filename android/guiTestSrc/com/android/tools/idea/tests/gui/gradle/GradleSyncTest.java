/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessageDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.util.GradleUtil.findWrapperPropertiesFile;
import static com.android.tools.idea.gradle.util.GradleUtil.updateGradleDistributionUrl;
import static com.android.tools.idea.gradle.util.Projects.lastGradleSyncFailed;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.android.tools.idea.tests.gui.gradle.GradleSyncUtil.findGradleSyncMessageDialog;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static com.intellij.openapi.util.io.FileUtil.delete;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GradleSyncTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testUnsupportedGradleVersion() throws IOException {
    // Open the project without updating the version of the plug-in
    File projectPath = setUpProject("OldAndroidPlugin", true, false /* do not update plug-in version to 0.13 */, "1.12");
    IdeFrameFixture projectFrame = openProject(projectPath);

    // Ensure we have an old, unsupported Gradle in the wrapper.
    File wrapperPropertiesFile = findWrapperPropertiesFile(projectFrame.getProject());
    assertNotNull(wrapperPropertiesFile);
    updateGradleDistributionUrl("1.5", wrapperPropertiesFile);

    projectFrame.requestProjectSync();

    // Expect message asking for updating wrapper with supported Gradle version.
    findGradleSyncMessageDialog(myRobot).clickOk();

    projectFrame.waitForGradleProjectSyncToFinish();
  }

  // See https://code.google.com/p/android/issues/detail?id=75060
  @Test @IdeGuiTest
  public void testHandlingOfOutOfMemoryErrors() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    // Force a sync failure by allocating not enough memory for the Gradle daemon.
    Properties gradleProperties = new Properties();
    gradleProperties.setProperty("org.gradle.jvmargs", "-XX:MaxHeapSize=8m");
    File gradlePropertiesFilePath = new File(projectFrame.getProjectPath(), FN_GRADLE_PROPERTIES);
    savePropertiesToFile(gradleProperties, gradlePropertiesFilePath, null);

    projectFrame.requestProjectSyncAndExpectFailure();

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Out of memory"));

    // Verify that at least we offer some sort of hint.
    HyperlinkFixture hyperlink = message.findHyperlink("Read Gradle's configuration guide");
    hyperlink.requireUrl("http://www.gradle.org/docs/current/userguide/build_environment.html");
  }

  // See https://code.google.com/p/android/issues/detail?id=73872
  @Test @IdeGuiTest
  public void testHandlingOfClassLoadingErrors() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("Unable to load class 'com.android.utils.ILogger'");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Unable to load class"));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
    message.findHyperlink("Stop Gradle daemons and sync project");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=72556
  public void testHandlingOfUnexpectedEndOfBlockData() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("unexpected end of block data");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("An unexpected I/O error occurred."));

    message.findHyperlink("Build Project");
    message.findHyperlink("Open Android SDK Manager");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=66880
  public void testAutomaticCreationOfMissingWrapper() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.deleteGradleWrapper()
                .requestProjectSync()
                .waitForGradleProjectSyncToFinish()
                .requireGradleWrapperSet();
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=72294
  public void testSyncWithEmptyGradleSettingsFile() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    File settingsFilePath = new File(projectFrame.getProjectPath(), FN_SETTINGS_GRADLE);
    delete(settingsFilePath);
    writeToFile(settingsFilePath, " ");
    assertThat(settingsFilePath).isFile();

    projectFrame.requestProjectSync();

    MessageDialogFixture messageDialog = findGradleSyncMessageDialog(myRobot);
    String message = messageDialog.getMessage();
    assertThat(message).startsWith("Unable to sync project with Gradle.");
    messageDialog.clickOk();

    assertTrue(lastGradleSyncFailed(projectFrame.getProject()));
  }
}
