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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.execution.common.debug.AndroidDebugger;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerConfigurable;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerContext;
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState;
import com.google.common.collect.Maps;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidDebuggerPanel {
  private final AndroidDebuggerContext myAndroidDebuggerContext;
  private JPanel myPanel;
  private JComboBox<AndroidDebugger> myDebuggerType;

  private JPanel myOptionPanel;
  private JComponent myOptionComponent;
  private final Map<String, AndroidDebuggerConfigurable<AndroidDebuggerState>> myConfigurables = Maps.newHashMap();

  public AndroidDebuggerPanel(@NotNull RunConfiguration runConfiguration, @NotNull AndroidDebuggerContext androidDebuggerContext) {
    setupUI();
    myAndroidDebuggerContext = androidDebuggerContext;

    myDebuggerType.setModel(new CollectionComboBoxModel(myAndroidDebuggerContext.getAndroidDebuggers()));
    myDebuggerType.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        AndroidDebugger androidDebugger = (AndroidDebugger)myDebuggerType.getSelectedItem();
        if (androidDebugger != null) {
          switchDebugOption(androidDebugger);
        }
      }
    });
    myDebuggerType.setRenderer(SimpleListCellRenderer.create("", AndroidDebugger::getDisplayName));

    for (AndroidDebugger androidDebugger : myAndroidDebuggerContext.getAndroidDebuggers()) {
      AndroidDebuggerConfigurable<AndroidDebuggerState> configurable = androidDebugger.createConfigurable(runConfiguration);
      configurable.resetFrom(myAndroidDebuggerContext.getAndroidDebuggerState(androidDebugger.getId()));
      myConfigurables.put(androidDebugger.getId(), configurable);
    }
  }

  public JComponent getComponent() {
    return myPanel;
  }

  private void switchDebugOption(@NotNull AndroidDebugger<AndroidDebuggerState> androidDebugger) {
    if (myOptionComponent != null) {
      myOptionPanel.remove(myOptionComponent);
      myOptionComponent = null;
    }

    AndroidDebuggerConfigurable<AndroidDebuggerState> configurable = getConfigurable(androidDebugger);
    configurable.resetFrom(myAndroidDebuggerContext.getAndroidDebuggerState(androidDebugger.getId()));

    myOptionComponent = configurable.getComponent();
    if (myOptionComponent != null) {
      myOptionPanel.add(myOptionComponent);
    }
  }

  public void resetFrom(@NotNull AndroidDebuggerContext androidDebuggerContext) {
    AndroidDebugger<AndroidDebuggerState> debugOption = androidDebuggerContext.getAndroidDebugger();
    if (debugOption != null) {
      myDebuggerType.setSelectedItem(debugOption);
      switchDebugOption(debugOption);
    }
  }

  public void applyTo(@NotNull AndroidDebuggerContext androidDebuggerContext) {
    AndroidDebugger<AndroidDebuggerState> androidDebugger = (AndroidDebugger)myDebuggerType.getSelectedItem();
    androidDebuggerContext.setDebuggerType(androidDebugger.getId());
    AndroidDebuggerConfigurable<AndroidDebuggerState> configurable = getConfigurable(androidDebugger);

    if (configurable != null) {
      configurable.applyTo(androidDebuggerContext.getAndroidDebuggerState(androidDebugger.getId()));
    }
  }

  @Nullable
  AndroidDebuggerConfigurable<AndroidDebuggerState> getConfigurable(@NotNull AndroidDebugger<AndroidDebuggerState> androidDebugger) {
    return myConfigurables.get(androidDebugger.getId());
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    myOptionPanel = new JPanel();
    myOptionPanel.setLayout(new BorderLayout(0, 0));
    myPanel.add(myOptionPanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("Debug type:");
    myPanel.add(label1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDebuggerType = new JComboBox();
    myDebuggerType.setEditable(false);
    myDebuggerType.setEnabled(true);
    myPanel.add(myDebuggerType, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                    0, false));
  }
}
