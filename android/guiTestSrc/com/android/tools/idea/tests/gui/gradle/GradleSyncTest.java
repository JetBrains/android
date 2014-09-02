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
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.HyperlinkFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.tools.idea.gradle.util.GradleUtil.findWrapperPropertiesFile;
import static com.android.tools.idea.gradle.util.GradleUtil.updateGradleDistributionUrl;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static org.junit.Assert.assertNotNull;

public class GradleSyncTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testUnsupportedGradleVersion() throws IOException {
    IdeFrameFixture projectFrame = openSimpleApplication();

    // Ensure we have an old, unsupported Gradle in the wrapper.
    File wrapperPropertiesFile = findWrapperPropertiesFile(projectFrame.getProject());
    assertNotNull(wrapperPropertiesFile);
    updateGradleDistributionUrl("1.5", wrapperPropertiesFile);

    projectFrame.requestProjectSyncAndExpectFailure();

    MessagesToolWindowFixture.ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = syncMessages.findMessage(ERROR, firstLineStartingWith("You are using an unsupported version of Gradle"));

    HyperlinkFixture hyperlink = message.findHyperlink("Fix Gradle wrapper and re-import project");
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
}
