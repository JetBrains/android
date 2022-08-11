/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.idea.devicemanager.DetailsPanel;
import com.android.tools.idea.devicemanager.DevicePanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import java.awt.Dimension;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.JButton;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VirtualDevicePanel extends DevicePanel {
  private final @Nullable Project myProject;
  private final @NotNull VirtualDeviceWatcher myWatcher;

  private final @NotNull JButton myCreateButton;
  private final @NotNull JSeparator mySeparator;
  private @Nullable AbstractButton myReloadButton;
  private final @NotNull JButton myHelpButton;

  public VirtualDevicePanel(@Nullable Project project, @NotNull Disposable parent) {
    this(project, parent, VirtualDeviceWatcher.getInstance());
  }

  @VisibleForTesting
  public VirtualDevicePanel(@Nullable Project project, @NotNull Disposable parent, @NotNull VirtualDeviceWatcher watcher) {
    super(project);

    myProject = project;
    myWatcher = watcher;

    initReloadButton();
    initTable();
    initScrollPane();

    myCreateButton = new JButton("Create device");
    myCreateButton.addActionListener(new BuildVirtualDeviceConfigurationWizardActionListener(myCreateButton,
                                                                                             project,
                                                                                             (VirtualDeviceTable)myTable));

    Dimension separatorSize = new JBDimension(3, 20);
    mySeparator = new JSeparator(SwingConstants.VERTICAL);
    mySeparator.setPreferredSize(separatorSize);
    mySeparator.setMaximumSize(separatorSize);

    myHelpButton = new CommonButton(AllIcons.Actions.Help);
    myHelpButton.addActionListener(event -> BrowserUtil.browse("https://d.android.com/r/studio-ui/device-manager/virtual"));

    initDetailsPanelPanel();
    layOut();

    myWatcher.addVirtualDeviceWatcherListener(getTable());
    Disposer.register(parent, this);
  }

  private void initReloadButton() {
    myReloadButton = new CommonButton(AllIcons.Actions.Refresh);
    myReloadButton.addActionListener(event -> getTable().refreshAvds());
  }

  @Override
  protected @NotNull JTable newTable() {
    VirtualDeviceTable table = new VirtualDeviceTable(this);
    Disposer.register(this, table);

    return table;
  }

  @Override
  protected @NotNull DetailsPanel newDetailsPanel() {
    return new VirtualDeviceDetailsPanel(((VirtualDeviceTable)myTable).getSelectedDevice().orElseThrow(AssertionError::new), myProject);
  }

  @NotNull VirtualDeviceTable getTable() {
    return (VirtualDeviceTable)myTable;
  }

  private void layOut() {
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(layout.createSequentialGroup()
                  .addGap(JBUIScale.scale(5))
                  .addComponent(myCreateButton)
                  .addGap(JBUIScale.scale(4))
                  .addComponent(mySeparator)
                  .addComponent(myReloadButton)
                  .addComponent(myHelpButton))
      .addComponent(myDetailsPanelPanel);

    Group verticalGroup = layout.createSequentialGroup()
      .addGroup(layout.createParallelGroup(Alignment.CENTER)
                  .addComponent(myCreateButton)
                  .addComponent(mySeparator)
                  .addComponent(myReloadButton)
                  .addComponent(myHelpButton))
      .addComponent(myDetailsPanelPanel);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  @Override
  public void dispose() {
    myWatcher.removeVirtualDeviceWatcherListener(getTable());
  }
}
