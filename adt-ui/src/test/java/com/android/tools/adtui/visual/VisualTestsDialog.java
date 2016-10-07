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

package com.android.tools.adtui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Choreographer;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;

/**
 * Dialog containing a series of tabs, which in turn contain a set of {@code VisualTest}.
 */
public class VisualTestsDialog extends JDialog {

  private List<Choreographer> mChoreographers = new LinkedList<>();

  @NonNull
  protected List<VisualTest> mTests = new LinkedList<>();

  @NonNull
  private final JTabbedPane mTabs;

  /**
   * Checkbox for entering/exiting debug mode.
   */
  private JCheckBox debugCheckbox;
  /**
   * Checkbox for activating/deactivating darcula mode.
   */
  private JCheckBox darculaCheckbox;
  /**
   * Checkbox for activating/deactivating continuous charts update.
   */
  private JCheckBox updateCheckbox;
  /**
   * Button for advancing the Choreographer by single steps.
   */
  private JButton stepButton;

  public VisualTestsDialog() {
    final JPanel contentPane = new JPanel(new BorderLayout());
    mTabs = new JTabbedPane();

    contentPane.setPreferredSize(new Dimension(1280, 1024));
    contentPane.add(mTabs, BorderLayout.CENTER);

    JPanel bottom = new JPanel(new BorderLayout());
    Box bottomButtonsBox = Box.createHorizontalBox();

    JButton close = createButton("Close", actionEvent -> dispose());

    JButton reset = createButton("Reset", actionEvent -> {
      resetTabs();
      resetControls();
    });

    bottomButtonsBox.add(reset);
    bottomButtonsBox.add(close);
    bottom.add(bottomButtonsBox, BorderLayout.EAST);
    contentPane.add(bottom, BorderLayout.SOUTH);

    JPanel controls = new JPanel();
    controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
    controls.add(Box.createRigidArea(new Dimension(100, 20)));

    debugCheckbox = new JCheckBox("Debug");
    debugCheckbox.addActionListener(actionEvent -> setDebugMode(debugCheckbox.isSelected()));

    darculaCheckbox = new JCheckBox("Darcula");
    darculaCheckbox.addActionListener(actionEvent -> setDarculaMode(darculaCheckbox.isSelected()));

    stepButton = createButton("Step", actionEvent -> mChoreographers.forEach(Choreographer::step));

    updateCheckbox = new JCheckBox("Update");
    updateCheckbox.addActionListener(actionEvent -> setUpdateMode(updateCheckbox.isSelected()));
    updateCheckbox.setSelected(true);

    stepButton.setEnabled(false);
    controls.add(debugCheckbox);
    controls.add(darculaCheckbox);
    controls.add(updateCheckbox);
    controls.add(stepButton);
    contentPane.add(controls, BorderLayout.WEST);

    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(close);
  }

  public void addTest(VisualTest test) {
    mTests.add(test);
  }

  private static JButton createButton(String label, ActionListener actionListener) {
    final JButton button = new JButton(label);
    button.addActionListener(actionListener);
    return button;
  }

  private void resetControls() {
    debugCheckbox.setSelected(false);
    darculaCheckbox.setSelected(false);
    updateCheckbox.setSelected(true);
  }

  private void resetTabs() {
    int currentTabIndex = mTabs.getSelectedIndex();
    mTabs.removeAll();
    mChoreographers.clear();
    for (VisualTest test : mTests) {
      test.reset();
      mTabs.addTab(test.getName(), test.getPanel());
      mChoreographers.add(test.getChoreographer());
    }
    // Return to previous selected tab if there was one
    if (currentTabIndex != -1) {
      mTabs.setSelectedIndex(currentTabIndex);
    }
  }

  private void setDarculaMode(boolean isDarcula) {
    try {
      if (isDarcula) {
        UIManager.setLookAndFeel(new DarculaLaf());
      } else {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
      JBColor.setDark(isDarcula);
      resetTabs();

      setUpdateMode(updateCheckbox.isSelected());
      setDebugMode(debugCheckbox.isSelected());
    } catch (Exception ignored) {}
  }

  private void setDebugMode(boolean isDebug) {
    for (Choreographer choreographer : mChoreographers) {
      choreographer.toggleDebug(isDebug);
    }
  }

  private void setUpdateMode(boolean continuousUpdate) {
    mChoreographers.forEach(c -> c.setUpdate(continuousUpdate));
    stepButton.setEnabled(!continuousUpdate);
  }

  @Override
  public void pack() {
    super.pack();
    resetTabs();
  }
}
