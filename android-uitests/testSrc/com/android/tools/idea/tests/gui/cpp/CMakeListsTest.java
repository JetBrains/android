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
package com.android.tools.idea.tests.gui.cpp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameFileDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CMakeListsTest {
  @Rule public GuiTestRule guiTest = new GuiTestRule().withTimeout(6, TimeUnit.MINUTES);

  private static final String FILE_PATH = "app/src/main/cpp/";
  private static final String CMAKELISTS_FILE_NAME = "CMakeLists.txt";
  private static final String C_FILE_NAME = "hello-jni.c";
  private static final String RENAMED_C_FILE_NAME = "hello.c";

  /**
   * Verifies that new editing features works in CMakeLists.txt file
   * (Syntax highlighting, refactoring ... ...).
   * <p>
   *   This is run to qualify releases. Please involve the test team in substantial changes.
   * </p>
   *
   * TT ID: c2a1abfd-87a6-48f2-a957-f6d560542e62
   * <p>
   *   <pre>
   *   This feature is for Android Studio 3.2 and above.
   *   Test Steps:
   *   1. Import CMakeListsHelloJni project.
   *   2. Navigate to app/src/main/cpp/CMakeLists.txt, and verify that there is syntax highlighting.
   *   3. Ctrl-click on hello-jni.c, and verify that app/src/main/cpp/hello-jni.c is opened.
   *   4. Navigate back to app/src/main/cpp/CMakeLists.txt.
   *   5. Select hello-jni.c; Right click and select Refactor -> Rename.
   *   6. Rename the file to hello.c, and verify that hello-jni.c file renamed to hello.c.
   *   7. Ctrl-click on hello.c, and verify that app/src/main/cpp/hello.c is opened.
   *   8. Click on the "Sync now" banner, and verify that the gradle build is successful.
   *   </pre>
   * </p>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void checkEditingFeaturesInCMakeListsFile() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/CMakeListsHelloJni");

    ideFrame.getEditor().open(FILE_PATH + CMAKELISTS_FILE_NAME);
    Wait.seconds(10).expecting(CMAKELISTS_FILE_NAME + "file is opened.")
      .until(() -> CMAKELISTS_FILE_NAME.equals(ideFrame.getEditor().getCurrentFileName()));

    // TODO: Syntax highlighting check.

    ideFrame.getEditor().open(FILE_PATH + C_FILE_NAME);
    Wait.seconds(10).expecting(C_FILE_NAME + " file is opened.")
      .until(() -> C_FILE_NAME.equals(ideFrame.getEditor().getCurrentFileName()));

    ProjectViewFixture.PaneFixture paneFixture = ideFrame.getProjectView()
      .selectAndroidPane()
      .expand(45);
    guiTest.waitForBackgroundTasks();
    paneFixture.clickPath("app", "cpp", C_FILE_NAME)
      .invokeMenuPath("Refactor", "Rename...");

    RenameFileDialogFixture.find(ideFrame)
      .enterText("hello")
      .clickRefactor();

    ideFrame.getEditor().open(FILE_PATH + RENAMED_C_FILE_NAME);
    Wait.seconds(10).expecting(RENAMED_C_FILE_NAME + "file is opened.")
      .until(() -> RENAMED_C_FILE_NAME.equals(ideFrame.getEditor().getCurrentFileName()));

    ideFrame.requestProjectSyncAndWaitForSyncToFinish();
  }
}
