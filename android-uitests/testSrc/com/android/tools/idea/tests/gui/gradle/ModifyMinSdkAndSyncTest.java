/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.backupGlobalGradlePropertiesFile;
import static com.android.tools.idea.tests.gui.gradle.UserGradlePropertiesUtil.restoreGlobalGradlePropertiesFile;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ModifyMinSdkAndSyncTest {
  @Nullable private File myBackupProperties;

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Generate a backup copy of user gradle.properties since some tests in this class make changes to the proxy that could
   * cause other tests to use an incorrect configuration.
   */
  @Before
  public void backupPropertiesFile() {
    myBackupProperties = backupGlobalGradlePropertiesFile();
  }

  /**
   * Restore user gradle.properties file content to what it had before running the tests, or delete if it did not exist.
   */
  @After
  public void restorePropertiesFile() {
    restoreGlobalGradlePropertiesFile(myBackupProperties);
  }

  /**
   * Verify that the project syncs and gradle file updates after changing the minSdkVersion in the build.gradle file.
   * <p>
   * TT ID: 01d7a0e9-a947-4cd1-a842-17c0b006d3f1
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <pre>
   *   Steps:
   *   1. Import a project.
   *   2. Open build.gradle file for the project and update the min sdk value to 23.
   *   3. Sync the project.
   *   Verify:
   *   Project syncs and minSdk version is updated.
   * </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void modifyMinSdkAndSync() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(120));
    ideFrame.actAndWaitForGradleProjectSyncToFinish(it -> {
      it.getEditor()
        .open("app/build.gradle")
        .select("minSdkVersion (21)")
        .enterText("23")
        .awaitNotification(
          "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
        .performActionWithoutWaitingForDisappearance("Sync Now");
    });
  }
}
