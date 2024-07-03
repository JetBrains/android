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
package com.android.tools.idea.tests.gui.designTools.problemsPanel;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProblemsPaneFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ProblemsPanelTest {
  @Rule
  public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  static private String MAINACTIVITY = "app/src/main/java/com/codegeneration/MainActivity.java";
  static private String CAR = "app/src/main/java/com/codegeneration/Person.java";
  static private String INVENTORY = "app/src/main/java/com/codegeneration/Inventory.kt";

  private IdeFrameFixture ideFrameFixture;
  private EditorFixture editorFixture;
  private  ProblemsPaneFixture problemsPaneFixture;

  //The test verifies the AGP upgrade from one stable version to the latest public stable version.
  /**
   * Verifies automatic update of gradle version
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: 90b13239-c667-402d-9552-49de9fddc2ba
   * <pre>
   *   Test Steps:
   *   1. Import project without views (Using testData/CodeGeneration)
   *   2. Close all opened files and Problem panel windows
   *   3. Open “JavaFile.java” form the Project
   *   4. Open Problems Panel Window (Verify #1)
   *   5. Close Problems Panel Window
   *   6. Open “KotlinFile.kt” file from Project
   *   7. Open Problems Panel window Again (Verify #1)
   *   Verification:
   *   1. Verify there are only 2 tabs “Current File” and “Project Errors”
   * </pre>
   */
  @Test
  public void testProjectWithoutViews() throws Exception {

    guiTest.importProjectAndWaitForProjectSyncToFinish("CodeGeneration");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrameFixture = guiTest.ideFrame();
    editorFixture = ideFrameFixture.getEditor();
    problemsPaneFixture = new ProblemsPaneFixture(ideFrameFixture);

    // Testing MainActivity Java file
    assertThat(testProblemsPanelForFile(MAINACTIVITY, "File", "Project Errors"))
      .isTrue();
    // Testing Kotlin file
    assertThat(testProblemsPanelForFile(INVENTORY, "File", "Project Errors"))
      .isTrue();
    // Testing Java Class file.
    assertThat(testProblemsPanelForFile(CAR, "File", "Project Errors"))
      .isTrue();
  }

  /**
   * Opens a file and problems tool window,
   * Verifies if the Problems Panel Window only has the mentioned tabs,
   * Closes the file.
   * @param filePath: File to be opened.
   * @param tabs: List of tabs names to verify
   * @return True: If all the tabs are present, False: If more or less tabs are opened.
   */
  private boolean testProblemsPanelForFile(String filePath, String... tabs) {
    editorFixture.open(filePath);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    problemsPaneFixture.activate();

    editorFixture.waitUntilErrorAnalysisFinishes();
    editorFixture.waitUntilErrorAnalysisFinishes(); // To reduce flakiness

    // Check number of tabs.
    if (tabs.length != problemsPaneFixture.getAvailableTabsCount()) {
      return false;
    }
    // Check if the tab is present.
    for (String tabName : tabs) {
      if (!problemsPaneFixture.doesTabExist(tabName)) {
        return false;
      }
    }

    editorFixture.closeFile(filePath);

    return true;
  }
}
