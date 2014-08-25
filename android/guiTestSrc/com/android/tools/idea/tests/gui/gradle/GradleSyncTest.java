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
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import org.fest.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static com.android.SdkConstants.FN_GRADLE_PROPERTIES;
import static com.android.tools.idea.gradle.util.GradleUtil.findWrapperPropertiesFile;
import static com.android.tools.idea.gradle.util.GradleUtil.updateGradleDistributionUrl;
import static com.android.tools.idea.gradle.util.PropertiesUtil.savePropertiesToFile;
import static org.junit.Assert.assertNotNull;

public class GradleSyncTest extends GuiTestCase {
  @Test @IdeGuiTest @Ignore
  public void testUnsupportedGradleVersion() throws IOException {
    IdeFrameFixture projectFrame = openProject("SimpleApplication");

    // Ensure we have an old, unsupported Gradle in the wrapper.
    File wrapperPropertiesFile = findWrapperPropertiesFile(projectFrame.getProject());
    assertNotNull(wrapperPropertiesFile);
    updateGradleDistributionUrl("1.5", wrapperPropertiesFile);

    projectFrame.requestProjectSyncAndExpectFailure();

    final String prefix = "You are using an unsupported version of Gradle";

    MessagesToolWindowFixture.ContentFixture syncMessages = projectFrame.getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message =
      syncMessages.findMessage(ErrorTreeElementKind.ERROR, new MessagesToolWindowFixture.TextMatcher() {
        @Override
        public boolean matches(@NotNull String[] text) {
          return text[0].startsWith(prefix);
        }

        @Override
        public String toString() {
          return "starting with " + Strings.quote(prefix);
        }
      });

    message.clickHyperlink("Fix Gradle wrapper and re-import project");

    projectFrame.waitForGradleProjectSyncToFinish();
  }

  // See https://code.google.com/p/android/issues/detail?id=75060
  @Test @IdeGuiTest
  public void testUnableToStartDaemon() throws IOException {
    IdeFrameFixture projectFrame = openProject("SimpleApplication");

    // Force a sync failure by allocating not enough memory for the Gradle daemon.
    Properties gradleProperties = new Properties();
    gradleProperties.setProperty("org.gradle.jvmargs", "-Xms8m -Xmx24m -XX:MaxPermSize=8m");
    File gradlePropertiesFilePath = new File(projectFrame.getProjectPath(), FN_GRADLE_PROPERTIES);
    savePropertiesToFile(gradleProperties, gradlePropertiesFilePath, null);

    projectFrame.requestProjectSync().waitForGradleProjectSyncToFail();
  }
}
