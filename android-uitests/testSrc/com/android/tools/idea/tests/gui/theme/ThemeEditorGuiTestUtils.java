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

import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for static methods used in UI tests for the Theme Editor
 */
public class ThemeEditorGuiTestUtils {
  private ThemeEditorGuiTestUtils() {}

  @NotNull
  public static ThemeEditorFixture openThemeEditor(@NotNull IdeFrameFixture projectFrame) {
    ThemeEditorFixture themeEditor = projectFrame.getEditor()
      .open("app/src/main/res/values/styles.xml", EditorFixture.Tab.EDITOR)
      .awaitNotification("Edit all themes in the project in the theme editor.")
      .performAction("Open editor")
      .getEditor()
      .getThemeEditor();

    themeEditor.getPreviewComponent().getThemePreviewPanel().getPreviewPanel().waitForRender();

    return themeEditor;
  }
}
