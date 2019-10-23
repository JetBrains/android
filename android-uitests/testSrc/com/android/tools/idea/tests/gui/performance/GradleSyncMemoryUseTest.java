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
package com.android.tools.idea.tests.gui.performance;

import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.Bleak;
import com.android.tools.idea.tests.gui.framework.heapassertions.bleak.UseBleak;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The test execute several basic scenario of gradle sync making use of BLeak - memory leak checker.
 * BLeak repeatedly runs the test and capture memory state of each run.
 * At the end, BLeak outputs result based on memory usage collected from each run.
 */

@RunWith(GuiTestRemoteRunner.class)
@RunIn(TestGroup.PERFORMANCE)
public class GradleSyncMemoryUseTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  @UseBleak
  public void changeCompileSdkVersion() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    Bleak.runWithBleak(() -> {
      String currentVersion = String.valueOf(GradleImport.CURRENT_COMPILE_VERSION);
      String previousVersion = String.valueOf(GradleImport.CURRENT_COMPILE_VERSION-1);
      ideFrameFixture.getEditor()
        .open("app/build.gradle")
        .select("compileSdkVersion (" + currentVersion + ")")
        .enterText(previousVersion)
        .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
        .performAction("Sync Now")
        .waitForGradleProjectSyncToFinish()
        .getEditor()
        .select("compileSdkVersion (" + previousVersion + ")")
        .enterText(currentVersion)
        .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
        .performAction("Sync Now")
        .waitForGradleProjectSyncToFinish();
    });
  }

  @Test
  @UseBleak
  public void changeCompileSdkVersionFail() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    Bleak.runWithBleak(() -> {
      String currentVersion = String.valueOf(GradleImport.CURRENT_COMPILE_VERSION);
      ideFrameFixture.getEditor()
        .open("app/build.gradle")
        .select("compileSdkVersion (" + currentVersion + ")")
        .enterText("-100")
        .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
        .performAction("Sync Now")
        .waitForGradleProjectSyncToFail()
        .getEditor()
        .select("compileSdkVersion (-100)")
        .enterText(currentVersion)
        .awaitNotification("Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.")
        .performAction("Try Again")
        .waitForGradleProjectSyncToFinish();
    });
  }

  @Test
  @UseBleak
  public void changeDependency() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    Bleak.runWithBleak(() -> {
      ideFrameFixture.getEditor()
        .open("app/build.gradle")
        .select("implementation ('com.google.guava.guava:18.0')")
        .enterText("'com.android.support:design:28.0.0'")
        .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
        .performAction("Sync Now")
        .waitForGradleProjectSyncToFinish()
        .getEditor()
        .select("implementation ('com.android.support:design:28.0.0')")
        .enterText("'com.google.guava:guava:18.0'")
        .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
        .performAction("Sync Now")
        .waitForGradleProjectSyncToFinish();
    });
  }

  @Test
  @UseBleak
  public void changeDependencyFailed() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    Bleak.runWithBleak(() -> {
      ideFrameFixture.getEditor()
        .open("app/build.gradle")
        .select("implementation ('com.google.guava.guava:18.0')")
        .enterText("'com.android.support:design123'")
        .awaitNotification("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
        .performAction("Sync Now")
        .waitForGradleProjectSyncToFail()
        .getEditor()
        .select("implementation ('com.android.support:design123')")
        .enterText("'com.google.guava:guava:18.0'")
        .awaitNotification("Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.")
        .performAction("Try Again")
        .waitForGradleProjectSyncToFinish();
    });
  }
}
