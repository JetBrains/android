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
package com.android.tools.idea.editors;

import com.android.tools.idea.editors.strings.StringResourceEditorProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringResourceEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.editors.strings");
  private final Project myProject;
  private boolean myShow;

  public StringResourceEditorNotificationProvider(@NotNull Project project) {
    myProject = project;
    myShow = true;
  }

  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, FileEditor fileEditor) {
    if (!myShow || !StringResourceEditorProvider.canViewTranslations(myProject, file)) {
      return null;
    }

    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("Translation editor available");
    panel.createActionLabel("Open editor", new Runnable() {
      @Override
      public void run() {
        StringResourceEditorProvider.openEditor(myProject, file);
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
}
