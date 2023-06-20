/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.archcomponents;

import static junit.framework.Assert.assertTrue;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.MouseInfo;
import java.awt.Point;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class RoomComponentTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private static String FILENAME = "app/src/main/java/com/example/roomsampleproject/entity/Word.java";

  private static String SQL_REFERENCE = "Referenced in SQL query";

  private static String  CLASS_NAME = "WordDao";

  private static String METHOD_NAME_1 = "deleteAll";

  private static String METHOD_NAME_2 = "getAlphabetizedWords";

  /**
   * Basic Room Project Deploy/Syntax highlighting.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 4e89ed48-e038-497b-b687-a105a32cc1bc
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import project (Architecture Components Basic). (File -> new -> Import Sample)
   *      - To reduce the time of importing and fixing the plugin version. Created a
   *        RoomSampleApplication with all the required dependencies.
   *   2. Go to versions.gradle file and update "versions.android_gradle_plugin"  to latest plugin.
   *   3. Quick fix all the necessary sdk and imports.
   *   4. Deploy app on emulator/device (Verify 1 & 2)
   *   5. Go to CommentEntity class
   *   6. Right click on the class name and choose "find usages" in context menu (Verify 3)
   *
   *   Verify:
   *   1.Verify code syncs well and deploys.
   *   2.Open "CommentDao" or ProductDao and verify that the SQL queries are highlighted in green
   *      - Green color highlighting is removed from AGP 7.0 and above.
   *   3.Verify that all the SQL queries in the for the comments table are referenced
   *
   *   </pre>
   *
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testRoomComponents() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("RoomSampleProject");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    EditorFixture editor = guiTest.ideFrame()
      .getEditor()
      .open(FILENAME, EditorFixture.Tab.EDITOR);

    editor.select(String.format("public class (Word)"));
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    Point mouse = MouseInfo.getPointerInfo().getLocation();
    guiTest.robot().click(mouse, MouseButton.RIGHT_BUTTON, 1);
    new JPopupMenuFixture(guiTest.robot(), guiTest.robot().findActivePopupMenu())
      .menuItemWithPath("Find Usages")
      .click();
    GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(120));
    guiTest.robot().waitForIdle();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Verify all the SQL query references are displayed in the find tool window
    assertTrue(guiTest.ideFrame().findToolWindowContains(SQL_REFERENCE));
    assertTrue(guiTest.ideFrame().findToolWindowContains(CLASS_NAME));
    assertTrue(guiTest.ideFrame().findToolWindowContains(METHOD_NAME_1));
    assertTrue(guiTest.ideFrame().findToolWindowContains(METHOD_NAME_2));
  }
}
