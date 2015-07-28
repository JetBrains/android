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
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

@BelongsToTestGroups({THEME})
public class MultiModuleThemeEditorTest extends GuiTestCase {
  @BeforeClass
  public static void runBeforeClass() {
    ThemeEditorTestUtils.enableThemeEditor();
  }

  @Test
  @IdeGuiTest
  public void testMultipleModules() throws IOException {
    final IdeFrameFixture projectFrame = importProjectAndWaitForProjectSyncToFinish("MultiAndroidModule");
    final ThemeEditorFixture themeEditor = ThemeEditorTestUtils.openThemeEditor(projectFrame);

    assertThat(themeEditor.getModulesList(), containsInAnyOrder("app", "library", "library2", "library3"));
    final JComboBoxFixture modulesComboBox = themeEditor.getModulesComboBox();

    modulesComboBox.selectItem("app");
    final List<String> appThemes = themeEditor.getThemesList();
    assertThat(Arrays.asList("[AppTheme]", "[Library1DependentTheme]", "[Library1Theme]", "[Library2Theme]"), everyItem(isIn(appThemes)));
    assertThat("[Library3Theme]", not(isIn(appThemes)));

    modulesComboBox.selectItem("library");
    final List<String> library1Themes = themeEditor.getThemesList();
    assertThat(Arrays.asList("[Library1Theme]", "[Library2Theme]"), everyItem(isIn(library1Themes)));
    assertThat(Arrays.asList("[AppTheme]", "[Library1DependentTheme]", "[Library3Theme]"), everyItem(not(isIn(library1Themes))));

    modulesComboBox.selectItem("library2");
    final List<String> library2Themes = themeEditor.getThemesList();
    assertThat("[Library2Theme]", isIn(library2Themes));
    assertThat(Arrays.asList("[AppTheme]", "[Library1DependentTheme]", "[Library1Theme]", "[Library3Theme]"),
               everyItem(not(isIn(library2Themes))));

    modulesComboBox.selectItem("library3");
    final List<String> library3Themes = themeEditor.getThemesList();
    assertThat("[Library3Theme]", isIn(library3Themes));
    assertThat(library3Themes, not(containsInAnyOrder("[AppTheme]", "[Library1DependentTheme]", "[Library1Theme]", "[Library2Theme]")));
  }
}
