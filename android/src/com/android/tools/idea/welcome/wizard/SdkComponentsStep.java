/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard;

import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.npw.WizardUtils.ValidationResult;
import com.android.tools.idea.npw.WizardUtils.ValidationResult.Status;
import com.android.tools.idea.npw.WizardUtils.WritableCheckMode;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.ui.Colors;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.ComponentTreeNode;
import com.android.tools.idea.welcome.install.InstallableComponent;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Wizard page for selecting SDK components to download.
 */
public class SdkComponentsStep extends FirstRunWizardStep {
  public static final String FIELD_SDK_LOCATION = "SDK location";

  @NotNull private final ComponentTreeNode myRootNode;
  @NotNull private final FirstRunWizardMode myMode;
  @NotNull private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;
  private final ScopedStateStore.Key<String> mySdkDownloadPathKey;
  private final ComponentsTableModel myTableModel;

  private JPanel myContents;
  private JBTable myComponentsTable;
  private JTextPane myComponentDescription;
  private JLabel myNeededSpace;
  private JLabel myAvailableSpace;
  private JLabel myErrorMessage;
  private TextFieldWithBrowseButton myPath;
  @SuppressWarnings("unused") private JPanel myBody;

  private boolean myUserEditedPath = false;
  private ValidationResult mySdkDirectoryValidationResult;
  private boolean myWasVisible = false;

