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
package com.android.tools.idea.tests.gui.theme;

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.theme.NewStyleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static junit.framework.Assert.assertEquals;

@BelongsToTestGroups({THEME})
public class NewStyleDialogGuiTest extends GuiTestCase {
  /**
   * When "Create New Theme" is selected, opened dialog contains theme that was edited
   * as a parent.
   *
   * Is a regression test for http://b.android.com/180575
   */
  @Test @IdeGuiTest
  public void testCreateNewThemeSelection() throws IOException {
    myProjectFrame = importSimpleApplication();
    final ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);

    final JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();
    themesComboBox.selectItem("@style/Theme.AppCompat.Light.NoActionBar");
    themesComboBox.selectItem("Create New Theme");
    final NewStyleDialogFixture newStyleDialog1 = NewStyleDialogFixture.find(myRobot);
    assertEquals("@style/Theme.AppCompat.Light.NoActionBar", newStyleDialog1.getParentComboBox().selectedItem());
    newStyleDialog1.clickCancel();

    themesComboBox.selectItem("@style/Theme.AppCompat.NoActionBar");
    themesComboBox.selectItem("Create New Theme");
    final NewStyleDialogFixture newStyleDialog2 = NewStyleDialogFixture.find(myRobot);
    assertEquals("@style/Theme.AppCompat.NoActionBar", newStyleDialog2.getParentComboBox().selectedItem());
    newStyleDialog2.clickCancel();
  }

}
