/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.action;

import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCopyPasteHelper;
import com.intellij.openapi.editor.actions.BasePasteHandler;
import com.intellij.openapi.editor.actions.TextComponentEditorAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import java.awt.Component;
import javax.swing.text.JTextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a copy of {@link com.intellij.openapi.editor.actions.PasteAction} with handling for languages that can't be displayed by the
 * default font.
 */
public final class TranslationsEditorPasteAction extends TextComponentEditorAction {
  public TranslationsEditorPasteAction() {
    super(new Handler());
    copyShortcutFrom(ActionManager.getInstance().getAction("EditorPaste"));
  }

  private static final class Handler extends BasePasteHandler {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, @Nullable DataContext context) {
      // This stuff is adapted from PasteAction
      if (myTransferable == null) {
        editor.putUserData(EditorEx.LAST_PASTED_REGION, null);
      }
      else {
        TextRange[] ranges = EditorCopyPasteHelper.getInstance().pasteTransferable(editor, myTransferable);

        if (ranges != null && ranges.length == 1) {
          editor.putUserData(EditorEx.LAST_PASTED_REGION, ranges[0]);
        }
      }

      // Make sure the font can display the language
      Component component = editor.getComponent();

      if (component instanceof JTextComponent) {
        component.setFont(StringResourceEditor.getFont(component.getFont()));
      }
    }
  }
}
