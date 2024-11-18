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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.tools.idea.welcome.wizard.ConsoleHighlighter;
import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.jetbrains.annotations.NotNull;

/**
 * Wizard step with progress bar and "more details" button.
 */
public class ProgressStepForm {
  private final ConsoleHighlighter myHighlighter;
  private final EditorEx myConsoleEditor;
  private JPanel myRoot;
  private JProgressBar myProgressBar;
  private JButton myShowDetailsButton;
  private JLabel myLabel;
  private JPanel myConsole;
  private JLabel myLabel2;
  private double myFraction = 0;

  public ProgressStepForm(@NotNull Disposable parent) {
    myLabel.setText("Installing");
    myConsoleEditor = ConsoleViewUtil.setupConsoleEditor((Project)null, false, false);
    myConsoleEditor.getSettings().setUseSoftWraps(true);
    myConsoleEditor.reinitSettings();
    myConsoleEditor.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 0));
    Disposer.register(parent, () -> EditorFactory.getInstance().releaseEditor(myConsoleEditor));
    myHighlighter = new ConsoleHighlighter();
    myHighlighter.setModalityState(ModalityState.stateForComponent(myLabel));
    myConsoleEditor.setHighlighter(myHighlighter);
    JComponent editorComponent = myConsoleEditor.getComponent();
    myConsole.add(editorComponent, BorderLayout.CENTER);
    editorComponent.setVisible(false);
    myShowDetailsButton.addActionListener(e -> showConsole());
  }

  /**
   * Output text to the console pane.
   *
   * @param s           text to print
   * @param contentType attributes of the text to output
   */
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
    myHighlighter.setModalityState(ModalityState.stateForComponent(myConsole));
    myHighlighter.print(s + (s.endsWith("\n") ? "" : "\n"), contentType.getAttributes());
  }

  /**
   * Will output process standard in and out to the console view.
   * <p/>
   * Note: current version does not support collecting user input. We may
   * reconsider this at a later point.
   *
   * @param processHandler process to track
   */
  public void attachToProcess(ProcessHandler processHandler) {
    myHighlighter.attachToProcess(processHandler);
  }

  /**
   * Displays console widget if one was not visible already
   */
  public void showConsole() {
    ApplicationManager.getApplication().invokeLater(() -> {
      JComponent editorComponent = myConsoleEditor.getComponent();
      if (!editorComponent.isVisible()) {
        myShowDetailsButton.getParent().remove(myShowDetailsButton);
        editorComponent.setVisible(true);
      }
    });
  }

  public void setFraction(double fraction) {
    myFraction = fraction;
    myProgressBar.setMaximum(1000);
    myProgressBar.setValue((int)(1000 * fraction));
  }

  public double getFraction() {
    return myFraction;
  }

  public JComponent getRoot() {
    return myRoot;
  }

  public JLabel getLabel() {
    return myLabel;
  }

  public JLabel getLabel2() {
    return myLabel2;
  }

  public JButton getShowDetailsButton() {
    return myShowDetailsButton;
  }

  public @NotNull JProgressBar getProgressBar() {
    return myProgressBar;
  }
}
