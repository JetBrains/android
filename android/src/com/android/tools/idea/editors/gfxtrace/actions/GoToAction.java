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
import com.android.tools.idea.editors.gfxtrace.controllers.Controller;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditor;

import javax.swing.*;

public class GoToAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Action action = Controller.getCurrentNavigable();
    action.actionPerformed(null); // TODO maybe use AnAction here
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    FileEditor editor = event.getData(PlatformDataKeys.FILE_EDITOR);
    Action action = Controller.getCurrentNavigable();
    presentation.setVisible(editor instanceof GfxTraceEditor && action != null);
    presentation.setEnabled(editor instanceof GfxTraceEditor && action != null && action.isEnabled());
    if (action != null) {
      presentation.setText((String)action.getValue(Action.NAME));
    }
  }
}
