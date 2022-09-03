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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.timing.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class XMLCodeStyleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private static final Pattern ORDER_OF_VIEWS = Pattern.compile(
    "button.*button2.*editText.*LinearLayout.*button3.*button5.*button6", Pattern.DOTALL);
  private IdeSettingsDialogFixture ideSettingsDialog = null;

  @Before
  public void setUp() throws Exception {
    guiTest.importSimpleApplication();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }


  /**
   * Verifies reformatting code with custom XML code styles
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 425d39f1-74cd-4a6b-a063-075df54f493b
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a new project
   *      To make the test run to be optimized, imported sampleapplication app with the required xml content.
   *   2. Open Studio Preferences/Settings > Code Styles > XML > Select Scheme as Default IDE
   *   3. Select "Tabs and Indents" > Increase Tab size, Indents to 10
   *        Tab size to 10
   *        Indent : 10
   *   4. Select Android tab > Check Use custom formatting settings....
   *   5. Check "Insert line breaks after last attribute" for all type of files
   *   6. Click OK
   *   7. Open Layout file and Drag and Drop TextView and Button to layout > Goto Text Editor view.(Verify 1)
   *      Imported sample app contains  xml with both TextView and Button, skipped this step in test.
   *   8. Goto Text Editor View . Click Code -> Reformat Code .(Verify 2)
   *   Verify:
   *   1. Make sure new elements are added
   *   2. Verify the layout file is updated with new code format. Make sure UI components are not interchanged.
   *   </pre>
   * <p>
   */
  @Test
  public void testXMlCodeStyleReformatting() throws IOException, InterruptedException {
    EditorFixture editorFixture = guiTest.ideFrame().getEditor();

    // Update the XNL code Style.
    updateXMLCodeStyleSettings();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    String currentFileContent = editorFixture.open("app/src/main/res/layout/absolute.xml",
                                                   EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();


    //Verify the XML content
    assertThat(currentFileContent).contains("    <Button");
    assertThat(currentFileContent).contains("    <EditText");

    //Verify the order of the Views
    assertThat(currentFileContent).containsMatch(ORDER_OF_VIEWS);


    // Apply Code Reformat
    Wait.seconds(2).expecting("Wait for code to be reformatted").until(() -> {
      try {
        guiTest.ideFrame().invokeMenuPath("Code", "Reformat Code");
        return true;
      }
      catch (WaitTimedOutError error) {
        return false;
      }
    });

    currentFileContent = editorFixture.open("app/src/main/res/layout/absolute.xml",
                                            EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();

    // Verify the XML content after reformat is applied
    assertThat(currentFileContent).contains("          <Button");
    assertThat(currentFileContent).contains("          <EditText");

    //Verify the order of the Views are not changed after reformat is applied.
    assertThat(currentFileContent).containsMatch(ORDER_OF_VIEWS);
  }


  private void updateXMLCodeStyleSettings() throws IOException, InterruptedException {
    List<JCheckBoxFixture> allFound;
    ideSettingsDialog = guiTest.ideFrame().openIdeSettings();

    // Update 'Tab Size' value and 'Indent' value in 'Tab and Indent' tab
    ideSettingsDialog.selectCodeStylePage("XML");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ideSettingsDialog.changeTextFieldContent("Tab size:", "4", "10");
    ideSettingsDialog.changeTextFieldContent("Indent:", "4", "10");

    // Update setting in Android Tab
    ideSettingsDialog.clickTab("Android");
    allFound = ideSettingsDialog.findAllCheckBoxes("Insert line break before first attribute");
    for (JCheckBoxFixture checkBoxFixture : allFound) {
      if (!checkBoxFixture.isEnabled()) {
        checkBoxFixture.select();
      }
    }

    ideSettingsDialog.clickButton("Apply");
    ideSettingsDialog.clickOK();
  }

  @After
  public void closeDialog() {
    if (ideSettingsDialog != null) {
      ideSettingsDialog.close();
    }
  }
}
