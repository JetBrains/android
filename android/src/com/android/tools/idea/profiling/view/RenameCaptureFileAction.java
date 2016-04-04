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
package com.android.tools.idea.profiling.view;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

/**
 * Action to rename the capture files without changing the file extension.
 */
public class RenameCaptureFileAction extends DumbAwareAction {

  protected RenameCaptureFileAction(JComponent ancestorComponent) {
    super("Rename...", "Rename selected capture file", null);
    registerCustomShortcutSet(CommonShortcuts.getRename(), ancestorComponent);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    DialogWrapper dialog = new RenameDialog(project, virtualFile);
    dialog.show();
  }

  protected static class RenameDialog extends DialogWrapper {

    private final Project myProject;
    private final VirtualFile myFile;
    private JBTextField myInput;

    protected RenameDialog(@NotNull Project project, @NotNull VirtualFile file) {
      super(project);
      myProject = project;
      myFile = file;
      setTitle("Rename");
      init();
    }

    @Override
    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      String fileNameWithoutExtension = myFile.getNameWithoutExtension();
      JLabel description = new JLabel(String.format("Rename %1$s to", fileNameWithoutExtension));
      constraints.gridx = 0;
      constraints.gridy = 0;
      constraints.weightx = 1;
      constraints.insets = new Insets(0, 0 , 8, 0);
      constraints.fill = GridBagConstraints.HORIZONTAL;
      panel.add(description, constraints);
      myInput = new JBTextField(fileNameWithoutExtension);
      myInput.selectAll();
      constraints.gridy = 1;
      panel.add(myInput, constraints);

      myInput.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void textChanged(DocumentEvent documentEvent) {
          boolean isValid = StandardFileSystems.local().isValidName(myInput.getText().trim());
          setErrorText(isValid ? null : String.format("'%1$s' is not valid", myInput.getText()));
          myOKAction.setEnabled(isValid);
        }
      });
      return panel;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return null;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myInput;
    }

    @Override
    protected void doOKAction() {
      String extension = myFile.getExtension();
      final String newName = myInput.getText().trim() + (extension != null && extension.length() > 0 ? "." + extension : "");
      String commandName = String.format("Rename %1$s to %2$s", myFile.getName(), newName);
      try {
        new WriteCommandAction(myProject, commandName) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            myFile.rename(this, newName);
          }
        }.execute();
      } catch (Exception e) {
        setErrorText(String.format("Could not rename to %1$s: %2$s", newName, e.getCause().getMessage()));
        return;
      }
      super.doOKAction();
    }
  }
}