  public SdkComponentsStep(@NotNull ComponentTreeNode rootNode,
                           @NotNull ScopedStateStore.Key<Boolean> keyCustomInstall,
                           @NotNull ScopedStateStore.Key<String> sdkDownloadPathKey,
                           @NotNull FirstRunWizardMode mode) {
    super("SDK Components Setup");
    // Since we create and initialize a new AndroidSdkHandler/RepoManager for every (partial)
    // path that's entered, disallow direct editing of the path.
    myPath.setEditable(false);
    myRootNode = rootNode;
    myMode = mode;
    myKeyCustomInstall = keyCustomInstall;

    // noinspection DialogTitleCapitalization
    myPath.addBrowseFolderListener("Android SDK", "Select Android SDK install directory", null,
                                   FileChooserDescriptorFactory.createSingleFolderDescriptor());

    mySdkDownloadPathKey = sdkDownloadPathKey;
    Font smallLabelFont = JBUI.Fonts.smallFont();
    myNeededSpace.setFont(smallLabelFont);
    myAvailableSpace.setFont(smallLabelFont);
    myErrorMessage.setText(null);

    myTableModel = new ComponentsTableModel(rootNode);
    myComponentsTable.setModel(myTableModel);
    myComponentsTable.setTableHeader(null);
    myComponentsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myComponentDescription.setText(myTableModel.getComponentDescription(myComponentsTable.getSelectedRow()));
      }
    });
    TableColumn column = myComponentsTable.getColumnModel().getColumn(0);
    column.setCellRenderer(new SdkComponentRenderer());
    column.setCellEditor(new SdkComponentRenderer());
    setComponent(myContents);
  }

  @Nullable
  private static File getExistingParentFile(@Nullable String path) {
    if (StringUtil.isEmpty(path)) {
      return null;
    }
    File file = new File(path).getAbsoluteFile();
    while (file != null && !file.exists()) {
      file = file.getParentFile();
    }
    return file;
  }

  private static String getDiskSpace(@Nullable String path) {
    File file = getTargetFilesystem(path);
    if (file == null) {
      return "";
    }
    String available = WelcomeUIUtils.getSizeLabel(file.getFreeSpace());
    if (SystemInfo.isWindows) {
      while (file.getParentFile() != null) {
        file = file.getParentFile();
      }
      return String.format("Disk space available on drive %s: %s", file.getName(), available);
    }
    else {
      return String.format("Available disk space: %s", available);
    }
  }

  @Nullable
  private static File getTargetFilesystem(@Nullable String path) {
    File file = getExistingParentFile(path);
    if (file == null) {
      File[] files = File.listRoots();
      if (files.length != 0) {
        file = files[0];
      }
    }
    return file;
  }

  @Contract("null->false")
  private static boolean isExistingSdk(@Nullable String path) {
    if (!StringUtil.isEmptyOrSpaces(path)) {
      File file = new File(path);
      return file.isDirectory() && IdeSdks.isValidAndroidSdkPath(file);
    }
    else {
      return false;
    }
  }

  private static boolean isNonEmptyNonSdk(@Nullable String path) {
    if (path == null) {
      return false;
    }
    File file = new File(path);

    if (file.exists() && FileOpUtils.create().listFiles(file).length != 0) {
      return AndroidSdkData.getSdkData(file) == null;
    }
    return false;
  }

  @Override
  public boolean validate() {
    String path = myState.get(mySdkDownloadPathKey);
    if (!StringUtil.isEmpty(path)) {
      myUserEditedPath = true;
    }

    mySdkDirectoryValidationResult =
      WizardUtils.validateLocation(path, FIELD_SDK_LOCATION, false, WritableCheckMode.NOT_WRITABLE_IS_WARNING);

    boolean ok = mySdkDirectoryValidationResult.isOk();
    Status status = mySdkDirectoryValidationResult.getStatus();
    String message = ok ? null : mySdkDirectoryValidationResult.getFormattedMessage();

    if (ok) {
      File filesystem = getTargetFilesystem(path);

      if (!(filesystem == null || filesystem.getFreeSpace() > getComponentsSize())) {
        status = Status.ERROR;
        message = "Target drive does not have enough free space.";
      }
      else if (isNonEmptyNonSdk(path)) {
        status = Status.WARN;
        message = "Target folder is neither empty nor does it point to an existing SDK installation.";
      }
      else if (isExistingSdk(path)) {
        status = Status.WARN;
        message = "An existing Android SDK was detected. The setup wizard will only download missing or outdated SDK components.";
      }
    }

    switch (status) {
      case WARN:
        myErrorMessage.setForeground(Colors.WARNING);
        break;
      case ERROR:
        myErrorMessage.setForeground(Colors.ERROR);
        break;
      default:
        break;
    }

    setErrorHtml(myUserEditedPath ? message : null);
    return !mySdkDirectoryValidationResult.isError();
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    super.deriveValues(modified);
    if (modified.contains(mySdkDownloadPathKey) || myRootNode.componentStateChanged(modified)) {
      String path = myState.get(mySdkDownloadPathKey);
      myAvailableSpace.setText(getDiskSpace(path));
      long selected = getComponentsSize();
      myNeededSpace.setText(String.format("Total disk space required: %s", WelcomeUIUtils.getSizeLabel(selected)));
    }
  }

  private long getComponentsSize() {
    long size = 0;
    for (InstallableComponent component : myRootNode.getChildrenToInstall()) {
      size += component.getInstalledSize();
    }
    return size;
  }

  @Override
  public void init() {
    register(mySdkDownloadPathKey, myPath);
    if (!myRootNode.getImmediateChildren().isEmpty()) {
      myComponentsTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    return myErrorMessage;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myComponentsTable;
  }

  @Override
  public boolean isStepVisible() {
    if (myWasVisible) {
      // If we showed it once (e.g. if we had a invalid path on the standard setup path) we want to be sure it shows again (e.g. if we
      // fix the path and then go backward and forward). Otherwise the experience is confusing.
      return true;
    }
    else if (myMode.hasValidSdkLocation()) {
      return false;
    }

    if (myState.getNotNull(myKeyCustomInstall, true)) {
      myWasVisible = true;
      return true;
    }

    validate();

    myWasVisible = !mySdkDirectoryValidationResult.isOk();
    return myWasVisible;
  }

  private void createUIComponents() {
    Splitter splitter = new Splitter(false, 0.5f, 0.2f, 0.8f);
    myBody = splitter;
    myComponentsTable = new JBTable();
    myComponentDescription = new JTextPane();
    splitter.setShowDividerIcon(false);
    splitter.setShowDividerControls(false);
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myComponentsTable, false));
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myComponentDescription, false));

    myComponentDescription.setFont(UIUtil.getLabelFont());
    myComponentDescription.setEditable(false);
    myComponentDescription.setBorder(BorderFactory.createEmptyBorder(WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                                                     WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                                                     WizardConstants.STUDIO_WIZARD_INSET_SIZE,
                                                                     WizardConstants.STUDIO_WIZARD_INSET_SIZE));
  }

  private final class SdkComponentRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private final JPanel myPanel;
    private final JCheckBox myCheckBox;
    private Border myEmptyBorder;

    public SdkComponentRenderer() {
      myPanel = new JPanel(new GridLayoutManager(1, 1));
      myCheckBox = new JCheckBox();
      myCheckBox.setOpaque(false);
      myCheckBox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          stopCellEditing();
        }
      });
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      setupControl(table, value, row, isSelected, hasFocus);
      return myPanel;
    }

    private void setupControl(JTable table, Object value, int row, boolean isSelected, boolean hasFocus) {
      myPanel.setBorder(getCellBorder(table, isSelected && hasFocus));
      Color foreground;
      Color background;
      if (isSelected) {
        background = table.getSelectionBackground();
        foreground = table.getSelectionForeground();
      }
      else {
        background = table.getBackground();
        foreground = table.getForeground();
      }
      myPanel.setBackground(background);
      myCheckBox.setForeground(foreground);
      myPanel.remove(myCheckBox);
      //noinspection unchecked
      Pair<ComponentTreeNode, Integer> pair = (Pair<ComponentTreeNode, Integer>)value;
      int indent = 0;
      if (pair != null) {
        ComponentTreeNode node = pair.getFirst();
        myCheckBox.setEnabled(node.isOptional());
        myCheckBox.setText(node.getLabel());
        myCheckBox.setSelected(node.isChecked());
        indent = pair.getSecond();
      }
      myPanel.add(myCheckBox,
                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, indent * 2));
      AccessibleContextUtil.setName(myPanel, myCheckBox);
    }

    private Border getCellBorder(JTable table, boolean isSelectedFocus) {
      Border focusedBorder = UIUtil.getTableFocusCellHighlightBorder();
      Border border;
      if (isSelectedFocus) {
        border = focusedBorder;
      }
      else {
        if (myEmptyBorder == null) {
          myEmptyBorder = new EmptyBorder(focusedBorder.getBorderInsets(table));
        }
        border = myEmptyBorder;
      }
      return border;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      setupControl(table, value, row, true, true);
      return myPanel;
    }

    @Override
    public Object getCellEditorValue() {
      return myCheckBox.isSelected();
    }
  }

  private static final class ComponentsTableModel extends AbstractTableModel {
    private final List<Pair<ComponentTreeNode, Integer>> myComponents;

    public ComponentsTableModel(final ComponentTreeNode component) {
      ImmutableList.Builder<Pair<ComponentTreeNode, Integer>> components = ImmutableList.builder();
      // Note that root component is not present in the table model so the tree appears to have multiple roots
      traverse(component.getImmediateChildren(), 0, components);
      myComponents = components.build();
    }

    private static void traverse(Collection<ComponentTreeNode> children,
                                 int indent,
                                 ImmutableList.Builder<Pair<ComponentTreeNode, Integer>> components) {
      for (ComponentTreeNode child : children) {
        components.add(Pair.create(child, indent));
        traverse(child.getImmediateChildren(), indent + 1, components);
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 0 && getInstallableComponent(rowIndex).isOptional();
    }

    @Override
    public int getRowCount() {
      return myComponents.size();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return myComponents.get(rowIndex);
    }

    @NotNull
    private ComponentTreeNode getInstallableComponent(int rowIndex) {
      return myComponents.get(rowIndex).getFirst();
    }

    @Override
    public void setValueAt(Object aValue, int row, int column) {
      ComponentTreeNode node = getInstallableComponent(row);
      node.toggle(((Boolean)aValue));
    }

    public String getComponentDescription(int index) {
      return getInstallableComponent(index).getDescription();
    }
  }
}
