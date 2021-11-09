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
import com.android.tools.idea.devicemanager.DetailsPanelPanel2;
import com.android.tools.idea.devicemanager.DetailsPanelPanelListSelectionListener;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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
  private final @NotNull JButton myHelpButton;

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

    myHelpButton = new CommonButton(AllIcons.Actions.Help);
    myHelpButton.addActionListener(event -> BrowserUtil.browse("https://d.android.com/r/studio-ui/device-manager/virtual"));

    layOut();
    Disposer.register(parent, this);
  }

  private void initTable() {
    myTable = new VirtualDeviceTable(myProject);

    if (!DetailsPanelPanel2.ENABLED) {
      myTable.getSelectionModel().addListSelectionListener(new DetailsPanelPanelListSelectionListener<>(this));
    }
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
    if (DetailsPanelPanel2.ENABLED) {
      layOut2();
      return;
    }

    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(layout.createSequentialGroup()
                  .addGap(JBUIScale.scale(5))
                  .addComponent(myCreateButton)
                  .addGap(JBUIScale.scale(4))
                  .addComponent(mySeparator)
                  .addComponent(myHelpButton))
      .addComponent(myScrollPane);

    if (myDetailsPanel != null) {
      horizontalGroup.addComponent(myDetailsPanel);
    }

    Group verticalGroup = layout.createSequentialGroup()
      .addGroup(layout.createParallelGroup(Alignment.CENTER)
                  .addComponent(myCreateButton)
                  .addComponent(mySeparator)
                  .addComponent(myHelpButton))
      .addComponent(myScrollPane, 0, 0, Short.MAX_VALUE);

    if (myDetailsPanel != null) {
      verticalGroup.addComponent(myDetailsPanel, 0, 0, JBUIScale.scale(240));
    }

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  private void layOut2() {
    Component panel = new DetailsPanelPanel2(myScrollPane);
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createParallelGroup()
      .addGroup(layout.createSequentialGroup()
                  .addGap(JBUIScale.scale(5))
                  .addComponent(myCreateButton)
                  .addGap(JBUIScale.scale(4))
                  .addComponent(mySeparator)
                  .addComponent(myHelpButton))
      .addComponent(panel);

    Group verticalGroup = layout.createSequentialGroup()
      .addGroup(layout.createParallelGroup(Alignment.CENTER)
                  .addComponent(myCreateButton)
                  .addComponent(mySeparator)
                  .addComponent(myHelpButton))
      .addComponent(panel);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }
}
