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
package com.android.tools.idea.tests.gui.editors;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.util.Ref;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class SurroundWithShortcutTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private static String HELLO_STR = "String hello = \"hello\";";
  private static String STATEMENT_BEFORE = "setContentView(R.layout.activity_my);";
  private static String TRY_CATCH_BLOCK =
    "try {\n" +
    "            String hello = \"hello\";\n" +
    "        } catch (Exception e) {\n" +
    "            throw new RuntimeException(e);\n" +
    "        }";
  private static String DO_WHILE_BLOCK =
    "do {\n" +
    "            String hello = \"hello\";\n" +
    "        } while (true);";

  /**
   * Verifies that surround with shortcut can be used to wrap block of code in some structure, like if-else or for each.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 98badec4-c84d-4b8a-98cb-f0d87322527d
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication project.
   *   2. In java class file, add a line of code with a variable assignment. Eg. String hello = "hello".
   *   3. Click on Code > Surround With.
   *   4. Click on any of the available option, say try/catch (Verify 1).
   *   5. Select the line of code using the mouse and then click on Code > Unwrap/Remove (Verify 2).
   *   6. Repeat step 2.
   *   7. On the keyboard, type the keyboard shortcut for any of the options, eg "4" for do/while (Verify 3).
   *   Expectations:
   *   1. The code is wrapped inside try/catch loop.
   *   2. The try/catch wrap is removed.
   *   3. The code is wrapped inside a do/while loop.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAT_BAZEL)
  public void surroundWithShortcut() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    EditorFixture editorFixture = ideFrame.getEditor().open("/app/src/main/java/google/simpleapplication/MyActivity.java");
    editorFixture.waitForFileToActivate();
    editorFixture.moveBetween(STATEMENT_BEFORE, "")
      .moveBetween(STATEMENT_BEFORE, "") //To reduce flakiness
      .typeText("\n" + HELLO_STR)
      .invokeAction(EditorFixture.EditorAction.SAVE);

    // Test Shortcut: "6" for try / catch
    clickCodeSurroundWith(ideFrame, KeyEvent.VK_6);
    assertThat(editorFixture.getCurrentFileContents()).contains(TRY_CATCH_BLOCK);

    removeTryCatchAndVerify(ideFrame, editorFixture);


    // Test Shortcut: "4" for do/while
    clickCodeSurroundWith(ideFrame, KeyEvent.VK_4);
    assertThat(editorFixture.getCurrentFileContents()).contains(DO_WHILE_BLOCK);
  }

  private void clickCodeSurroundWith(@NotNull IdeFrameFixture ideFrame, int key) {
    ideFrame.invokeMenuPath("Code", "Surround With\u2026");
    GuiTests.waitUntilShowing(guiTest.robot(), Matchers.byType(JBList.class));
    guiTest.robot().pressAndReleaseKey(key);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.getEditor()
      .waitUntilErrorAnalysisFinishes();
  }

  private void removeTryCatchAndVerify(@NotNull IdeFrameFixture ideFrame,
                                       @NotNull EditorFixture editorFixture) {
    editorFixture.select("(" + HELLO_STR + ")");
    ideFrame.invokeMenuPath("Code", "Unwrap/Remove\u2026");

    Ref<JPanel> unwrapRemoveJPanel = new Ref<>();
    Wait.seconds(7).expecting("Unwrap/Remove popup list to show.").until(() -> {
      Collection<JPanel> allFound =
        ideFrame.robot().finder().findAll(ideFrame.target(), Matchers.byType(JPanel.class));

      JPanel unwrapRemove = null;
      for (JPanel jPanel : allFound) {
        if (jPanel instanceof AbstractPopup.MyContentPanel) {
          unwrapRemove = jPanel;
        }
      }

      if (unwrapRemove == null) {
        return false;
      }

      unwrapRemoveJPanel.set(unwrapRemove);
      return true;
    });

    // There is only one list inside of Unwrap/Remove pop up.
    JBList popList = ideFrame.robot().finder().findByType(unwrapRemoveJPanel.get(), JBList.class);
    JListFixture listFixture= new JListFixture(guiTest.robot(), popList);
    listFixture.replaceCellReader((jList, index) -> jList.getModel().getElementAt(index).toString());
    listFixture.clickItem("Unwrap 'try...'");

    Wait.seconds(5).expecting("try/catch block to be removed.").until(() -> {
      String content = editorFixture.getCurrentFileContents();
      return !content.contains(TRY_CATCH_BLOCK) && content.contains(HELLO_STR);
    });
  }
}
