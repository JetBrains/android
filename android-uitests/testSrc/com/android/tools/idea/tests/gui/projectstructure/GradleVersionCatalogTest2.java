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
package com.android.tools.idea.tests.gui.projectstructure;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FindToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguageForNewProject;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class GradleVersionCatalogTest2 {
  @Rule
  public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private String versionsFilePath= "gradle/libs.versions.toml";

  /**
   * Gradle version catalog - Gradle version catalog - Navigation, Find Usages, Error Highlighting test
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * Bugs: b/291959386, b/291960156, b/291960154
   * <p>
   *
   * <pre>
   *   Test Steps:
   *    1. Create a new project.
   *    2. Open libs.versions.toml file.
   *    3. Navigate within the file, example: version.ref = <variable name> -> variable location
   *    4. Navigate from toml to build.gradle.kts
   *    5. Navigate from build file to kts file.
   *    6. Find Usages for any plugin.
   *    7. Error highlighting.
   *   Verify:
   *    1. Navigating between files is successful.
   *    2. Find usages is working for toml file.
   *    3. Error highlighting is working.
   * </pre>
   * <p>
   */
  @Test
  public void testNavigationAndFindUsages() {
    // Create a new project.
    WizardUtils.createNewProject(guiTest,
                                 "Empty Views Activity",
                                 Language.Kotlin,
                                 BuildConfigurationLanguageForNewProject.KTS);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    EditorFixture editor = ideFrame.getEditor();

    editor.open(versionsFilePath);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Test navigation from module to variables.
    editor.select(String.format("version.ref = \"(constraintlayout)\""))
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);

    assertThat(editor.getCurrentLine())
      .contains("constraintlayout = ");

    // Test navigation from build.gradle.kts file to toml file.
    editor.open("app/build.gradle.kts");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.select(String.format("libs.(material)"))
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    Wait.seconds(60)
      .expecting("Referencing...")
      .until(() -> editor.getCurrentFileName().equalsIgnoreCase("libs.versions.toml"));

    assertThat(editor.getCurrentLine())
      .contains("material = {");

    //Add navigation from toml to kts file.
    editor.select(String.format("(appcompat).* group"))
      .invokeAction(EditorFixture.EditorAction.GOTO_DECLARATION);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    Wait.seconds(60)
      .expecting("Referencing...")
      .until(() -> editor.getCurrentFileName().contains("build.gradle.kts"));

    assertThat(editor.getCurrentLine())
      .contains("appcompat");

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideFrame.requestFocusIfLost();

    //Test find usages
    editor.open(versionsFilePath);

    editor.select(String.format("(android-application)"))
      .invokeAction(EditorFixture.EditorAction.FIND_USAGES);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    Wait.seconds(120)
      .expecting("Waiting for the tool window to activate ...")
      .until(() -> ToolWindowManager.getInstance(ideFrame.getProject()).getToolWindow("Find").isVisible()
      );

    FindToolWindowFixture.ContentFixture toolWindow = ideFrame.getFindToolWindow();
    JTreeFixture toolWindowTree = new JTreeFixture(ideFrame.robot(), toolWindow.getContentsTree());

    navigateToFileUsingLastRow(toolWindowTree, ideFrame);
    // Wait for the file to open.
    Wait.seconds(60)
      .expecting("Waiting for the file to open...")
      .until(() -> editor.getCurrentFileName().equalsIgnoreCase("build.gradle.kts"));

    // Test error highlighting
    editor.open(versionsFilePath);
    editor.select(String.format("(kotlin) = "))
      .enterText("hello");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.waitForFileToActivate();
    editor.waitUntilErrorAnalysisFinishes();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editor.getHighlights(HighlightSeverity.WARNING).size())
      .isGreaterThan(1);
  }

  // A recursive function which can help in navigating through the steps to find the last file in the tree.
  private void navigateToFileUsingLastRow(JTreeFixture tree, IdeFrameFixture ideFrame) {
    // Expand the last row.
    String val = (Objects.requireNonNull(tree.valueAt(tree.getRowCount() - 1)));
    boolean condition = val.equalsIgnoreCase("2|alias|(|libs|.|plugins|.|android|.|application|)");
    if (condition) {
      tree.doubleClickRow(tree.getRowCount() - 1);
    }
    else {
      tree.expandRow(tree.getRowCount() - 1);
      guiTest.waitForAllBackgroundTasksToBeCompleted();
      navigateToFileUsingLastRow(tree, ideFrame);
    }
  }
}
