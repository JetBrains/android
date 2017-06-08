/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.webp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.IconPickerDialogFixture;
import org.fest.swing.core.MouseButton;
import org.fest.swing.fixture.JTableFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.data.TableCell.row;

@RunWith(GuiTestRunner.class)
public class FilterIconTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  private static final String REG_EXP = "android:autoMirrored";

  /**
   * Verifies the icons can be filtered by name when creating a Vector Asset.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 54600e1a-8aa6-4874-854e-e076956edf14
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import a simple project.
   *   2. Right click the default app module (or any manually created module) and select
   *      New > Vector Asset (Verify 1).
   *   3. Select checkbox "Enable auto mirroring for RTL Layout".
   *   4. Click on "Choose Icon", enter keyword to filter icon, select one and click OK (verify 1).
   *   5. Hit "Next" and "Finish" button.
   *   6. In drawable folder, find the XML file just created, double click it. (Verify 2).
   *   Verify:
   *   1. "Select Icon" dialog with an option to search icons should be displayed. Should be able
   *      to filter the icon by looking up the filename in the icon repository.
   *   2. If preview pane is enabled, then a similar preview image should show up. Look for
   *      attributes android:autoMirrored in the generated xml.
   *   </pre>
   * <p>
   */
  @Test
  @RunIn(TestGroup.QA)
  public void testFilterIcon() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();

    AssetStudioWizardFixture assetStudioWizardFixture = ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Vector Asset");
    assetStudioWizardFixture.enableAutoMirror();

    IconPickerDialogFixture iconPickerDialogFixture = assetStudioWizardFixture.chooseIcon(ideFrame);
    iconPickerDialogFixture.filterIconByName("call");
    JTableFixture tableFixture = iconPickerDialogFixture.getIconTable();
    // Searching icon by "call", count of results should be greater than 0.
    assertThat(tableFixture.rowCount()).isGreaterThan(0);
    // Select 1st icon.
    tableFixture.click(row(0).column(0), MouseButton.LEFT_BUTTON);
    iconPickerDialogFixture.clickOk();
    assetStudioWizardFixture.clickNext().clickFinish();

    EditorFixture editor = guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/res/drawable/ic_call_black_24dp.xml");
    String fileContents = editor.getCurrentFileContents();
    assertThat(fileContents).containsMatch(REG_EXP);
  }
}
