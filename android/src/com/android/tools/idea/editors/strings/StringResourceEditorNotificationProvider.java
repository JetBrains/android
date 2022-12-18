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

import static com.android.tools.idea.editors.strings.StringResourceEditorProvider.canViewTranslations;
import static com.android.tools.idea.editors.strings.StringResourceEditorProvider.openEditor;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringResourceEditorNotificationProvider implements EditorNotificationProvider {
  private boolean myHide;

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    return fileEditor -> {
      if (myHide || !canViewTranslations(project, file)) {
        return null;
      }

      final InfoPanel panel = new InfoPanel(fileEditor);
      panel.setText("Edit translations for all locales in the translations editor.");
      panel.createActionLabel("Open editor", () -> openEditor(project, file));
      panel.createActionLabel("Hide notification", () -> {
        panel.setVisible(false);
        myHide = true;
      });
      return panel;
    };
  }

  public static class InfoPanel extends EditorNotificationPanel {
    public InfoPanel(@NotNull FileEditor fileEditor) {
      super(fileEditor, null, EditorColors.READONLY_BACKGROUND_COLOR, Status.Info);
    }

    @NotNull
    @Override
    public Color getFallbackBackgroundColor() {
      return UIUtil.getPanelBackground();
    }
  }
}
