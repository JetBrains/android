/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.observable.demo;

import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.string.StringExpression;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.Insets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static com.android.tools.idea.observable.expressions.bool.BooleanExpressions.any;

/**
 * Any checkboxes checked?
 * <p/>
 * This panel shows "Yes" if any of four checkboxes are checked.
 * <p/>
 * Demonstrates:
 * - How to wrap a collection of elements into a single expression
 * - How to create a one-off expression
 */
public final class AnyCheckedDemo {
  private JPanel myRootPanel;
  private JLabel myYesNoLabel;
  private JCheckBox myCheckBox1;
  private JCheckBox myCheckBox2;
  private JCheckBox myCheckBox3;
  private JCheckBox myCheckBox4;

  private BindingsManager myBindings = new BindingsManager();

  public AnyCheckedDemo() {
    setupUI();
  }

  private void setupUI() {
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new GridLayoutManager(1, 6, new Insets(0, 0, 0, 0), -1, -1));
    final JLabel label1 = new JLabel();
    label1.setText("Any?");
    myRootPanel.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                false));
    myYesNoLabel = new JLabel();
    myYesNoLabel.setText("Label");
    myRootPanel.add(myYesNoLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                      0, false));
    myCheckBox1 = new JCheckBox();
    myCheckBox1.setText("1");
    myRootPanel.add(myCheckBox1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myCheckBox2 = new JCheckBox();
    myCheckBox2.setText("4");
    myRootPanel.add(myCheckBox2, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myCheckBox3 = new JCheckBox();
    myCheckBox3.setText("3");
    myRootPanel.add(myCheckBox3, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myCheckBox4 = new JCheckBox();
    myCheckBox4.setText("2");
    myRootPanel.add(myCheckBox4, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }

  public static void main(String[] args) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      final JFrame frame = new JFrame("AnyCheckedDemo");
      final AnyCheckedDemo demo = new AnyCheckedDemo();
      frame.setContentPane(demo.myRootPanel);
      demo.init();
      frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
      frame.pack();
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
      frame.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          demo.dispose();
        }
      });
    });
  }

  public void init() {
    StringProperty yesNoText = new TextProperty(myYesNoLabel);
    myBindings.bind(yesNoText, new YesNoExpression(
      any(new SelectedProperty(myCheckBox1), new SelectedProperty(myCheckBox2), new SelectedProperty(myCheckBox3),
          new SelectedProperty(myCheckBox4))));
  }

  public void dispose() {
    myBindings.releaseAll(); // You should always remember to release your bindings!
  }

  /**
   * One-off expression that converts a boolean to "Yes" or "No"
   */
  private static final class YesNoExpression extends StringExpression {
    private final ObservableValue<Boolean> myValue;

    public YesNoExpression(ObservableValue<Boolean> value) {
      super(value);
      myValue = value;
    }

    @NotNull
    @Override
    public String get() {
      return myValue.get() ? "Yes" : "No";
    }
  }
}
