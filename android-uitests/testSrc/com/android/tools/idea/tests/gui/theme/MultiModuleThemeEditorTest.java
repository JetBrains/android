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

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.intellij.notification.EventLog;
import com.intellij.notification.LogModel;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import org.fest.swing.fixture.JComboBoxFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
public class MultiModuleThemeEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final ScreenshotsDuringTest screenshotsDuringTest = new ScreenshotsDuringTest();

  @Test
  public void testMultipleModules() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiAndroidModule");
    final ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());

    assertThat(themeEditor.getModulesList()).containsExactly("app", "library", "library2", "library3", "nothemeslibrary");
    final JComboBoxFixture modulesComboBox = themeEditor.getModulesComboBox();

    modulesComboBox.selectItem("app");
    final List<String> appThemes = themeEditor.getThemesList();
    assertThat(appThemes).containsAllOf("AppTheme", "Library1DependentTheme", "Library1Theme", "Library2Theme");
    assertThat(appThemes).doesNotContain("Library3Theme");

    modulesComboBox.selectItem("library");
    final List<String> library1Themes = themeEditor.getThemesList();
    assertThat(library1Themes).containsAllOf("Library1Theme", "Library2Theme");
    assertThat(library1Themes).containsNoneOf("AppTheme", "Library1DependentTheme", "Library3Theme");

    modulesComboBox.selectItem("library2");
    final List<String> library2Themes = themeEditor.getThemesList();
    assertThat(library2Themes).contains("Library2Theme");
    assertThat(library2Themes).containsNoneOf("AppTheme", "Library1DependentTheme", "Library1Theme", "Library3Theme");

    modulesComboBox.selectItem("library3");
    final List<String> library3Themes = themeEditor.getThemesList();
    assertThat(library3Themes).contains("Library3Theme");
    assertThat(library3Themes).containsNoneOf("AppTheme", "Library1DependentTheme", "Library1Theme", "Library2Theme");
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
    for (Notification notification : logModel.getNotifications()) {
      assertThat(notification.getType()).isNotEqualTo(NotificationType.ERROR);
    }
  }
}
