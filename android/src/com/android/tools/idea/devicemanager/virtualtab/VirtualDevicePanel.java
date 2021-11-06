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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.idea.avdmanager.AvdUiAction.AvdInfoProvider;
import com.android.tools.idea.avdmanager.CreateAvdAction;
import com.android.tools.idea.devicemanager.DetailsPanel;
import com.android.tools.idea.devicemanager.DetailsPanelPanel;
import com.android.tools.idea.devicemanager.DetailsPanelPanelListSelectionListener;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.JButton;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public final class VirtualDevicePanel extends JBPanel<VirtualDevicePanel> implements Disposable, DetailsPanelPanel<AvdInfo> {
  private final @NotNull JButton myCreateButton;
  private final @NotNull JSeparator mySeparator;
  private @Nullable JButton myRefreshButton;
  private final @NotNull JButton myHelpButton;
  private @Nullable SearchTextField mySearchTextField;

  private final @Nullable Project myProject;
  private final @NotNull Component myScrollPane;
  private VirtualDeviceTable myTable;
  private @Nullable DetailsPanel myDetailsPanel;

  public VirtualDevicePanel(@Nullable Project project, @NotNull Disposable parent) {
    this(project, parent, CreateAvdAction::new);
  }

  @VisibleForTesting
  VirtualDevicePanel(@Nullable Project project,
                     @NotNull Disposable parent,
                     @NotNull Function<@NotNull AvdInfoProvider, @NotNull ActionListener> createAvdActionProvider) {
    myProject = project;
    initTable();
    myScrollPane = new JBScrollPane(myTable);

    myCreateButton = new JButton("Create device");
    myCreateButton.addActionListener(createAvdActionProvider.apply(myTable));

    Dimension separatorSize = new JBDimension(3, 20);
    mySeparator = new JSeparator(SwingConstants.VERTICAL);
    mySeparator.setPreferredSize(separatorSize);
    mySeparator.setMaximumSize(separatorSize);

    if (enableHalfBakedFeatures()) {
      myRefreshButton = new CommonButton(AllIcons.Actions.Refresh);
      myRefreshButton.addActionListener(event -> myTable.refreshAvds());
    }

    myHelpButton = new CommonButton(AllIcons.Actions.Help);
    myHelpButton.addActionListener(event -> BrowserUtil.browse("https://d.android.com/r/studio-ui/device-manager/virtual"));

    if (enableHalfBakedFeatures()) {
      mySearchTextField = new SearchTextField(true);
      mySearchTextField.setToolTipText("Search virtual devices by name");
    }

    setLayout(createGroupLayout());

    Disposer.register(parent, this);
  }

  private void initTable() {
    myTable = new VirtualDeviceTable(myProject);
    myTable.getSelectionModel().addListSelectionListener(new DetailsPanelPanelListSelectionListener<>(this));
  }

  private @NotNull GroupLayout createGroupLayout() {
    GroupLayout groupLayout = new GroupLayout(this);

    Group toolbarHorizontalGroup = createToolbarHorizontalGroup(groupLayout);
    Group toolbarVerticalGroup = createToolbarVerticalGroup(groupLayout);

    Group horizontalGroup = groupLayout.createParallelGroup(Alignment.LEADING)
      .addGroup(toolbarHorizontalGroup)
      .addComponent(myScrollPane);

    if (myDetailsPanel != null) {
      horizontalGroup.addComponent(myDetailsPanel);
    }

    Group verticalGroup = groupLayout.createSequentialGroup()
      .addGroup(toolbarVerticalGroup)
      .addComponent(myScrollPane, 0, 0, Short.MAX_VALUE);

    if (myDetailsPanel != null) {
      verticalGroup.addComponent(myDetailsPanel, 0, 0, JBUIScale.scale(240));
    }

    groupLayout.setHorizontalGroup(horizontalGroup);
    groupLayout.setVerticalGroup(verticalGroup);
    return groupLayout;
  }

  private @NotNull Group createToolbarHorizontalGroup(@NotNull GroupLayout groupLayout) {
    Group toolbarHorizontalGroup = groupLayout.createSequentialGroup()
      .addGap(JBUIScale.scale(5))
      .addComponent(myCreateButton, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
      .addGap(JBUIScale.scale(4))
      .addComponent(mySeparator, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

    if (myRefreshButton != null) {
      toolbarHorizontalGroup
        .addComponent(myRefreshButton, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
    }

    toolbarHorizontalGroup.addComponent(myHelpButton, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

    if (mySearchTextField != null) {
      toolbarHorizontalGroup
        .addComponent(mySearchTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE);
    }

    return toolbarHorizontalGroup;
  }

  private @NotNull Group createToolbarVerticalGroup(@NotNull GroupLayout groupLayout) {
    Group toolbarVerticalGroup = groupLayout.createParallelGroup(Alignment.CENTER);
    toolbarVerticalGroup.addComponent(myCreateButton, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
      .addComponent(mySeparator, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

    if (myRefreshButton != null) {
      toolbarVerticalGroup.addComponent(myRefreshButton, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
    }

    toolbarVerticalGroup.addComponent(myHelpButton, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);

    if (mySearchTextField != null) {
      toolbarVerticalGroup
        .addComponent(mySearchTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
    }

    return toolbarVerticalGroup;
  }

  private static boolean enableHalfBakedFeatures() {
    return StudioFlags.ENABLE_DEVICE_MANAGER_HALF_BAKED_FEATURES.get();
  }

  @Override
  public void dispose() {
    if (myDetailsPanel != null) {
      Disposer.dispose(myDetailsPanel);
    }
  }

  @VisibleForTesting
  @NotNull JButton getCreateButton() {
    return myCreateButton;
  }

  @Override
  public @NotNull Optional<@NotNull AvdInfo> getSelectedDevice() {
    return myTable.getSelectedDevice();
  }

  @Override
  public boolean containsDetailsPanel() {
    return myDetailsPanel != null;
  }

  @Override
  public void removeDetailsPanel() {
    assert myDetailsPanel != null;

    remove(myDetailsPanel);
    Disposer.dispose(myDetailsPanel);
    myDetailsPanel = null;
  }

  @Override
  public void initDetailsPanel(@NotNull AvdInfo device) {
    myDetailsPanel = new VirtualDeviceDetailsPanel(device);

    myDetailsPanel.getCloseButton().addActionListener(event -> {
      myTable.clearSelection();

      removeDetailsPanel();
      layOut();
    });
  }

  @Override
  public void layOut() {
    setLayout(createGroupLayout());
  }
}
