/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class StringResourceEditorNotificationProvider extends EditorNotifications.Provider<StringResourceEditorNotificationProvider.InfoPanel> {
  private static final Key<InfoPanel> KEY = Key.create("android.editors.strings");
  private boolean myShow = true;

  @NotNull
  @Override
  public Key<InfoPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public InfoPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (!myShow || !StringResourceEditorProvider.canViewTranslations(project, file)) {
      return null;
    }

    final InfoPanel panel = new InfoPanel();
    panel.setText("Edit translations for all locales in the translations editor.");
    panel.createActionLabel("Open editor", new Runnable() {
      @Override
      public void run() {
        StringResourceEditorProvider.openEditor(project, file);
      }
    });
    panel.createActionLabel("Hide notification", new Runnable() {
      @Override
      public void run() {
        panel.setVisible(false);
        myShow = false;
      }
    });
    return panel;
  }

  public static class InfoPanel extends EditorNotificationPanel {
    @Override
    public Color getBackground() {
      Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.READONLY_BACKGROUND_COLOR);
      return color == null ? UIUtil.getPanelBackground() : color;
    }
  }
}
