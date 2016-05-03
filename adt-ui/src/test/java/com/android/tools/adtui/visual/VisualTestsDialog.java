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
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Choreographer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;

/**
 * Dialog containing a series of tabs, which in turn contain a set of {@code VisualTest}.
 */
public class VisualTestsDialog extends JDialog {

  private static final Color DARCULA_COLOR = new Color(60, 63, 65);

  private static final Color DEFAULT_COLOR = new Color(244, 244, 244);

  private List<Choreographer> mChoreographers = new LinkedList<>();

  @NonNull
  private List<AnimatedComponent> mComponents = new LinkedList<>();

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

  protected VisualTestsDialog() {
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
    debugCheckbox.addActionListener(actionEvent -> {
      for (AnimatedComponent component : mComponents) {
        component.setDrawDebugInfo(debugCheckbox.isSelected());
      }
    });

    darculaCheckbox = new JCheckBox("Darcula");
    darculaCheckbox.addActionListener(
      actionEvent -> setDarculaMode(darculaCheckbox.isSelected()));

    final JButton step = createButton("Step",
                                      actionEvent -> mChoreographers.forEach(Choreographer::step));

    updateCheckbox = new JCheckBox("Update");
    updateCheckbox.addActionListener(actionEvent -> {
      mChoreographers.forEach(c -> c.setUpdate(updateCheckbox.isSelected()));
      step.setEnabled(!updateCheckbox.isSelected());
    });
    updateCheckbox.setSelected(true);

    step.setEnabled(false);
    controls.add(debugCheckbox);
    controls.add(darculaCheckbox);
    controls.add(updateCheckbox);
    controls.add(step);
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
    // Make sure to reset the components and choreographers list
    mComponents.clear();
    mChoreographers.clear();
    for (VisualTest test : mTests) {
      test.reset();
      test.registerComponents(mComponents);
      mTabs.addTab(test.getName(), test.getPanel());
      mChoreographers.add(test.getChoreographer());
    }
    // Return to previous selected tab if there was one
    if (currentTabIndex != -1) {
      mTabs.setSelectedIndex(currentTabIndex);
    }
  }

  private void setDarculaMode(boolean isDarcula) {
    for (VisualTest c : mTests) {
      if (c.getPanel() != null) {
        c.getPanel().setBackground(isDarcula ? DARCULA_COLOR : DEFAULT_COLOR);
      }
    }
  }

  @Override
  public void pack() {
    super.pack();
    resetTabs();
  }
}
