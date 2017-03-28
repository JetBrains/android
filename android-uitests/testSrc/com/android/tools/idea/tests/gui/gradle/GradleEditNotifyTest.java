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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction.BACK_SPACE;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class GradleEditNotifyTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testEditNotify() throws IOException {
    // Edit a build.gradle file and ensure that you are immediately notified that
    // the build.gradle model is out of date
    // Regression test for https://code.google.com/p/android/issues/detail?id=75983

    guiTest.importSimpleApplication()
      .getEditor()
      .open("app/build.gradle").waitUntilErrorAnalysisFinishes()
      .checkNoNotification()
      .moveBetween("versionCode ", "")
      .enterText("1")
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now")
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .checkNoNotification()
      .invokeAction(BACK_SPACE)
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.");
  }
}
