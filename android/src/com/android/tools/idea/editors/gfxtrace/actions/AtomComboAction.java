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

import com.android.tools.idea.configurations.FlatComboAction;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class AtomComboAction extends FlatComboAction {

  @NotNull private final GfxTraceEditor myGfxTraceEditor;
  @NotNull private List<Long> myAtomIds = Collections.emptyList();

  public AtomComboAction(@NotNull GfxTraceEditor editor) {
    myGfxTraceEditor = editor;
  }

  public void setAtomIds(@NotNull List<Long> atomIds) {
    myAtomIds = atomIds;
  }

  @Override
  protected FlatComboButton createComboBoxButton(@NotNull Presentation presentation) {
    presentation.setIcon(AndroidIcons.GfxTrace.Jump);
    presentation.setDescription("Accesses / Modifications");
    return super.createComboBoxButton(presentation);
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();

    for (final Long atomIndex : myAtomIds) {
      AnAction action = new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (atomIndex != null) {
            myGfxTraceEditor.getAtomStream().selectAtom(atomIndex, this);
          }
        }
      };
      actionGroup.add(action);
      Presentation presentation = action.getTemplatePresentation();

      Atom atom = myGfxTraceEditor.getAtomStream().getAtom(atomIndex);
      presentation.setText(atomIndex + ": " + atom.getName());
    }

    return actionGroup;
  }
}
