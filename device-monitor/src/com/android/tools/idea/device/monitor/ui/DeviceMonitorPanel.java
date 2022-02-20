/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.monitor.ui;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.idea.device.monitor.processes.Device;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionListener;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class DeviceMonitorPanel {
  static final int TEXT_RENDERER_HORIZ_PADDING = 6;
  static final int TEXT_RENDERER_VERT_PADDING = 4;
  private JComboBox myDeviceCombo;
  private JComponent myColumnTreePane;
  private JPanel myComponent;
  private JPanel myToolbarPanel;
  private ProgressPanel myProgressPanel;
  private JPanel myErrorPanel;
  private JBLabel myErrorText;
  private Tree myTree;

  public DeviceMonitorPanel() {
    myErrorPanel.setBackground(UIUtil.getTreeBackground());

    myErrorText.setFont(AdtUiUtils.EMPTY_TOOL_WINDOW_FONT);
    myErrorText.setForeground(UIUtil.getInactiveTextColor());

    // Disable toolbar until implementation is complete, as the "Device Explorer"
    // feature is enabled by default.
    //createToolbar();
  }

  @NotNull
  public JPanel getComponent() {
    return myComponent;
  }

  @NotNull
  public JComboBox<Device> getDeviceCombo() {
    //noinspection unchecked
    return myDeviceCombo;
  }

  @NotNull
  public ProgressPanel getProgressPanel() {
    return myProgressPanel;
  }

  @TestOnly
  public JBScrollPane getColumnTreePane() { return (JBScrollPane)myColumnTreePane.getComponent(0); }

  public void showMessageLayer(@NotNull String message, boolean showDeviceList) {
    showMessageLayerWorker(message, UIUtil.getInactiveTextColor(), null, showDeviceList);
  }

  @SuppressWarnings("SameParameterValue")
  public void showMessageLayer(@NotNull String message, @NotNull Icon messageIcon, boolean showDeviceList) {
    showMessageLayerWorker(message, UIUtil.getInactiveTextColor(), messageIcon, showDeviceList);
  }

  public void showErrorMessageLayer(@NotNull String errorMessage, boolean showDeviceList) {
    showMessageLayerWorker(errorMessage, JBColor.RED, null, showDeviceList);
  }

  private void showMessageLayerWorker(@NotNull String message, @NotNull Color color, @Nullable Icon icon, boolean showDeviceList) {
    myErrorText.setForeground(color);
    myErrorText.setIcon(icon);
    myDeviceCombo.setVisible(showDeviceList);
    myColumnTreePane.setVisible(false);
    // Note: In addition to having the label centered in the panel, we want the text
    // to wrap ("html") and the wrapped lines to be centered as well ("text-align").
    String htmlText = String.format("<html><div style='text-align: center;'>%s</div></html>",
                                    StringUtil.escapeXml(message));
    myErrorText.setText(htmlText);
    myErrorPanel.setVisible(true);
  }

  public void showTree() {
    myErrorPanel.setVisible(false);
    myDeviceCombo.setVisible(true);
    myColumnTreePane.setVisible(true);
    myErrorText.setText("");
  }

  public void setCancelActionListener(@Nullable ActionListener cancelActionListener) {
    myProgressPanel.setCancelActionListener(cancelActionListener);
  }

  @SuppressWarnings("unused")
  private void createToolbar() {
    final ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar("Device Explorer Toolbar",
                                                                    (DefaultActionGroup)actionManager
                                                                      .getAction("Android.DeviceExplorer.ActionsToolbar"),
                                                                    true);

    actionToolbar.setTargetComponent(myTree);
    myToolbarPanel.add(actionToolbar.getComponent(), BorderLayout.CENTER);
  }

  private void createUIComponents() {
    createTree();
  }

  private void createTree() {
    DefaultTreeModel treeModel = new DefaultTreeModel(new LoadingNode());
    myTree = new Tree(treeModel) {
      @Override
      protected boolean shouldShowBusyIconIfNeeded() {
        // By default, setPaintBusy(true) is skipped if the tree component does not have the focus.
        // By overriding this method, we ensure setPaintBusy(true) is never skipped.
        return true;
      }
    };
    myTree.setShowsRootHandles(true);
    myTree.setRootVisible(true);
    myTree.getEmptyText().setText("No debuggable process on device");
    myColumnTreePane = new ProcessListTreeBuilder().build(myTree);
  }

  @NotNull
  public Tree getTree() {
    return myTree;
  }
}
