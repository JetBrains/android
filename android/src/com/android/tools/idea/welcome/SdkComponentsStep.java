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

import com.android.sdklib.devices.Storage.Unit;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.android.tools.idea.wizard.WizardConstants;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;

/**
 * Wizard page for selecting SDK components to download.
 */
public class SdkComponentsStep extends FirstRunWizardStep {
  private static final ScopedStateStore.Key<BitSet> KEY_SELECTED_COMPONENTS =
    ScopedStateStore.createKey("selected.components", ScopedStateStore.Scope.STEP, BitSet.class);

  private final SdkComponent[] mySdkComponents;
  private final ScopedStateStore.Key<Boolean> myKeyShouldDownload;
  private JPanel myContents;
  private JBTable myComponentsTable;
  private JTextPane myComponentDescription;
  private JLabel myNeededSpace;
  private JLabel myAvailableSpace;
  private JLabel myErrorMessage;
  private JSplitPane mySplitPane;
  private Set<SdkComponent> myUncheckedComponents = Sets.newHashSet();
  private ScopedStateStore.Key<String> mySdkDownloadPathKey;
  private TextFieldWithBrowseButton myPath;
  private boolean myUserEditedPath = false;

  public SdkComponentsStep(ScopedStateStore.Key<Boolean> keyShouldDownload, ScopedStateStore.Key<String> sdkDownloadPathKey) {
    super("SDK Settings");

    myPath.addBrowseFolderListener("Android SDK", "Select Android SDK install directory", null,
                                   FileChooserDescriptorFactory.createSingleFolderDescriptor());

    myKeyShouldDownload = keyShouldDownload;
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

    mySdkComponents = createModel();
    DefaultTableModel model = new DefaultTableModel(0, 1) {
      @Override
      public void setValueAt(Object aValue, int row, int column) {
        boolean isSelected = ((Boolean)aValue);
        SdkComponent sdkComponent = mySdkComponents[row];
        if (isSelected) {
          select(sdkComponent);
        }
        else {
          deselect(sdkComponent);
        }
        fireTableRowsUpdated(row, row);
      }
    };
    for (SdkComponent sdkComponent : mySdkComponents) {
      model.addRow(new Object[]{sdkComponent});
    }
    myComponentsTable.setModel(model);
    myComponentsTable.setTableHeader(null);
    myComponentsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int selected = myComponentsTable.getSelectedRow();
        String description = selected >= 0 ? mySdkComponents[selected].myDescription : null;
        myComponentDescription.setText(description);
      }
    });
    TableColumn column = myComponentsTable.getColumnModel().getColumn(0);
    column.setCellRenderer(new SdkComponentRenderer());
    column.setCellEditor(new SdkComponentRenderer());
    setComponent(myContents);
  }

  private static SdkComponent[] createModel() {
    long mb = Unit.MiB.getNumberOfBytes();
    SdkComponent androidSdk = new SdkComponent("Android Studio + SDK", 684 * mb, null, false);
    SdkComponent sdkPlatform = new SdkComponent("Android SDK Platform", 0, null, true);
    SdkComponent lmp = new SdkComponent("LMP - Android 5.0 (API 21)", 292 * mb, sdkPlatform, true);
    SdkComponent root = new SdkComponent("Android Emulator", 0, null, true);
    SdkComponent nexus = new SdkComponent("Nexus", 0, root, true);
    SdkComponent nexus5 = new SdkComponent("Nexus 5", 2499 * mb, nexus, true);
    SdkComponent performance = new SdkComponent("Performance", 0, root, true);
    SdkComponent haxm = new SdkComponent("Intel® HAXM", 2306867, performance, true);

    return new SdkComponent[]{androidSdk, sdkPlatform, lmp, root, nexus, nexus5, performance, haxm};
  }

  private static boolean isChild(@Nullable SdkComponent child, @NotNull SdkComponent sdkComponent) {
    return child != null && (child == sdkComponent || isChild(child.myParent, sdkComponent));
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
    File file = getExistingParentFile(path);
    if (file == null) {
      File[] files = File.listRoots();
      if (files.length != 0) {
        file = files[0];
      }
    }
    if (file == null) {
      return "";
    }
    String available = getSizeLabel(file.getFreeSpace());
    if (SystemInfo.isWindows) {
      while (file.getParent() != null) {
        file = file.getParentFile();
      }
      return String.format("Disk space available on rive %s: %s", file.getName(), available);
    }
    else {
      return String.format("Available disk space: %s", available);
    }
  }

  private static String getSizeLabel(long freeSpace) {
    Unit[] values = Unit.values();
    Unit unit = values[values.length - 1];
    for (int i = values.length - 2; unit.getNumberOfBytes() > freeSpace && i >= 0; i--) {
      unit = values[i];
    }
    final double space = freeSpace * 1.0 / unit.getNumberOfBytes();
    String formatted = roundToNumberOfDigits(space, 3);
    return String.format("%s %s", formatted, unit.toString());
  }

  /**
   * <p>Returns a string that rounds the number so number of
   * integer places + decimal places is less or equal to maxDigits.</p>
   * <p>Number will not be truncated if it has more integer digits
   * then macDigits</p>
   */
  private static String roundToNumberOfDigits(double number, int maxDigits) {
    int multiplier = 1, digits;
    for (digits = maxDigits; digits > 0 && number > multiplier; digits--) {
      multiplier *= 10;
    }
    NumberFormat numberInstance = NumberFormat.getNumberInstance();
    numberInstance.setGroupingUsed(false);
    numberInstance.setRoundingMode(RoundingMode.HALF_UP);
    numberInstance.setMaximumFractionDigits(digits);
    return numberInstance.format(number);
  }

  @NotNull
  private static String inventDescription(String name, long size) {
    return String.format("<html><p>This is a description for <em>%s</em> component</p>" +
                         "<p>We know is that it takes <strong>%s</strong> disk space</p></html>", name, getSizeLabel(size));
  }

  @Override
  public boolean validate() {
    String error = validatePath(myState.get(mySdkDownloadPathKey));
    setErrorHtml(myUserEditedPath ? error : null);
    return error == null;
  }

  @Nullable
  private String validatePath(@Nullable String path) {
    if (StringUtil.isEmpty(path)) {
      return "Path is empty";
    }
    else {
      myUserEditedPath = true;
      File file = new File(path);
      while (file != null && !file.exists()) {
        if (!PathUtil.isValidFileName(file.getName())) {
          return "Specified path is not valid";
        }
        file = file.getParentFile();
      }
    }
    return null;
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    myAvailableSpace.setText(getDiskSpace(myState.get(mySdkDownloadPathKey)));
    BitSet bitSet = myState.get(KEY_SELECTED_COMPONENTS);
    long selected = 0;
    for (int i = 0; i < mySdkComponents.length; i++) {
      if (bitSet == null || bitSet.get(i)) {
        SdkComponent sdkComponent = mySdkComponents[i];
        selected += sdkComponent.mySize;
      }
    }
    myNeededSpace.setText(String.format("Total disk space required: %s", getSizeLabel(selected)));
    super.deriveValues(modified);
  }

  private void deselect(SdkComponent sdkComponent) {
    for (SdkComponent child : mySdkComponents) {
      if (child.mySize > 0 && isChild(child, sdkComponent)) {
        myUncheckedComponents.add(child);
      }
    }
  }

  private Iterable<SdkComponent> getChildren(final SdkComponent sdkComponent) {
    return Iterables.filter(Arrays.asList(mySdkComponents), new Predicate<SdkComponent>() {
      @Override
      public boolean apply(@Nullable SdkComponent input) {
        assert input != null;
        SdkComponent n = input;
        do {
          if (n == sdkComponent) {
            return true;
          }
          n = n.myParent;
        }
        while (n != null);
        return false;
      }
    });
  }

  private void select(SdkComponent sdkComponent) {
    for (SdkComponent child : getChildren(sdkComponent)) {
      myUncheckedComponents.remove(child);
    }
  }

  @Override
  public void init() {
    register(mySdkDownloadPathKey, myPath);
    register(KEY_SELECTED_COMPONENTS, myComponentsTable, new ComponentBinding<BitSet, JBTable>() {
      @Override
      public void setValue(@Nullable BitSet newValue, @NotNull JBTable component) {
        for (int i = 0; i < mySdkComponents.length; i++) {
          component.getModel().setValueAt(newValue == null || newValue.get(i), i, 0);
        }
      }

      @Nullable
      @Override
      public BitSet getValue(@NotNull JBTable component) {
        BitSet bitSet = new BitSet(mySdkComponents.length);
        int i = 0;
        for (SdkComponent sdkComponent : mySdkComponents) {
          bitSet.set(i++, sdkComponent.mySize > 0 && !myUncheckedComponents.contains(sdkComponent));
        }
        return bitSet;
      }

      @Override
      public void addActionListener(@NotNull final ActionListener listener, @NotNull final JBTable component) {
        component.getModel().addTableModelListener(new TableModelListener() {
          @Override
          public void tableChanged(TableModelEvent e) {
            ActionEvent event = new ActionEvent(component, ActionEvent.ACTION_FIRST + 1, "toggle");
            listener.actionPerformed(event);
          }
        });
      }
    });
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

  private boolean isSelected(SdkComponent sdkComponent) {
    for (SdkComponent child : getChildren(sdkComponent)) {
      if (myUncheckedComponents.contains(child)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isStepVisible() {
    return Objects.equal(Boolean.TRUE, myState.get(myKeyShouldDownload));
  }

  private static final class SdkComponent {
    @NotNull private final String myName;
    private final long mySize;
    @Nullable private final SdkComponent myParent;
    private final boolean myCanDeselect;
    private final String myDescription;

    public SdkComponent(@NotNull String name, long size, @Nullable SdkComponent parent, boolean canDeselect) {
      this(name, size, parent, canDeselect, inventDescription(name, size));
    }

    public SdkComponent(@NotNull String name, long size, @Nullable SdkComponent parent, boolean canDeselect, @NotNull String description) {
      myName = name;
      mySize = size;
      myParent = parent;
      myCanDeselect = canDeselect;
      myDescription = description;
    }

    @Override
    public String toString() {
      return myName;
    }

    public String getLabel() {
      return mySize == 0 ? myName : String.format("%s – (%s)", myName, getSizeLabel(mySize));
    }
  }

  private final class SdkComponentRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private final JPanel myPanel;
    private final JCheckBox myCheckBox;
    private Border myEmptyBorder;

    public SdkComponentRenderer() {
      myPanel = new JPanel(new GridLayoutManager(1, 1));
      myCheckBox = new JCheckBox();
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
      if (isSelected) {
        myPanel.setBackground(table.getSelectionBackground());
        foreground = table.getSelectionForeground();
      }
      else {
        myPanel.setBackground(table.getBackground());
        foreground = table.getForeground();
      }
      myCheckBox.setForeground(foreground);
      myPanel.remove(myCheckBox);
      SdkComponent sdkComponent = (SdkComponent)value;
      int indent = 0;
      if (sdkComponent != null) {
        myCheckBox.setEnabled(sdkComponent.myCanDeselect);
        myCheckBox.setText(sdkComponent.getLabel());
        myCheckBox.setSelected(isSelected((SdkComponent)value));
        while (sdkComponent.myParent != null) {
          indent++;
          sdkComponent = sdkComponent.myParent;
          assert sdkComponent != null;
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
