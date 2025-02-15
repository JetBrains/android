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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

public class ChooseModuleDialog extends DialogWrapper {
  @NotNull private final Consumer<PsModule> myOnOkTask;

  private JPanel myPanel;
  private JBList myModuleList;

  public ChooseModuleDialog(@NotNull PsProject project, @NotNull Consumer<PsModule> onOkTask, @NotNull String title) {
    super(project.getIdeProject());
    setupUI();
    myOnOkTask = onOkTask;
    init();
    setTitle(title);

    DefaultListModel<PsModule> listModel = new DefaultListModel<>();
    project.forEachModule(listModel::addElement);

    //noinspection unchecked
    myModuleList.setModel(listModel);
    myModuleList.setSelectionMode(SINGLE_SELECTION);
    myModuleList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (c instanceof JLabel && value instanceof PsModule) {
          PsModule module = (PsModule)value;
          JLabel label = (JLabel)c;
          label.setText(module.getName());
          label.setIcon(module.getIcon());
        }
        return c;
      }
    });

    if (!myModuleList.isEmpty()) {
      myModuleList.setSelectedIndex(0);
    }
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return "psd.choose.module.panel.dimension";
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myModuleList;
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    if (getSelectedModule() == null) {
      return new ValidationInfo("Please select a module", myModuleList);
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    PsModule selectedModule = getSelectedModule();
    assert selectedModule != null;
    myOnOkTask.accept(selectedModule);
  }

  @Nullable
  private PsModule getSelectedModule() {
    Object selectedValue = myModuleList.getSelectedValue();
    return selectedValue instanceof PsModule ? (PsModule)selectedValue : null;
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.setPreferredSize(new Dimension(350, 200));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Please select a module:");
    myPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                              false));
    final JBScrollPane jBScrollPane1 = new JBScrollPane();
    myPanel.add(jBScrollPane1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
    myModuleList = new JBList();
    myModuleList.setSelectionMode(0);
    jBScrollPane1.setViewportView(myModuleList);
    final JBLabel jBLabel2 = new JBLabel();
    myPanel.add(jBLabel2,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }
}
