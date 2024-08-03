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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.components.JBList;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CodeGenerationTest {
  @Rule public GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  static private String ACTIVITY_CLASS = "app/src/main/java/com/codegeneration/MainActivity.java";
  static private String PERSON_CLASS = "app/src/main/java/com/codegeneration/Person.java";
  static private String JAVA_CODE = "String hello = \"hello\";";
  static private String POPUPLIST_CLASS = "com.intellij.ui.popup.list.ListPopupImpl$MyList";
  static private String JBLIST_CLASS = "com.intellij.ui.components.JBList";

  /**
   * Verifies getter/setter and surround with shortcut can be used to wrap block of code
   * in some structure, like if-else or for each.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 98badec4-c84d-4b8a-98cb-f0d87322527d
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import CodeGeneration project and wait for project sync to finish
   *   2. Open MainActivity.java file, move the mouse at the end of line 'String hello = "hello";'
   *   3. Click on Code > Surround With
   *   4. Click on any of the available option, say if, Verify 1
   *   5. Select the line of code using the mouse and then click on Code > Unwrap/Remove, Verify 2
   *   6. Repeat step 3
   *   7. On the keyboard, type the keyboard shortcut for any of the options,
   *      eg "4" for do/while, Verify 3
   *   8. Open Person.java file, While having cursor within the curly braces, invoke
   *      Code -> Generate > Getter and Setter
   *   9. Select fields "name" and "age" in Generate Getter and Setters dialog box, click "OK",
   *      Verify 4
   *   Verify:
   *   1. The code is wrapped inside if statement
   *   2. The if wrap is removed
   *   3. The code is wrapped inside a do/while loop
   *   4. Getter and setter methods are added for both the fields.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void codeGeneration() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("CodeGeneration", Wait.seconds(120));
    guiTest.waitForBackgroundTasks();

    // Add Surround With and Unwrap code
    EditorFixture editor = ideFrame.getEditor().open(ACTIVITY_CLASS)
      .moveBetween(JAVA_CODE, "");
    // Workaround to handle the cursor is not moved to the right place.
    // Without this, it fails 4/100 times here.
    if (!editor.getCurrentLine().contains(JAVA_CODE)) {
      editor.moveBetween(JAVA_CODE, "");
    }

    ideFrame.invokeMenuPath("Code", "Surround With...");
    getList(POPUPLIST_CLASS).clickItem(".*if");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    // Waiting for error analysis to finish to add extra wait for the file to refresh.
    editor.waitUntilErrorAnalysisFinishes();
    assertThat(editor.getCurrentFileContents().contains("if () {")).isTrue();

    ideFrame.invokeMenuPath("Code", "Unwrap/Remove...");
    getList(JBLIST_CLASS).clickItem("Unwrap 'if...'");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editor.getCurrentFileContents().contains("if () {")).isFalse();

    ideFrame.invokeMenuPath("Code", "Surround With...");
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_4);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editor.getCurrentFileContents().contains("while (true);")).isTrue();

    // Generate constructor
    ideFrame.getEditor().open(PERSON_CLASS)
      .moveBetween("public class Person {", "");
    ideFrame.invokeMenuPath("Code", "Generate...");
    // Selecting Getter and Setter by clicking on DOWN key and ENTER key.
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_DOWN);
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_DOWN);
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_DOWN);
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);

    findDialog(withTitle("Select Fields to Generate Getters and Setters"))
      .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot())
      .button(withText("OK")).click();
    String contents = editor.getCurrentFileContents();
    assertThat(contents.contains("public String getName()")).isTrue();
    assertThat(contents.contains("public void setName(String name)")).isTrue();
  }

  private JListFixture getList(@NotNull String className) {
    JBList jbList =
      GuiTests.waitUntilShowingAndEnabled(guiTest.robot(),
                                          null, new GenericTypeMatcher<JBList>(JBList.class) {
        @Override
        protected boolean isMatching(@NotNull JBList list) {
          return list.getClass().getName().equals(className);
        }
      });
    return new JListFixture(guiTest.robot(), jbList);
  }
}
