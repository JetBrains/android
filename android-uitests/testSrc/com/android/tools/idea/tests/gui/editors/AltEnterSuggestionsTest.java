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
package com.android.tools.idea.tests.gui.editors;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;
import static org.junit.Assert.assertEquals;



import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.components.JBList;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fest.swing.fixture.DialogFixture;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AltEnterSuggestionsTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Views Activity";

  private IdeFrameFixture ideFrame;
  private EditorFixture editor;

  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE); // Default projects are created with androidx dependencies
    guiTest.robot().waitForIdle();
    ideFrame = guiTest.ideFrame();
    editor = ideFrame.getEditor();
  }

  /**
   * Verifies user can link project with Kotlin.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 885eaf61-8b92-4e36-9f80-a34fbef9f27d
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a java project. Go to MainActivity file.
   *   2. Copy paste a java code, in "onCreate" method. (Verify 1)
   *   3. Click on "Log" and hit "Alt + Enter". (Verify 2)
   *   4. Click on "SomeClass" class name and hit "Alt + Enter". (Verify 4)
   *   5. Click on "someMethod(2)" and hit "Alt + Enter". (Verify 3)
   *
   *  Verification:
   *  1. "Log" keyword, "someMethod(2)", and "Person" object class are not resolved and shows in red color.
   *  2. "Log" class is imported and the error is resolved.
   *  3. "Alt + Enter" command will show following two options.
   *     a. Create method "someMethod" in MainActivity
   *     b. Rename reference
   *    When "Create method" option is selected, studio will create private method in MainActivity named "someMethod"
   *    which will take "int" parameter and "int" return type.
   *  4. "Alt + Enter" command will show following options.
   *     a. Create class "SomeClass"
   *     b. Create enum "SomeClass"
   *     c. Create inner class "SomeClass"
   *     d. Create interface "SomeClass"
   *     e. Create type parameter "SomeClass"
   *  Studio creates class, enum, inner class, interface, or type parameter based on the user selection without error.
   *   </pre>
   * <p>
   */

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testAltEnterSuggestions() throws IOException, InterruptedException {
    ideFrame.clearNotificationsPresentOnIdeFrame();

    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween("(R.layout.activity_main);", "")
      .enterText("\nLog.d(\"MainActivity\", \"Shortcut testing\");\n int x = someMethod(2);\n\nSomeClass someClass;");

    // Wait for symbol to be marked as unrecognized
    editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 3);

    editor.moveBetween("L", "og");
    editor.invokeAction(EditorFixture.EditorAction.SHOW_INTENTION_ACTIONS);
    JBList popupOptionsForImportingLog = GuiTests.waitForPopup(ideFrame.robot());

    List<String> optionsForLog = editor.moreActionsOptions();
    assertEquals("Import class", optionsForLog.get(0));

    GuiTests.clickPopupMenuItem("Import class", popupOptionsForImportingLog, ideFrame.robot());
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertThat(editor.getCurrentFileContents()).contains("import android.util.Log;");

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.moveBetween("SomeC", "lass someClass;");
    editor.waitForQuickfix();
    editor.invokeAction(EditorFixture.EditorAction.SHOW_INTENTION_ACTIONS);
    JBList popupForImportingSomeClass = GuiTests.waitForPopup(ideFrame.robot());

    List<String> optionsForImportingSomeClass = editor.moreActionsOptions();
    assertEquals("Create class 'SomeClass'", optionsForImportingSomeClass.get(0));
    assertEquals("Create interface 'SomeClass'", optionsForImportingSomeClass.get(1));
    assertEquals("Create enum 'SomeClass'", optionsForImportingSomeClass.get(2));
    assertEquals("Create inner class 'SomeClass'", optionsForImportingSomeClass.get(3));
    assertEquals("Create type parameter 'SomeClass'", optionsForImportingSomeClass.get(4));

    GuiTests.clickPopupMenuItem("Create class 'SomeClass'", popupForImportingSomeClass, ideFrame.robot());

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.requestFocusIfLost();

    DialogFixture createClassDialog = ideFrame.waitForDialog("Create Class SomeClass");
    GuiTests.findAndClickOkButton(createClassDialog);

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.getProjectView().assertFilesExist(
      "/app/src/main/java/com/google/myapplication/SomeClass.java"
    );

    ideFrame.focus();
    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween("someMeth", "od");
    editor.waitForQuickfix();
    editor.invokeAction(EditorFixture.EditorAction.SHOW_INTENTION_ACTIONS);

    JBList popupForImportingSomeMethod = GuiTests.waitForPopup(ideFrame.robot());

    List<String> optionsForImportingSomeMethod = editor.moreActionsOptions();
    assertEquals("Create method 'someMethod' in 'MainActivity'", optionsForImportingSomeMethod.get(0));
    //assertEquals("Rename reference", optionsForImportingSomeMethod.get(1));

    GuiTests.clickPopupMenuItem("Create method 'someMethod' in 'MainActivity'", popupForImportingSomeMethod, ideFrame.robot());

    assertThat(editor.getCurrentFileContents())
      .contains("private int someMethod(int i) {");
  }
}
