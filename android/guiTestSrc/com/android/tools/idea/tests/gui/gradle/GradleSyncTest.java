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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.GradleUtil.findWrapperPropertiesFile;
import static com.android.tools.idea.gradle.util.GradleUtil.updateGradleDistributionUrl;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.util.SystemProperties.getLineSeparator;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class GradleSyncTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testUnsupportedGradleVersion() throws IOException {
    // Open the project without updating the version of the plug-in
    File projectPath = setUpProject("OldAndroidPlugin", true, false /* do not update plug-in version to 0.14 */, "1.12");
    IdeFrameFixture projectFrame = openProject(projectPath);

    // Ensure we have an old, unsupported Gradle in the wrapper.
    File wrapperPropertiesFile = findWrapperPropertiesFile(projectFrame.getProject());
    assertNotNull(wrapperPropertiesFile);
    updateGradleDistributionUrl("1.5", wrapperPropertiesFile);

    projectFrame.requestProjectSyncAndExpectFailure();

    MessagesToolWindowFixture.ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture msg = syncMessages.findMessage(ERROR,
                                                  firstLineStartingWith("The project is using an unsupported version of the Android Gradle"));

    HyperlinkFixture hyperlink = msg.findHyperlink("Fix plug-in version and re-import project");
    hyperlink.click();

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
  public void testSyncWithEmptyGradleSettingsFileInMultiModuleProject() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    createEmptyGradleSettingsFile(projectFrame.getProjectPath());

    projectFrame.requestProjectSync();

    // Sync should be successful for multi-module projects with an empty settings.gradle file.
    projectFrame.requestProjectSync()
                .waitForGradleProjectSyncToFinish();
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=76444
  public void testSyncWithEmptyGradleSettingsFileInSingleModuleProject() throws IOException {
    IdeFrameFixture projectFrame = importProject("Basic");

    createEmptyGradleSettingsFile(projectFrame.getProjectPath());

    // Sync should be successful for single-module projects with an empty settings.gradle file.
    projectFrame.requestProjectSync()
                .waitForGradleProjectSyncToFinish();
  }

  private static void createEmptyGradleSettingsFile(@NotNull File projectPath) throws IOException {
    File settingsFilePath = new File(projectPath, FN_SETTINGS_GRADLE);
    delete(settingsFilePath);
    writeToFile(settingsFilePath, " ");
    assertThat(settingsFilePath).isFile();
  }

  @Test @IdeGuiTest
  public void testGradleDslMethodNotFoundInBuildFile() throws IOException {
    final IdeFrameFixture projectFrame = openSimpleApplication();

    File topLevelBuildFile = new File(projectFrame.getProjectPath(), FN_BUILD_GRADLE);
    assertThat(topLevelBuildFile).isFile();
    String content = "asdf()" + getLineSeparator() + loadFile(topLevelBuildFile);
    writeToFile(topLevelBuildFile, content);

    projectFrame.requestProjectSyncAndExpectFailure();

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR,
                                                                         firstLineStartingWith("Gradle DSL method not found: 'asdf()'"));

    final EditorFixture editor = projectFrame.getEditor();
    editor.close();

    // Verify that at least we offer some sort of hint.
    HyperlinkFixture openGradleWrapperFileHyperlink = message.findHyperlink("Open Gradle wrapper file");
    openGradleWrapperFileHyperlink.click();

    Pause.pause(new Condition("Wait for gradle-wrapper.properties is opened") {
      @Override
      public boolean test() {
        VirtualFile currentFile = editor.getCurrentFile();
        return currentFile != null && currentFile.getName().equals("gradle-wrapper.properties");
      }
    }, SHORT_TIMEOUT);
  }

  @Test @IdeGuiTest
  public void testGradleDslMethodNotFoundInSettingsFile() throws IOException {
    final IdeFrameFixture projectFrame = openSimpleApplication();

    File settingsFile = new File(projectFrame.getProjectPath(), FN_SETTINGS_GRADLE);
    assertThat(settingsFile).isFile();
    writeToFile(settingsFile, "incude ':app'");

    projectFrame.requestProjectSyncAndExpectFailure();

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR,
                                                                         firstLineStartingWith("Gradle DSL method not found: 'incude()'"));

    // Ensure the error message contains the location of the error.
    message.requireLocation(settingsFile, 1);
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=76797
  public void testHandlingOfZipFileOpeningError() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    projectFrame.requestProjectSyncAndSimulateFailure("error in opening zip file");

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("Failed to open zip file."));

    message.findHyperlink("Re-download dependencies and sync project (requires network)");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=75520
  public void testConnectionPermissionDeniedError() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    String failure = "Connection to the Internet denied.";
    projectFrame.requestProjectSyncAndSimulateFailure(failure);

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith(failure));

    HyperlinkFixture hyperlink = message.findHyperlink("More details (and potential fix)");
    hyperlink.requireUrl("http://tools.android.com/tech-docs/project-sync-issues-android-studio");
  }

  @Test @IdeGuiTest
  // See https://code.google.com/p/android/issues/detail?id=76984
  public void testDaemonContextMismatchError() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    String failure = "The newly created daemon process has a different context than expected.\n" +
                     "It won't be possible to reconnect to this daemon. Context mismatch: \n" +
                     "Java home is different.\n" +
                     "javaHome=c:\\Program Files\\Java\\jdk,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=7868,idleTimeout=null]\n" +
                     "javaHome=C:\\Program Files\\Java\\jdk\\jre,daemonRegistryDir=C:\\Users\\user.name\\.gradle\\daemon,pid=4792,idleTimeout=10800000]";
    projectFrame.requestProjectSyncAndSimulateFailure(failure);

    Pause.pause(10, TimeUnit.SECONDS);

    MessagesToolWindowFixture messages = projectFrame.getMessagesToolWindow();
    MessageFixture message = messages.getGradleSyncContent().findMessage(ERROR, firstLineStartingWith("The newly created daemon"));

    message.findHyperlink("Open JDK Settings");
  }
}
