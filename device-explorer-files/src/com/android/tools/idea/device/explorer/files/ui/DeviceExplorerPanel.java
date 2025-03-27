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
package com.android.tools.idea.device.explorer.files.ui;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.util.HumanReadableUtil;
import com.android.tools.idea.device.explorer.files.DeviceFileEntryNode;
import com.android.tools.idea.device.explorer.files.ErrorNode;
import com.android.tools.idea.device.explorer.files.MyLoadingNode;
import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class DeviceExplorerPanel {
  private static final int TEXT_RENDERER_HORIZ_PADDING = 6;
  private static final int TEXT_RENDERER_VERT_PADDING = 4;
  private JComponent myColumnTreePane;
  private JPanel myComponent;
  private JPanel myToolbarPanel;
  private ProgressPanel myProgressPanel;
  private JPanel myErrorPanel;
  private JBLabel myErrorText;
  private Tree myTree;

  public DeviceExplorerPanel() {
    setupUI();
    myErrorPanel.setBackground(UIUtil.getTreeBackground());

    myErrorText.setFont(AdtUiUtils.EMPTY_TOOL_WINDOW_FONT);
    myErrorText.setForeground(NamedColorUtil.getInactiveTextColor());
  }

  @NotNull
  public JPanel getComponent() {
    return myComponent;
  }

  @NotNull
  public ProgressPanel getProgressPanel() {
    return myProgressPanel;
  }

  @NotNull
  public JPanel getToolbarPanel() {
    return myToolbarPanel;
  }

  @TestOnly
  public JBScrollPane getColumnTreePane() { return (JBScrollPane)myColumnTreePane.getComponent(0); }

  public void showMessageLayer(@NotNull String message) {
    showMessageLayerWorker(message, NamedColorUtil.getInactiveTextColor(), null);
  }

  @SuppressWarnings("SameParameterValue")
  public void showMessageLayer(@NotNull String message, @NotNull Icon messageIcon) {
    showMessageLayerWorker(message, NamedColorUtil.getInactiveTextColor(), messageIcon);
  }

  public void showErrorMessageLayer(@NotNull String errorMessage) {
    showMessageLayerWorker(errorMessage, JBColor.RED, null);
  }

  private void showMessageLayerWorker(@NotNull String message, @NotNull Color color, @Nullable Icon icon) {
    myErrorText.setForeground(color);
    myErrorText.setIcon(icon);
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

    TreeSpeedSearch treeSpeedSearch = new TreeSpeedSearch(myTree, path -> {
      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(path.getLastPathComponent());
      if (node == null) {
        return null;
      }

      return node.getEntry().getName();
    }, true);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree)
      .setBackground(UIUtil.getTreeBackground())
      .setShouldPaintExpandControl(true)
      .setBorder(DEFAULT_TOP_BORDER)
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Name")
                   .setPreferredWidth(JBUI.scale(600))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new NameRenderer(treeSpeedSearch))
                   .setMinWidth(JBUI.scale(15)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Permissions")
                   .setPreferredWidth(JBUI.scale(190))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new PermissionsRenderer())
                   .setMinWidth(JBUI.scale(15)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Date")
                   .setPreferredWidth(JBUI.scale(280))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new DateRenderer())
                   .setMinWidth(JBUI.scale(15)))
      .addColumn(new ColumnTreeBuilder.ColumnBuilder()
                   .setName("Size")
                   .setPreferredWidth(JBUI.scale(130))
                   .setHeaderAlignment(SwingConstants.LEADING)
                   .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
                   .setRenderer(new SizeRenderer())
                   .setMinWidth(JBUI.scale(15))
      );
    myColumnTreePane = builder.build();
  }

  @NotNull
  public Tree getTree() {
    return myTree;
  }

  private void setupUI() {
    createUIComponents();
    myComponent = new JPanel();
    myComponent.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), 1, 1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myComponent.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myToolbarPanel = new JPanel();
    myToolbarPanel.setLayout(new BorderLayout(0, 0));
    panel1.add(myToolbarPanel,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                   false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myComponent.add(panel2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                                null, 0, false));
    panel2.add(myColumnTreePane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 0, false));
    myErrorPanel = new JPanel();
    myErrorPanel.setLayout(new BorderLayout(0, 0));
    myErrorPanel.setVisible(false);
    panel2.add(myErrorPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
    myErrorPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(120, 10, 0, 10), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myErrorText = new JBLabel();
    myErrorText.setHorizontalAlignment(0);
    myErrorText.setHorizontalTextPosition(0);
    myErrorText.setVerticalAlignment(1);
    myErrorText.setVerticalTextPosition(3);
    myErrorPanel.add(myErrorText, BorderLayout.CENTER);
    myProgressPanel = new ProgressPanel();
    myProgressPanel.setVisible(false);
    myComponent.add(myProgressPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }

  public JComponent getRootComponent() { return myComponent; }

  private static class NameRenderer extends ColoredTreeCellRenderer {
    @NotNull private final TreeSpeedSearch mySpeedSearch;

    public NameRenderer(@NotNull TreeSpeedSearch speedSearch) {
      mySpeedSearch = speedSearch;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      @Nullable Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      setToolTipText(null);
      setIcon(null);
      setIpad(JBUI.insetsRight(TEXT_RENDERER_HORIZ_PADDING));

      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(value);
      if (node != null) {
        setIcon(getIconFor(node));

        // Add name fragment (with speed search support)
        SimpleTextAttributes attr = SimpleTextAttributes.REGULAR_ATTRIBUTES;
        SearchUtil.appendFragments(mySpeedSearch.getEnteredPrefix(), node.getEntry().getName(), attr.getStyle(), attr.getFgColor(),
                                   attr.getBgColor(), this);
        if (node.isTransferring()) {
          // Transfer progress
          if (node.getTotalTransferredBytes() > 0) {
            append(String.format(" (%s / %s) ",
                                 HumanReadableUtil.getHumanizedSize(node.getCurrentTransferredBytes()),
                                 HumanReadableUtil.getHumanizedSize(node.getTotalTransferredBytes())),
                   SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
          }
          else if (node.getCurrentTransferredBytes() > 0) {
            append(String.format(" (%s) ",
                                 HumanReadableUtil.getHumanizedSize(node.getCurrentTransferredBytes())),
                   SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
          }
          else {
            appendProgress(node.getTransferringTick());
          }
        }
        String linkTarget = node.getEntry().getSymbolicLinkTarget();
        if (!StringUtil.isEmpty(linkTarget)) {
          setToolTipText("Link target: " + linkTarget);
        }
      }
      else if (value instanceof ErrorNode) {
        ErrorNode errorNode = (ErrorNode)value;
        append(errorNode.getText(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
      else if (value instanceof MyLoadingNode) {
        MyLoadingNode loadingNode = (MyLoadingNode)value;
        append("loading", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        appendProgress(loadingNode.getTick());
      }
    }

    private void appendProgress(int tick) {
      // "..." text, moving left to right and back according to tick
      int mod = 20;
      int progressTick = (tick % mod);
      if (progressTick >= (mod / 2)) {
        progressTick = mod - progressTick;
      }
      append(StringUtil.repeatSymbol(' ', progressTick));
      append(StringUtil.repeatSymbol('.', 3));
    }

    @NotNull
    private static Icon getIconFor(@NotNull DeviceFileEntryNode node) {
      Icon icon = getIconForImpl(node);
      if (node.getEntry().isSymbolicLink()) {
        return new LayeredIcon(icon, PlatformIcons.SYMLINK_ICON);
      }
      return icon;
    }

    @NotNull
    private static Icon getIconForImpl(@NotNull DeviceFileEntryNode node) {
      DeviceFileEntry entry = node.getEntry();
      if (entry.isDirectory() || node.isSymbolicLinkToDirectory()) {
        return AllIcons.Nodes.Folder;
      }
      else if (entry.isFile() || entry.isSymbolicLink()) {
        LightVirtualFile file = new LightVirtualFile(entry.getName());
        Icon ftIcon = file.getFileType().getIcon();
        return ftIcon == null ? AllIcons.FileTypes.Any_type : ftIcon;
      }
      else {
        return AllIcons.FileTypes.Any_type;
      }
    }
  }

  private static class PermissionsRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      @Nullable Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(value);
      if (node != null) {
        append(node.getEntry().getPermissions().getText());
      }
      setIpad(JBUI.insets(0, TEXT_RENDERER_HORIZ_PADDING));
    }
  }

  private static class DateRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      @Nullable Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(value);
      if (node != null) {
        DeviceFileEntry.DateTime date = node.getEntry().getLastModifiedDate();
        append(date.getText());
      }
      setIpad(JBUI.insets(0, TEXT_RENDERER_HORIZ_PADDING));
    }
  }

  private static class SizeRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      @Nullable Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(value);
      if (node != null) {
        // If node is uploading, show the number of bytes uploaded instead of the last known size
        long size = node.isUploading() ? node.getCurrentTransferredBytes() : node.getEntry().getSize();
        if (size >= 0) {
          setTextAlign(SwingConstants.RIGHT);
          append(HumanReadableUtil.getHumanizedSize(size));
        }
      }
      setIpad(JBUI.insets(0, TEXT_RENDERER_HORIZ_PADDING));
    }
  }
}
