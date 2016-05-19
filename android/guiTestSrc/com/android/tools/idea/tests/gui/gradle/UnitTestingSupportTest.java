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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.UnitTestTreeFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunIn(TestGroup.TEST_SUPPORT)
@RunWith(GuiTestRunner.class)
public class UnitTestingSupportTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private EditorFixture myEditor;

  @Test
  public void appModule_gradleAwareMake() throws Exception {
    doTest("app/src/test/java/com/android/tests", "UnitTest");
  }

  @Ignore("go/studio-builder/builders/ubuntu-studio-master-dev-uitests/builds/69")
  @Test
  public void libModule_gradleAwareMake() throws Exception {
    doTest("lib/src/test/java/com/android/tests/lib", "LibUnitTest");
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
  private void doTest(@NotNull String path, @NotNull String testClass) throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("ProjectWithUnitTests");

    // Open the test file:
    myEditor = guiTest.ideFrame().getEditor();
    myEditor.open(path + "/" + testClass + ".java");

    // Run the test case that is supposed to pass:
    myEditor.moveBetween("passing", "Test");

    runTestUnderCursor();

    UnitTestTreeFixture unitTestTree = getTestTree(testClass + ".passingTest");
    assertTrue(unitTestTree.isAllTestsPassed());
    assertEquals(1, unitTestTree.getAllTestsCount());

    // Run the test that is supposed to fail:
    myEditor.moveBetween("failing", "Test");

    runTestUnderCursor();

    unitTestTree = getTestTree(testClass + ".failingTest");
    assertEquals(1, unitTestTree.getFailingTestsCount());
    assertEquals(1, unitTestTree.getAllTestsCount());

    // Fix the failing test and re-run the tests.
    myEditor.moveBetween("(7", ",");
    myEditor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    myEditor.enterText("6");

    runTestUnderCursor();
    guiTest.waitForBackgroundTasks();
    unitTestTree = getTestTree(testClass + ".failingTest");
    assertTrue(unitTestTree.isAllTestsPassed());
    assertEquals(1, unitTestTree.getAllTestsCount());

    // Run the whole class, it should pass now.
    myEditor.moveBetween("class ", testClass);

    runTestUnderCursor();

    unitTestTree = getTestTree(testClass);
    assertTrue(unitTestTree.isAllTestsPassed());
    assertThat(unitTestTree.getAllTestsCount()).isGreaterThan(1);

    // Break the test again to check the re-run buttons.
    myEditor.moveBetween("(6", ",");
    myEditor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    myEditor.enterText("8");

    // Re-run all the tests.
    unitTestTree.getContent().rerun();
    guiTest.waitForBackgroundTasks();
    unitTestTree = getTestTree(testClass);
    assertEquals(1, unitTestTree.getFailingTestsCount());
    assertThat(unitTestTree.getAllTestsCount()).isGreaterThan(1);

    // Fix it again.
    myEditor.moveBetween("(8", ",");
    myEditor.invokeAction(EditorFixture.EditorAction.BACK_SPACE);
    myEditor.enterText("6");

    // Re-run failed tests.
    unitTestTree.getContent().rerunFailed();
    guiTest.waitForBackgroundTasks();
    unitTestTree = getTestTree("Rerun Failed Tests");
    assertTrue(unitTestTree.isAllTestsPassed());
    assertEquals(1, unitTestTree.getAllTestsCount());

    // Rebuild the project and run tests again, they should still run and pass.
    guiTest.ideFrame().invokeMenuPath("Build", "Rebuild Project");
    guiTest.waitForBackgroundTasks();

    myEditor.moveBetween("class ", testClass);
    runTestUnderCursor();
    unitTestTree = getTestTree(testClass);
    assertTrue(unitTestTree.isAllTestsPassed());
    assertThat(unitTestTree.getAllTestsCount()).isGreaterThan(1);
  }

  @NotNull
  private UnitTestTreeFixture getTestTree(@NotNull String tabName) {
    ContentFixture content = guiTest.ideFrame().getRunToolWindow().findContent(tabName);
    content.waitForExecutionToFinish();
    guiTest.waitForBackgroundTasks();
    return content.getUnitTestTree();
  }

  private void runTestUnderCursor() {
    // This only works when there's one applicable run configurations, otherwise a popup would show up.
    myEditor.invokeAction(EditorFixture.EditorAction.RUN_FROM_CONTEXT);
    guiTest.waitForBackgroundTasks();
  }
}
