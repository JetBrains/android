/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ReformatFileTest {
  @Rule public GuiTestRule guiTest = new GuiTestRule().withTimeout(8, TimeUnit.MINUTES);

  private static final Pattern ORDER_OF_VIEWS = Pattern.compile(
    "textView.*switch1.*textView2", Pattern.DOTALL);


  /**
   * Autoformatting in Linear layout
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 04e9e54a-8ebc-473c-8548-5c380165d7bf
   * <p>
   * <pre>
   *   Test Steps:
   *   To eliminate flakiness created a layout file with require views
   *   for the first 3 steps
   *   1. Add a textview in the linearlayout.
   *   2. Add a switch below the textview
   *   3. Add a textview below the the switch
   *   4. Reformat using keyboard shortcuts Press Ctrl/CMD + Alt + Shift + L
   *
   *   Verify:
   *   1. Verify that the order of the elements in the above are in the same order after the auto formatting
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void reformatFileTest() throws Exception {
    String fileContent = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/linear_layout.xml", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();

    assertThat(fileContent).containsMatch(ORDER_OF_VIEWS);

    KeyPressInfo keyPressInfo;
    if (SystemInfo.isMac) {
      // command + option + shift + l
      keyPressInfo = KeyPressInfo.keyCode(KeyEvent.VK_L)
        .modifiers(InputEvent.META_MASK, InputEvent.ALT_MASK, InputEvent.SHIFT_MASK);
    }
    else {
      // ctrl + alt + shift + l
      keyPressInfo = KeyPressInfo.keyCode(KeyEvent.VK_L)
        .modifiers(InputEvent.CTRL_MASK, InputEvent.ALT_MASK, InputEvent.SHIFT_MASK);
    }

    guiTest.ideFrame()
      .getEditor()
      .pressAndReleaseKey(keyPressInfo);


    guiTest.ideFrame()
      .waitForDialog("Reformat File: linear_layout.xml")
      .dialog()
      .button(JButtonMatcher.withText("Run"))
      .click();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertThat(fileContent).containsMatch(ORDER_OF_VIEWS);
  }
}
