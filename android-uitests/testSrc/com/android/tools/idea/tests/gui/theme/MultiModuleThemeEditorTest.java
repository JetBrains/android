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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.intellij.notification.EventLog;
import com.intellij.notification.LogModel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import org.fest.swing.fixture.JComboBoxFixture;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
public class MultiModuleThemeEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testMultipleModules() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiAndroidModule");
    final ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());

    assertThat(themeEditor.getModulesList(), containsInAnyOrder("app", "library", "library2", "library3", "nothemeslibrary"));
    final JComboBoxFixture modulesComboBox = themeEditor.getModulesComboBox();

    modulesComboBox.selectItem("app");
    final List<String> appThemes = themeEditor.getThemesList();
    assertThat(Arrays.asList("AppTheme", "Library1DependentTheme", "Library1Theme", "Library2Theme"), everyItem(isIn(appThemes)));
    assertThat("Library3Theme", not(isIn(appThemes)));

    modulesComboBox.selectItem("library");
    final List<String> library1Themes = themeEditor.getThemesList();
    assertThat(Arrays.asList("Library1Theme", "Library2Theme"), everyItem(isIn(library1Themes)));
    assertThat(Arrays.asList("AppTheme", "Library1DependentTheme", "Library3Theme"), everyItem(not(isIn(library1Themes))));

    modulesComboBox.selectItem("library2");
    final List<String> library2Themes = themeEditor.getThemesList();
    assertThat("Library2Theme", isIn(library2Themes));
    assertThat(Arrays.asList("AppTheme", "Library1DependentTheme", "Library1Theme", "Library3Theme"),
               everyItem(not(isIn(library2Themes))));

    modulesComboBox.selectItem("library3");
    final List<String> library3Themes = themeEditor.getThemesList();
    assertThat("Library3Theme", isIn(library3Themes));
    assertThat(library3Themes, not(containsInAnyOrder("AppTheme", "Library1DependentTheme", "Library1Theme", "Library2Theme")));
  }

  @Test
  public void testModuleWithoutThemes() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiAndroidModule");
    final ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());

    final JComboBoxFixture modulesComboBox = themeEditor.getModulesComboBox();

    modulesComboBox.selectItem("app");
    themeEditor.getThemesComboBox().selectItem("AppTheme");
    themeEditor.waitForThemeSelection("AppTheme");

    modulesComboBox.selectItem("nothemeslibrary");
    guiTest.robot().waitForIdle();

    final LogModel logModel = EventLog.getLogModel(guiTest.ideFrame().getProject());
    assertThat(logModel.getNotifications(), everyItem(new BaseMatcher<Notification>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("a notification which is not an error");
      }

      @Override
      public boolean matches(Object item) {
        return (item instanceof Notification && ((Notification)item).getType() != NotificationType.ERROR);
      }
    }));
  }
}
