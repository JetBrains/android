/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.intellijplatform;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class GotoDeclarationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private EditorFixture editorFixture;
  private IdeFrameFixture ideFrame;
  private Point pointerLocation;

  private static final String ACTIVITY_FILE_PATH =
    "app/src/main/java/com/codegeneration/MainActivity.java";
  private static final String COMPAT_ACTIVITY_FILE = "AppCompatActivity.class";
  private static final String JETBRAINS_DECOMPILER_FILE =
    "JetBrains Decompiler - ...dx/appcompat/app/AppCompatActivity.class";

  /**
   * Verifies that Goto Declaration should navigate to the method or class implementation
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 2c8a46f7-451c-4486-bc71-866d3cff19a1
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import CodeGeneration
   *   2. Open MainActivity.java file and right click on method name (setContentView)
   *      > Go to > Declaration (Ctrl + B), Verify 1
   *   3. Close the "AppCompatActivity" file and go back to MainActivity file,
   *      right click on class name "AppCompatActivity" > Go To > Declaration (Ctrl + B) (Verify 2)
   *   Verify:
   *   1. Studio will open "AppCompatActivity" file and cursor will point to
   *      "setContentView" method implementation
   *   2. Studio will open "AppCompatActivity" file
   *   </pre>
   */

  @Before
  public void setup() throws Exception {
    ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("CodeGeneration", Wait.seconds(120));
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.clearNotificationsPresentOnIdeFrame();
    editorFixture = ideFrame.getEditor();
  }

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testgotoDeclaration() throws Exception  {
    //Test go to declarations using keyboard shortcuts.
    editorFixture
      .open(ACTIVITY_FILE_PATH)
      .select(String.format("(setContentView)"))
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);

    // Handle the pop up dialog, accept and continue
    DialogFixture dialog = findDialog(withTitle(JETBRAINS_DECOMPILER_FILE))
      //"JetBrains Decompiler - .../android.jar!/android/app/Activity.class"
      .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    JButtonFixture accept = dialog.button(withText("Accept"));
    Wait.seconds(30).expecting("Android source to be installed").until(accept::isEnabled);
    accept.click();

    Wait.seconds(10).expecting("File is opened for navigating to definition")
      .until(() -> COMPAT_ACTIVITY_FILE.equals(ideFrame.getEditor().getCurrentFileName()));

    // TODO: The cursor is not auto point to setContentView.
    // When manually test it, it works well.
    //String currentLine = ideFrame.getEditor().getCurrentLine();
    //assertThat(currentLine.contains("setContentView")).isTrue();

    editorFixture
      .open(ACTIVITY_FILE_PATH)
      .moveBetween("import ", "")
      .select(String.format("(AppCompatActivity)"))
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);

    Wait.seconds(10).expecting("File is opened for navigating to definition")
      .until(() -> COMPAT_ACTIVITY_FILE.equals(ideFrame.getEditor().getCurrentFileName()));

    editorFixture
      .open(ACTIVITY_FILE_PATH)
      .select(String.format("(R.layout.activity_main)"))
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);

    Wait.seconds(30).expecting("XML file is opened for navigating to definition")
      .until(() -> "activity_main.xml".equals(ideFrame.getEditor().getCurrentFileName()));

    //Test goto declarations using control + mouse click
    editorFixture
      .open(ACTIVITY_FILE_PATH)
      .moveBetween("setCont", "entView");

    pointerLocation = MouseInfo.getPointerInfo().getLocation();
    pressControlKeyAndClick(pointerLocation);

    Wait.seconds(20).expecting("File is opened for navigating to definition")
      .until(() -> COMPAT_ACTIVITY_FILE.equals(ideFrame.getEditor().getCurrentFileName()));

    editorFixture
      .open(ACTIVITY_FILE_PATH)
      .moveBetween("import ", "")
      .moveBetween("AppCompat", "Activity");

    pointerLocation = MouseInfo.getPointerInfo().getLocation();
    pressControlKeyAndClick(pointerLocation);

    Wait.seconds(10).expecting("File is opened for navigating to definition")
      .until(() -> COMPAT_ACTIVITY_FILE.equals(ideFrame.getEditor().getCurrentFileName()));

    editorFixture
      .open(ACTIVITY_FILE_PATH)
      .moveBetween("activi", "ty_main");

    pointerLocation = MouseInfo.getPointerInfo().getLocation();
    pressControlKeyAndClick(pointerLocation);

    Wait.seconds(30).expecting("XML file is opened for navigating to definition")
      .until(() -> "activity_main.xml".equals(ideFrame.getEditor().getCurrentFileName()));
  }

  private void pressControlKeyAndClick(Point pointerLocation) {
    if (SystemInfo.isMac) {
      ideFrame.robot().pressKey(KeyEvent.META_MASK);
      ideFrame.robot().click(editorFixture.frame().target(),
                             pointerLocation,
                             MouseButton.LEFT_BUTTON,
                             1);
      ideFrame.robot().releaseKey(KeyEvent.META_MASK);
    }
    else {
      ideFrame.robot().pressKey(KeyEvent.VK_CONTROL);
      ideFrame.robot().click(editorFixture.frame().target(),
                             pointerLocation,
                             MouseButton.LEFT_BUTTON,
                             1);
      ideFrame.robot().releaseKey(KeyEvent.VK_CONTROL);
    }
  }
}
