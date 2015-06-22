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
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import org.fest.assertions.Index;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static org.fest.assertions.Assertions.assertThat;


/**
 * Unit test for the layout theme editor
 */
@BelongsToTestGroups({THEME})
public class ThemeEditorTest extends GuiTestCase {
  @BeforeClass
  public static void runBeforeClass() {
    ThemeEditorTestUtils.enableThemeEditor();
  }

  /**
   * Checks that no errors are present in the event log
   */
  private static void checkNoErrors(@NotNull IdeFrameFixture projectFrame) {
    projectFrame.robot().waitForIdle();
    for(Notification notification : EventLog.getLogModel(projectFrame.getProject()).getNotifications()) {
      assertThat(notification.getType()).isNotEqualTo(NotificationType.ERROR);
    }
  }

  @Test @IdeGuiTest
  public void testOpenProject() throws IOException {
    // Test that we can open the simple application and the theme editor opens correctly
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorTestUtils.openThemeEditor(projectFrame);

    // Search is empty
    themeEditor.getThemePreviewPanel().getSearchTextField().requireText("");

    // Check the theme combo is populated correctly
    List<String> themeList = themeEditor.getThemesList();
    // The expected elements are:
    // 0. AppTheme
    // 1. -- Separator
    // 2. AppCompat Light
    // 3. AppCompat
    // 4. Show all themes
    // 5. -- Separator
    // 6. Create New Theme
    // 7. Rename AppTheme
    assertThat(themeList)
      .hasSize(8)
      .contains("[AppTheme]", Index.atIndex(0))
      .contains("Theme.AppCompat.Light.NoActionBar", Index.atIndex(2))
      .contains("Theme.AppCompat.NoActionBar", Index.atIndex(3))
      .contains("Show all themes", Index.atIndex(4))
      .contains("Create New Theme", Index.atIndex(6))
      .contains("Rename AppTheme", Index.atIndex(7));

    assertThat(themeList.get(1)).startsWith("javax.swing.JSeparator");
    assertThat(themeList.get(5)).startsWith("javax.swing.JSeparator");

    // Check the attributes table is populated
    assertThat(themeEditor.getPropertiesTable().rowCount()).isGreaterThan(0);

    projectFrame.getEditor().close();
    checkNoErrors(projectFrame);
  }
}
