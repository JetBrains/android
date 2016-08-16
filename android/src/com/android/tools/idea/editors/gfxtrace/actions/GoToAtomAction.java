/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.actions;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditor;

import javax.swing.*;

public class GoToAtomAction extends AnAction {

  public GoToAtomAction() {
    super("Jump to command");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    GfxTraceEditor editor = (GfxTraceEditor)e.getData(PlatformDataKeys.FILE_EDITOR);
    assert editor != null;
    long line = editor.getAtomStream().getSelectedAtomsPath().getLast();
    long max = editor.getAtomStream().getAtomCount() - 1;
    JSpinner spinner = new JSpinner(new SpinnerNumberModel((Number)line, 0L, max, 1));

    int result = JOptionPane.showOptionDialog(editor.getComponent(), spinner, getTemplatePresentation().getText(),
                                              JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                                              null, null, null);
    if (result == JOptionPane.OK_OPTION) {
      long atomIndex = (Long)spinner.getValue();
      editor.getAtomStream().selectAtoms(atomIndex, 1, this);
    }
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    FileEditor editor = event.getData(PlatformDataKeys.FILE_EDITOR);
    presentation.setVisible(editor instanceof GfxTraceEditor);
    presentation.setEnabled(editor instanceof GfxTraceEditor && ((GfxTraceEditor)editor).getAtomStream().isLoaded());
  }
}
