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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import org.jetbrains.annotations.NotNull;

/**
 * Wizard step with progress bar and "more details" button.
 *
 * <p>This class must be registered with a {@link Disposable} to ensure proper
 * disposal of the editor and prevent memory leaks.
 */
public class ProgressStepForm implements Disposable {
  private final ConsoleHighlighter myHighlighter;
  private final EditorEx myConsoleEditor;
  private JPanel myRoot;
  private JProgressBar myProgressBar;
  private JButton myShowDetailsButton;
  private JLabel myLabel;
  private JPanel myConsole;
  private JLabel myLabel2;
  private double myFraction = 0;

  public ProgressStepForm() {
    setupUI();
    myLabel.setText("Installing");
    myConsoleEditor = ConsoleViewUtil.setupConsoleEditor((Project)null, false, false);
    myConsoleEditor.getSettings().setUseSoftWraps(true);
    myConsoleEditor.reinitSettings();
    myConsoleEditor.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 0));
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

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myConsoleEditor);
  }

  private void setupUI() {
    myRoot = new JPanel();
    myRoot.setLayout(new GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myRoot.add(panel1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    myLabel = new JLabel();
    panel1.add(myLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                            false));
    myProgressBar = new JProgressBar();
    myProgressBar.setIndeterminate(true);
    panel1.add(myProgressBar, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    myLabel2 = new JLabel();
    panel1.add(myLabel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                             false));
    final Spacer spacer1 = new Spacer();
    myRoot.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(5, -1), null, 0, false));
    final Spacer spacer2 = new Spacer();
    myRoot.add(spacer2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(5, -1), null, 0, false));
    myShowDetailsButton = new JButton();
    myShowDetailsButton.setText("Show Details");
    myRoot.add(myShowDetailsButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    myRoot.add(spacer3, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(5, -1), null, 0, false));
    myConsole = new JPanel();
    myConsole.setLayout(new BorderLayout(0, 0));
    myRoot.add(myConsole, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                              null, 0, false));
    final Spacer spacer4 = new Spacer();
    myRoot.add(spacer4, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_FIXED, 1, null, new Dimension(5, -1), null, 0, false));
  }
}
