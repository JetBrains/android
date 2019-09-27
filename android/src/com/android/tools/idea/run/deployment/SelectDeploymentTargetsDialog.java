/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SelectDeploymentTargetsDialog extends DialogWrapper {
  private static final String SELECTED_DEVICES = "SelectDeploymentTargetsDialog.selectedDevices";

  @NotNull
  private final Project myProject;

  @Nullable
  private SelectDeploymentTargetsDialogTable myTable;

  @Nullable
  private Collection<Device> mySelectedDevices;

  SelectDeploymentTargetsDialog(@NotNull Project project) {
    super(project);
    myProject = project;

    initTable();
    init();
    setTitle("Select Deployment Targets");
  }

  private void initTable() {
    myTable = new SelectDeploymentTargetsDialogTable();

    myTable.setModel(new SelectDeploymentTargetsDialogTableModel(myProject, myTable));
    myTable.getSelectionModel().addListSelectionListener(event -> getOKAction().setEnabled(myTable.getSelectedRowCount() != 0));

    String[] array = PropertiesComponent.getInstance(myProject).getValues(SELECTED_DEVICES);

    if (array == null) {
      return;
    }

    Collection<Key> collection = Arrays.stream(array)
      .map(Key::new)
      .collect(Collectors.toSet());

    myTable.setSelectedDevices(collection);
  }

  @NotNull
  Collection<Device> getSelectedDevices() {
    assert mySelectedDevices != null;
    return mySelectedDevices;
  }

  @NotNull
  @Override
  protected JComponent createCenterPanel() {
    JComponent panel = new JPanel();
    GroupLayout layout = new GroupLayout(panel);
    Component label = new JLabel("Available devices");
    Component scrollPane = new JBScrollPane(myTable);

    Group horizontalGroup = layout.createParallelGroup()
      .addComponent(label)
      .addComponent(scrollPane);

    Group verticalGroup = layout.createSequentialGroup()
      .addComponent(label)
      .addComponent(scrollPane);

    layout.setAutoCreateGaps(true);
    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    return panel;
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();

    myOKAction.setEnabled(false);
    myOKAction.putValue(Action.NAME, "Run");
  }

  @NotNull
  @Override
  public JComponent getPreferredFocusedComponent() {
    assert myTable != null;
    return myTable;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    assert myTable != null;
    mySelectedDevices = myTable.getSelectedDevices();

    String[] keys = mySelectedDevices.stream()
      .map(Device::getKey)
      .map(Key::toString)
      .toArray(String[]::new);

    PropertiesComponent.getInstance(myProject).setValues(SELECTED_DEVICES, keys);
  }
}
