/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.BuildVariantsToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.UnitTestTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@BelongsToTestGroups({TestGroup.UNIT_TESTING, TestGroup.PROJECT_SUPPORT})
public class UnitTestingSupportTest extends GuiTestCase {
  protected IdeFrameFixture myProjectFrame;
  private EditorFixture myEditor;

  @Test @IdeGuiTest
  public void unitTestingSupport_defaultMake() throws Exception {
    doTest("Make");
  }

  @Test @IdeGuiTest
  public void unitTestingSupport_gradleAwareMake() throws Exception {
    doTest("Gradle-aware Make");
  }

  /**
   * This covers all functionality that we expect from AS when it comes to unit tests:
   *
   * <ul>
   *   <li>Tests can be run from the editor.
   *   <li>Results are correctly reported in the Run window.
   *   <li>The classpath when running tests is correct.
   *   <li>You can fix a test and changes are picked up the next time tests are run (which means the correct gradle tasks are run).
   * </ul>
   */
  private void doTest(String makeStepName) throws Exception {
    myProjectFrame = importProjectAndWaitForProjectSyncToFinish("ProjectWithUnitTests");
    myProjectFrame.setJUnitDefaultBeforeRunTask(makeStepName);

    BuildVariantsToolWindowFixture buildVariants = myProjectFrame.getBuildVariantsWindow();
    buildVariants.activate();
    buildVariants.selectUnitTests();

    // Open the test file:
    myEditor = myProjectFrame.getEditor();
    myEditor.open("app/src/test/java/com/android/tests/UnitTest.java");

    // Run the test case that is supposed to pass:
    myEditor.moveTo(myEditor.findOffset("passing", "Test", true));

    runTestUnderCursor();

    UnitTestTreeFixture unitTestTree = getTestTree("UnitTest.passingTest");
    assertTrue(unitTestTree.isAllTestsPassed());

    // Run the test that is supposed to fail:
    myEditor.requestFocus();
    myEditor.moveTo(myEditor.findOffset("failing", "Test", true));

    runTestUnderCursor();

    unitTestTree = getTestTree("UnitTest.failingTest");
    assertEquals(1, unitTestTree.getFailingTestsCount());

    // Fix the failing test and re-run the tests.
    myEditor.requestFocus();
    myEditor.moveTo(myEditor.findOffset("(5", ",", true));
    myEditor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    myEditor.enterText("4");

    unitTestTree.getContent().rerun();
    myProjectFrame.waitForBackgroundTasksToFinish();
    unitTestTree = getTestTree("UnitTest.failingTest");
    assertTrue(unitTestTree.isAllTestsPassed());


    // Run the whole class, it should pass now.
    myEditor.moveTo(myEditor.findOffset("class Unit", "Test", true));

    runTestUnderCursor();

    unitTestTree = getTestTree("UnitTest");
    assertTrue(unitTestTree.isAllTestsPassed());
  }

  @NotNull
  private UnitTestTreeFixture getTestTree(@NotNull String tabName) {
    ContentFixture content = myProjectFrame.getRunToolWindow().findContent(tabName);
    content.waitForExecutionToFinish(GuiTests.SHORT_TIMEOUT);
    myProjectFrame.waitForBackgroundTasksToFinish();
    return content.getUnitTestTree();
  }

  private void runTestUnderCursor() {
    // This only works when there's one applicable run configurations, otherwise a popup would show up.
    myEditor.invokeAction(EditorFixture.EditorAction.RUN_FROM_CONTEXT);
    myProjectFrame.waitForBackgroundTasksToFinish();
  }
}
