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
package com.android.tools.idea.fd.actions;

import com.android.tools.idea.fd.FlightRecorder;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.*;
import com.intellij.ui.SingleSelectionModel;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;

public class InstantRunFeedbackDialog extends DialogWrapper {
  private final List<Path> myLogs;

  private JPanel myPanel;
  private JTextArea myIssueTextArea;
  private JBList myFilesList;
  private String myIssueText;

  protected InstantRunFeedbackDialog(@NotNull Project project) {
    super(project);

    myFilesList.setVisibleRowCount(4);
    myFilesList.setEmptyText("No Log Files found");
    myLogs = FlightRecorder.get(project).getAllLogs();
    myFilesList.setModel(new CollectionListModel<>(myLogs));
    myFilesList.setCellRenderer(new ColoredListCellRenderer<Path>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Path value, int index, boolean selected, boolean hasFocus) {
        append(value.toString());
      }
    });

    myFilesList.setSelectionModel(new SingleSelectionModel());
    myFilesList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (event.getButton() == MouseEvent.BUTTON1) {
          Object selectedValue = myFilesList.getSelectedValue();
          if (selectedValue instanceof Path) {
            ShowFilePathAction.openFile(((Path)selectedValue).toFile());
            return true;
          }
        }
        return false;
      }
    }.installOn(myFilesList);

    setTitle("Report Instant Run Issue");
    setModal(true);
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @NotNull
  public List<Path> getLogs() {
    return myLogs;
  }

  @NotNull
  public String getIssueText() {
    return myIssueText;
  }

  @Override
  protected void doOKAction() {
    myIssueText = getIssueReport();
    super.doOKAction();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String issueReport = getIssueReport();
    if (issueReport.isEmpty()) {
      return new ValidationInfo("Please describe the issue", myIssueTextArea);
    }
    return super.doValidate();
  }

  private String getIssueReport() {
    Document document = myIssueTextArea.getDocument();
    try {
      return document.getText(0, document.getLength());
    }
    catch (BadLocationException e) { // can't happen since we explicitly get from [0..length]
      assert false : e.toString();
      return e.toString();
    }
  }
}
