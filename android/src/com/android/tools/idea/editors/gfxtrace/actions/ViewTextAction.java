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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.impl.ui.TextViewer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class ViewTextAction extends AbstractAction {

  @NotNull private final Project myProject;
  @NotNull private final String myTitle;
  @Nullable private final Object myObject;
  @Nullable private MyDialog myDialog;

  public ViewTextAction(@NotNull Project project, @NotNull String title, @Nullable Object object) {
    super("View Text");
    myProject = project;
    myTitle = title;
    myObject = object;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myDialog == null) {
      myDialog = new MyDialog(myProject);
      myDialog.setTitle(myTitle);
      myDialog.show();
    }
    myDialog.setText(String.valueOf(myObject));
  }

  /**
   * @see com.intellij.debugger.actions.ViewTextAction.MyDialog
   */
  private static class MyDialog extends DialogWrapper {
    private final EditorTextField myTextViewer;

    private MyDialog(Project project) {
      super(project, false);
      setModal(false);
      setCancelButtonText("Close");
      setCrossClosesWindow(true);

      myTextViewer = new TextViewer(project, true, true);
      init();
    }

    public void setText(String text) {
      myTextViewer.setText(text);
    }

    @Override
    @NotNull
    protected Action[] createActions() {
      return new Action[] {getCancelAction()};
    }

    @Override
    protected JComponent createCenterPanel() {
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(myTextViewer, BorderLayout.CENTER);
      panel.setPreferredSize(JBUI.size(300, 200));
      return panel;
    }
  }
}
