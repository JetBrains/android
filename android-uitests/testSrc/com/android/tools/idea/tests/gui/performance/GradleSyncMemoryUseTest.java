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

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.bleak.UseBleak;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @Test
  @UseBleak
  public void changeCompileSdkVersion() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    guiTest.runWithBleak(() -> {
      String currentVersion = String.valueOf(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API);
      String previousVersion = String.valueOf(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API - 1);
      ideFrameFixture
        .actAndWaitForGradleProjectSyncToFinish(
          it ->
            it.getEditor()
              .open("app/build.gradle")
              .select("compileSdkVersion (" + currentVersion + ")")
              .enterText(previousVersion)
              .awaitNotification(
                "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
              .performAction("Sync Now")
        )
        .actAndWaitForGradleProjectSyncToFinish(
          it ->
            it.getEditor()
              .select("compileSdkVersion (" + previousVersion + ")")
              .enterText(currentVersion)
              .awaitNotification(
                "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
              .performAction("Sync Now")
        );
    });
  }

  @Test
  @UseBleak
  public void changeCompileSdkVersionFail() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    guiTest.runWithBleak(() -> {
      String currentVersion = String.valueOf(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API);
      ideFrameFixture
        .actAndWaitForGradleProjectSyncToFinish(
          it ->
            it.getEditor()
              .open("app/build.gradle")
              .select("compileSdkVersion (" + currentVersion + ")")
              .enterText("-100")
              .awaitNotification(
                "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
              .performAction("Sync Now")
        )
        .actAndWaitForGradleProjectSyncToFinish(
          it ->
            it.getEditor()
              .select("compileSdkVersion (-100)")
              .enterText(currentVersion)
              .awaitNotification(
                "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
              .performAction("Sync Now")
        );
    });
  }

  private static String getAppcompatDependencyString(IdeFrameFixture ideFrameFixture) throws IOException {
    String appGradleContents =
      new String(ideFrameFixture.findFileByRelativePath("app/build.gradle").contentsToByteArray(), StandardCharsets.UTF_8);
    Matcher matcher = Pattern.compile("'androidx\\.appcompat:appcompat:.*'").matcher(appGradleContents);
    matcher.find();
    return matcher.group();
  }

  @Test
  @UseBleak
  public void changeDependency() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    String appcompatDependencyString = getAppcompatDependencyString(ideFrameFixture);
    guiTest.runWithBleak(() -> {
      ideFrameFixture
        .actAndWaitForGradleProjectSyncToFinish(
          it ->
            it.getEditor()
              .open("app/build.gradle")
              .select("implementation (" + appcompatDependencyString + ")")
              .enterText("'com.android.support:design:28.0.0'")
              .awaitNotification(
                "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
              .performAction("Sync Now")
        )
        .actAndWaitForGradleProjectSyncToFinish(
          it ->
            it.getEditor()
              .select("implementation ('com.android.support:design:28.0.0')")
              .enterText(appcompatDependencyString)
              .awaitNotification(
                "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
              .performAction("Sync Now")
        );
    });
  }

  @Test
  @UseBleak
  public void changeDependencyFailed() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    String appcompatDependencyString = getAppcompatDependencyString(ideFrameFixture);
    guiTest.runWithBleak(() -> {
      ideFrameFixture
        .actAndWaitForGradleProjectSyncToFinish(
          it ->
            it.getEditor()
              .open("app/build.gradle")
              .select("implementation (" + appcompatDependencyString + ")")
              .enterText("'com.android.support:design123'")
              .awaitNotification(
                "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
              .performAction("Sync Now")
        )
        .actAndWaitForGradleProjectSyncToFinish(
          it ->
            it.getEditor()
              .select("implementation ('com.android.support:design123')")
              .enterText(appcompatDependencyString)
              .awaitNotification(
                "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
              .performAction("Sync Now")
        );
    });
  }

  @Test
  @UseBleak
  public void rebuildProject() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    guiTest.runWithBleak(() -> ideFrameFixture.invokeAndWaitForBuildAction("Build", "Rebuild Project"));
  }
}
