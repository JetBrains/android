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
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.fest.assertions.Index;
import org.fest.swing.annotation.GUITest;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertNotNull;


/**
 * Unit test for the layout theme editor
 */
@BelongsToTestGroups({THEME})
public class ThemeEditorTest extends GuiTestCase {
  @BeforeClass
  public static void runBeforeClass() {
    System.setProperty("enable.theme.editor", "true");
  }

  /**
   * Checks that no errors are present in the event log
   */
  private static void checkNoErrors(@NotNull Project project) {
    for(Notification notification : EventLog.getLogModel(project).getNotifications()) {
      assertThat(notification.getType()).isNotEqualTo(NotificationType.ERROR);
    }
  }

  @Test
  @GUITest
  public void testOpenProject() throws IOException {
    // Test that we can open the simple application and the theme editor opens correctly
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = openThemeEditor(projectFrame);

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
    checkNoErrors(projectFrame.getProject());
  }

  @Test @IdeGuiTest
  public void testRenameTheme() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = openThemeEditor(projectFrame);

    final JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();
    themesComboBox.selectItem("Rename AppTheme");

    RenameRefactoringDialogFixture renameRefactoringDialog = RenameRefactoringDialogFixture.find(myRobot);
    renameRefactoringDialog.setNewName("NewAppTheme").clickRefactor();

    pause(new Condition("Waiting for renaming to be finished") {
      @Override
      public boolean test() {
        return "[NewAppTheme]".equals(themesComboBox.selectedItem());
      }
    }, GuiTests.SHORT_TIMEOUT);

    themesComboBox.requireSelection("[NewAppTheme]");

    List<String> themeList = themeEditor.getThemesList();
    assertThat(themeList)
      .hasSize(8)
      .contains("[NewAppTheme]", Index.atIndex(0))
      .contains("Theme.AppCompat.Light.NoActionBar", Index.atIndex(2))
      .contains("Theme.AppCompat.NoActionBar", Index.atIndex(3))
      .contains("Show all themes", Index.atIndex(4))
      .contains("Create New Theme", Index.atIndex(6))
      .contains("Rename NewAppTheme", Index.atIndex(7));
  }

  @Test @IdeGuiTest
  public void testNoRenameForReadOnlyTheme() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = openThemeEditor(projectFrame);

    JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();
    themesComboBox.selectItem("Theme.AppCompat.NoActionBar"); // AppCompat is read-only, being a library theme

    List<String> themeList = themeEditor.getThemesList();
    assertThat(themeList)
      .hasSize(7)
      .contains("[AppTheme]", Index.atIndex(0))
      .contains("Theme.AppCompat.Light.NoActionBar", Index.atIndex(2))
      .contains("Theme.AppCompat.NoActionBar", Index.atIndex(3))
      .contains("Show all themes", Index.atIndex(4))
      .contains("Create New Theme", Index.atIndex(6));
  }

  @NotNull
  public static ThemeEditorFixture openThemeEditor(@NotNull IdeFrameFixture projectFrame) {
    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/values/styles.xml", EditorFixture.Tab.EDITOR);
    EditorNotificationPanelFixture notificationPanel =
      projectFrame.requireEditorNotification("Edit all themes in the project in the theme editor.");
    notificationPanel.performAction("Open editor");

    ThemeEditorFixture themeEditor = editor.getThemeEditor();
    assertNotNull(themeEditor);

    themeEditor.getThemePreviewPanel().getPreviewPanel().waitForRender();

    return themeEditor;
  }
}
