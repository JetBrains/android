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

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction.BACK_SPACE;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class GradleEditNotifyTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testEditNotify() throws IOException {
    // Edit a build.gradle file and ensure that you are immediately notified that
    // the build.gradle model is out of date
    // Regression test for https://code.google.com/p/android/issues/detail?id=75983

    myProjectFrame = importSimpleApplication();
    EditorFixture editor = myProjectFrame.getEditor();
    editor.open("app/build.gradle").waitUntilErrorAnalysisFinishes();

    // When done and a successful gradle sync there should not be any messages
    myProjectFrame.requireNoEditorNotification();

    // Insert:
    editor.moveTo(editor.findOffset("versionCode ", null, true))
          .enterText("1");

    // Sync:
    myProjectFrame.requireEditorNotification("Gradle files have changed since last project sync").performAction("Sync Now");
    myProjectFrame.waitForGradleProjectSyncToFinish()
                  .requireNoEditorNotification();

    editor.invokeAction(BACK_SPACE);
    myProjectFrame.requireEditorNotification("Gradle files have changed since last project sync");
  }
}
