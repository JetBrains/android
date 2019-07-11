/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.editor;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link TextEditorWithPreview} in which {@link #myPreview} is a {@link DesignerEditor} and {@link #myEditor} contains the corresponding
 * XML file being displayed in the preview.
 */
public class SplitEditor extends TextEditorWithPreview {

  @NotNull
  private final Project myProject;

  @NotNull
  private final DesignerEditor myDesignerEditor;

  public SplitEditor(@NotNull TextEditor textEditor,
                     @NotNull DesignerEditor designerEditor,
                     @NotNull String editorName,
                     @NotNull Project project) {
    super(textEditor, designerEditor, editorName);
    myProject = project;
    myDesignerEditor = designerEditor;
  }

  @NotNull
  public TextEditor getTextEditor() {
    return myEditor;
  }

  @NotNull
  public DesignerEditor getDesignerEditor() {
    return myDesignerEditor;
  }

  @Nullable
  @Override
  protected ActionToolbar createToolbar() {
    return super.createToolbar();
  }

  @Override
  public void selectNotify() {
    super.selectNotify();
    // select/deselectNotify will be called when the user selects (clicks) or opens a new editor. However, in some cases, the editor might
    // be deselected but still visible. We first check whether we should pay attention to the select/deselect so we only do something if we
    // are visible.
    if (ArrayUtil.contains(this, FileEditorManager.getInstance(myProject).getSelectedEditors())) {
      myDesignerEditor.getComponent().activate();
    }
  }

  @Override
  public void deselectNotify() {
    super.deselectNotify();
    // If we are still visible but the user deselected us, do not deactivate the model since we still need to receive updates.
    if (!ArrayUtil.contains(this, FileEditorManager.getInstance(myProject).getSelectedEditors())) {
      myDesignerEditor.getComponent().deactivate();
    }
  }
}
