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

import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * Utility class for static methods used in UI tests for the Theme Editor
 */
public class ThemeEditorTestUtils {
  private ThemeEditorTestUtils() {}

  @NotNull
  public static ThemeEditorFixture openThemeEditor(@NotNull IdeFrameFixture projectFrame) {
    // Makes sure Android Studio has focus, useful when running with a window manager
    projectFrame.focus();

    EditorFixture editor = projectFrame.getEditor();
    editor.open("app/src/main/res/values/styles.xml", EditorFixture.Tab.EDITOR);
    EditorNotificationPanelFixture notificationPanel =
      projectFrame.requireEditorNotification("Edit all themes in the project in the theme editor.");
    notificationPanel.performAction("Open editor");

    ThemeEditorFixture themeEditor = editor.getThemeEditor();

    themeEditor.getThemePreviewPanel().getPreviewPanel().waitForRender();

    return themeEditor;
  }

  public static void enableThemeEditor() {
    // TODO: remove once the theme editor flag has been removed
    System.setProperty("enable.theme.editor", "true");
  }

  /**
   * Returns the attributes that were defined in the theme itself and not its parents.
   */
  public static Collection<EditedStyleItem> getStyleLocalValues(@NotNull final ThemeEditorStyle style) {
    final Set<String> localAttributes = style.getConfiguredValues().keySet();

    return Collections2.filter(ThemeEditorUtils.resolveAllAttributes(style, null), new Predicate<EditedStyleItem>() {
      @Override
      public boolean apply(@javax.annotation.Nullable EditedStyleItem input) {
        assert input != null;
        return localAttributes.contains(input.getQualifiedName());
      }
    });
  }
}
