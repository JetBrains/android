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
package com.android.tools.idea.welcome;

import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.WizardUtils;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.Set;

/**
 * Wizard page for selecting SDK components to download.
 */
public class SdkComponentsStep extends FirstRunWizardStep {
  public static final String FIELD_SDK_LOCATION = "SDK location";

  private final InstallableComponent[] myInstallableComponents;
  private final ScopedStateStore.Key<Boolean> myKeyInstallSdk;
  private JPanel myContents;
  private JBTable myComponentsTable;
  private JTextPane myComponentDescription;
  private JLabel myNeededSpace;
  private JLabel myAvailableSpace;
  private JLabel myErrorMessage;
  private JSplitPane mySplitPane;
  private ScopedStateStore.Key<String> mySdkDownloadPathKey;
  private TextFieldWithBrowseButton myPath;
  private boolean myUserEditedPath = false;

  public SdkComponentsStep(@NotNull InstallableComponent[] components,
                           @NotNull ScopedStateStore.Key<Boolean> keyInstallSdk,
                           @NotNull ScopedStateStore.Key<String> sdkDownloadPathKey) {
    super("SDK Settings");

    myPath.addBrowseFolderListener("Android SDK", "Select Android SDK install directory", null,
                                   FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myKeyInstallSdk = keyInstallSdk;
    mySdkDownloadPathKey = sdkDownloadPathKey;
    myComponentDescription.setEditable(false);
    myComponentDescription.setContentType("text/html");
    myComponentDescription.setBorder(new EmptyBorder(WizardConstants.STUDIO_WIZARD_INSETS));
    mySplitPane.setBorder(null);
    Font labelFont = UIUtil.getLabelFont();
    Font smallLabelFont = labelFont.deriveFont(labelFont.getSize() - 1.0f);
    myNeededSpace.setFont(smallLabelFont);
    myAvailableSpace.setFont(smallLabelFont);
    myErrorMessage.setText(null);
    myErrorMessage.setForeground(JBColor.red);

    myInstallableComponents = components;
    DefaultTableModel model = new DefaultTableModel(0, 1) {
      @Override
      public void setValueAt(Object aValue, int row, int column) {
        boolean isSelected = ((Boolean)aValue);
        InstallableComponent installableComponent = myInstallableComponents[row];
        if (isSelected) {
          select(installableComponent);
        }
        else {
          deselect(installableComponent);
        }
        fireTableRowsUpdated(row, row);
      }
    };
    for (InstallableComponent installableComponent : myInstallableComponents) {
      model.addRow(new Object[]{installableComponent});
    }
    myComponentsTable.setModel(model);
    myComponentsTable.setTableHeader(null);
    myComponentsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int selected = myComponentsTable.getSelectedRow();
        String description = selected >= 0 ? myInstallableComponents[selected].getDescription() : null;
        myComponentDescription.setText(description);
      }
    });
    TableColumn column = myComponentsTable.getColumnModel().getColumn(0);
    column.setCellRenderer(new SdkComponentRenderer());
    column.setCellEditor(new SdkComponentRenderer());
    setComponent(myContents);
  }

  private static boolean isChild(@Nullable InstallableComponent child, @NotNull InstallableComponent installableComponent) {
    return child != null && (child == installableComponent || isChild(child.getParent(), installableComponent));
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

  @Override
  public boolean validate() {
    String path = myState.get(mySdkDownloadPathKey);
    if (!StringUtil.isEmpty(path)) {
      myUserEditedPath = true;
    }
    WizardUtils.ValidationResult error = WizardUtils.validateLocation(path, FIELD_SDK_LOCATION, false);
    String message = error.isOk() ? null : error.getFormattedMessage();
    boolean isOk = !error.isError();
    if (isOk) {
      File filesystem = getTargetFilesystem(path);
      if (!(filesystem == null || filesystem.getFreeSpace() > getComponentsSize())) {
        isOk = false;
        message = "Target drive does not have enough free space";
      }
      else if (isNonEmptyNonSdk(path)) {
        isOk = true;
        message = "Target folder is neither empty nor does it point to an existing SDK installation.";
      }
    }
    setErrorHtml(myUserEditedPath ? message : null);
    return isOk;
  }

  private static boolean isNonEmptyNonSdk(@Nullable String path) {
    if (path == null) {
      return false;
    }
    File file = new File(path);
    if (file.exists() && TemplateUtils.listFiles(file).length > 0) {
      return AndroidSdkData.getSdkData(file) == null;
    }
    return false;
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    for (int i = 0; i < myInstallableComponents.length; i++) {
      ScopedStateStore.Key<Boolean> key = myInstallableComponents[i].getKey();
      if (modified.contains(key)) {
        myComponentsTable.getModel().setValueAt(myState.getNotNull(key, true), i, 0);
      }
    }
    myAvailableSpace.setText(getDiskSpace(myState.get(mySdkDownloadPathKey)));
    long selected = getComponentsSize();
    myNeededSpace.setText(String.format("Total disk space required: %s", WelcomeUIUtils.getSizeLabel(selected)));
    super.deriveValues(modified);
  }

  private long getComponentsSize() {
    long selected = 0;
    for (InstallableComponent installableComponent : myInstallableComponents) {
      if (isSelected(installableComponent)) {
        selected += installableComponent.getSize();
      }
    }
    return selected;
  }

  private void deselect(InstallableComponent installableComponent) {
    for (InstallableComponent child : myInstallableComponents) {
      if (child.getSize() > 0 && isChild(child, installableComponent)) {
        myState.put(child.getKey(), false);
      }
    }
  }

  private Iterable<InstallableComponent> getChildren(final InstallableComponent installableComponent) {
    return Iterables.filter(Arrays.asList(myInstallableComponents), new Predicate<InstallableComponent>() {
      @Override
      public boolean apply(@Nullable InstallableComponent input) {
        assert input != null;
        InstallableComponent n = input;
        do {
          if (n == installableComponent) {
            return true;
          }
          n = n.getParent();
        }
        while (n != null);
        return false;
      }
    });
  }

  private void select(InstallableComponent installableComponent) {
    for (InstallableComponent child : getChildren(installableComponent)) {
      myState.put(child.getKey(), true);
    }
  }

  @Override
  public void init() {
    register(mySdkDownloadPathKey, myPath);
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

  private boolean isSelected(InstallableComponent installableComponent) {
    for (InstallableComponent child : getChildren(installableComponent)) {
      if (!myState.getNotNull(child.getKey(), true)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isStepVisible() {
    InstallerData data = InstallerData.get();
    boolean hasSdk = data != null && data.hasValidSdkLocation();
    Boolean shouldInstallSdk = myState.getNotNull(myKeyInstallSdk, true);
    return !hasSdk && shouldInstallSdk;
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
      setupControl(table, value, isSelected, hasFocus);
      return myPanel;
    }

    private void setupControl(JTable table, Object value, boolean isSelected, boolean hasFocus) {
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
      InstallableComponent installableComponent = (InstallableComponent)value;
      int indent = 0;
      if (installableComponent != null) {
        myCheckBox.setEnabled(installableComponent.isOptional());
        myCheckBox.setText(installableComponent.getLabel());
        myCheckBox.setSelected(isSelected((InstallableComponent)value));
        //noinspection ConstantConditions
        while (installableComponent.getParent() != null) {
          indent++;
          installableComponent = installableComponent.getParent();
        }
      }
      myPanel.add(myCheckBox,
                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, indent * 2));
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
      setupControl(table, value, true, true);
      return myPanel;
    }

    @Override
    public Object getCellEditorValue() {
      return myCheckBox.isSelected();
    }
  }
}
