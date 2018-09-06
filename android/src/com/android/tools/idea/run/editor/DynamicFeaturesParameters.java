/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TableUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DynamicFeaturesParameters {
  /** Specifies how these features may be deployed in this run configuration */
  public enum AvailableDeployTypes {
    /** Run configuration is deployable in installed state only, don't annotate instant status
     *  or prevent selection of non-instant features when checkbox is selected */
    INSTALLED_ONLY,
    /** Run configuration is deployable as installed or instant, annotate instant status of
     *  features and disable non-instant features when checkbox is selected */
    INSTANT_AND_INSTALLED
  }

  public enum FeatureType {
    BASE_FEATURE,
    NON_INSTANT_DYNAMIC_FEATURE,
    DYNAMIC_FEATURE
  }

  private static final int PREFERRED_HEIGHT_IN_ROWS = 4;

  @NotNull
  private final DynamicFeaturesTableModel myTableModel = new DynamicFeaturesTableModel();
  @NotNull
  private final Set<String> myDisabledDynamicFeatures = new HashSet<>();

  private JPanel myRootPanel;
  private JBScrollPane myTableScrollPane;
  private JBTable myTable;
  private JBLabel myAdditionalTextLabel;

  public DynamicFeaturesParameters() {
    // Additional text should show as "gray"
    myAdditionalTextLabel.setForeground(UIUtil.getInactiveTextColor());

    // Setup table: custom mode, ensure table header/grid/separators are not displayed
    myTable.setModel(myTableModel);
    myTable.setTableHeader(null);
    myTableScrollPane.setColumnHeaderView(null);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);

    // Setup column rendering: First column is a check box, second column is a label (i.e. the feature name)
    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn checkMarkColumn = columnModel.getColumn(DynamicFeaturesTableModel.CHECK_MARK_COLUMN_INDEX);
    checkMarkColumn.setCellRenderer(new CheckBoxCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    TableUtil.setupCheckboxColumn(myTable, DynamicFeaturesTableModel.CHECK_MARK_COLUMN_INDEX);
    columnModel.getColumn(DynamicFeaturesTableModel.FEATURE_NAME_COLUMN_INDEX).setCellRenderer(new FeatureNameCellRenderer());

    // By default, the component should not be visible
    disable();
  }


  /**
   * Returns the root component of this form, to be used into its container
   */
  @NotNull
  public JComponent getComponent() {
    return myRootPanel;
  }

  @TestOnly
  JTable getTableComponent() {
    return myTable;
  }

  @TestOnly
  Object getTableDisplayValueAt(int rowIndex, int columnIndex) { return myTableModel.getDisplayValueAt(rowIndex, columnIndex); }
  
  /**
   * Returns the list of disabled feature names
   */
  @NotNull
  public List<String> getDisabledDynamicFeatures() {
    return ImmutableList.copyOf(myDisabledDynamicFeatures);
  }

  /**
   * Update the list of features after a new {@link Module} is activated
   * @param module
   */
  public void setActiveModule(@Nullable Module module, AvailableDeployTypes deployType) {
    setDisabledDynamicFeatures(new ArrayList<>());
    if (module == null) {
      disable();
      return;
    }

    myTableModel.clear();
    addBaseModule(module);
    java.util.List<Module> features = DynamicAppUtils.getDependentFeatureModules(module);
    if (features.isEmpty()) {
      disable();
      return;
    }

    addFeatureList(features, deployType);
  }

  /**
   * Add a list of {@link Module modules} to the list of features
   */
  public void addFeatureList(@NotNull List<Module> features, AvailableDeployTypes deployType) {
    features.stream()
            .sorted((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true))
            .map(f -> createRow(f, deployType))
            .forEach(row -> myTableModel.addRow(row));
    enable();
  }

  private DynamicFeatureRow createRow(@NotNull Module module, AvailableDeployTypes deployType) {
    if (deployType == AvailableDeployTypes.INSTANT_AND_INSTALLED) {
      if (AndroidModuleModel.get(module).getSelectedVariant().isInstantAppCompatible()) {
        return new DynamicFeatureRow(module.getName(), isFeatureEnabled(module.getName()));
      } else {
        return new DynamicFeatureRow(module.getName(), isFeatureEnabled(module.getName()), true, FeatureType.NON_INSTANT_DYNAMIC_FEATURE);
      }
    } else {
      return new DynamicFeatureRow(module.getName(), isFeatureEnabled(module.getName()));
    }
  }

  public void addBaseModule(@NotNull Module module) {
    if (StudioFlags.UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS.get()) {
      Module baseFeature = DynamicAppUtils.getBaseFeature(module);
      if (baseFeature == null && AndroidModuleModel.get(module).getAndroidProject().isBaseSplit()) {
        baseFeature = module;
      }
      else {
        return;
      }
      myTableModel.addRow(new DynamicFeatureRow(baseFeature.getName(), true, false, FeatureType.BASE_FEATURE));
    }
  }

  /**
   * Set the list of disabled features, update the UI so that checkboxes are enabled/disabled
   * depending on this new list.
   */
  public void setDisabledDynamicFeatures(@NotNull List<String> disabledDynamicFeatures) {
    myDisabledDynamicFeatures.clear();
    myDisabledDynamicFeatures.addAll(disabledDynamicFeatures);

    // Update enabled/disabled state of features in active model
    myTableModel.myFeatures.forEach(x -> x.isChecked = isFeatureEnabled(x.getFeatureName()));
    myTableModel.fireTableDataChanged();
  }

  public void updateBasedOnInstantState(@NotNull Module module, boolean instantAppDeploy) {
    java.util.List<Module> features = DynamicAppUtils.getDependentInstantFeatureModules(module);
    java.util.List<String> featurenames = features.stream().map(x -> x.getName()).collect(Collectors.toList());
    if (instantAppDeploy) {
      myTableModel.myFeatures.forEach(x -> {
        if (!featurenames.contains(x.getFeatureName()) && !x.isBaseFeature()) {
          x.isChecked = false;
          x.setEnabled(false);
          myDisabledDynamicFeatures.add(x.getFeatureName());
        }
      });
    } else {
      myTableModel.myFeatures.forEach(x -> {
        if (!x.isBaseFeature()) {
          x.setEnabled(true);
        }
      });
    }
    myTableModel.fireTableDataChanged();
  }

  private boolean isFeatureEnabled(@NotNull String name) {
    return !myDisabledDynamicFeatures.contains(name);
  }

  private void enable() {
    myTableModel.fireTableDataChanged();
    // Set minimum size now that we have real data
    Insets insets = myTableScrollPane.getInsets();
    int minHeight = insets.top +
                    myTable.getRowHeight() * PREFERRED_HEIGHT_IN_ROWS +
                    insets.bottom;
    myTableScrollPane.setMinimumSize(new Dimension(200, minHeight));
    myRootPanel.setVisible(true);
  }

  private void disable() {
    myTableModel.clear();
    myTableModel.fireTableDataChanged();
    myRootPanel.setVisible(false);
  }

  private static class DynamicFeatureRow {
    @NotNull private final String featureName;
    public boolean isChecked;
    public boolean isEnabled;
    public FeatureType featureType;

    public DynamicFeatureRow(@NotNull String name, boolean isChecked, boolean isEnabled, FeatureType featureType) {
      this.featureName = name;
      this.isChecked = isChecked;
      this.isEnabled = isEnabled;
      this.featureType = featureType;
    }

    public DynamicFeatureRow(@NotNull String name, boolean isChecked) {
      this(name, isChecked, true, FeatureType.DYNAMIC_FEATURE);
    }

    public void setEnabled(boolean enabled) {
      isEnabled = enabled;
    }

    public String getDisplayName() {
      if (featureType == FeatureType.BASE_FEATURE) {
        return this.featureName + " (base)";
      } else if (featureType == FeatureType.NON_INSTANT_DYNAMIC_FEATURE) {
        return this.featureName + " (not instant app enabled)";
      } else {
        return this.featureName;
      }
    }

    public String getFeatureName() {
      return featureName;
    }

    public boolean isBaseFeature() {
      return this.featureType == FeatureType.BASE_FEATURE;
    }
  }

  private class DynamicFeaturesTableModel extends AbstractTableModel {
    public static final int CHECK_MARK_COLUMN_INDEX = 0;
    public static final int FEATURE_NAME_COLUMN_INDEX = 1;

    private List<DynamicFeatureRow> myFeatures = new ArrayList<>();

    public DynamicFeaturesTableModel() {
    }

    public void clear() {
      myFeatures.clear();
    }

    public void addRow(@NotNull DynamicFeatureRow row) {
      myFeatures.add(row);
    }

    @Override
    public int getRowCount() {
      return myFeatures.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUMN_INDEX) {
        return myFeatures.get(rowIndex).isChecked;
      }
      else if (columnIndex == FEATURE_NAME_COLUMN_INDEX) {
        return myFeatures.get(rowIndex).getFeatureName();
      }
      return null;
    }

    public Object getDisplayValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUMN_INDEX) {
        return myFeatures.get(rowIndex).isChecked;
      }
      else if (columnIndex == FEATURE_NAME_COLUMN_INDEX) {
        return myFeatures.get(rowIndex).getDisplayName();
      }
      return null;
    }

    @Override
    public void setValueAt(@Nullable Object aValue, int rowIndex, int columnIndex) {
      DynamicFeatureRow row = myFeatures.get(rowIndex);
      if (columnIndex == CHECK_MARK_COLUMN_INDEX) {
        row.isChecked = aValue == null || ((Boolean)aValue).booleanValue();
        if (row.isChecked) {
          myDisabledDynamicFeatures.remove(row.getFeatureName());
        }
        else {
          myDisabledDynamicFeatures.add(row.getFeatureName());
        }
      }
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    @Override
    @NotNull
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUMN_INDEX) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUMN_INDEX && myFeatures.get(rowIndex).isEnabled) {
        return true;
      }
      return false;
    }
  }

  private static class StripedRowCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (!isSelected) {
        component.setBackground(row % 2 == 0 ? UIUtil.getDecoratedRowColor() : UIUtil.getTableBackground());
      }
      return component;
    }
  }

  private class FeatureNameCellRenderer extends StripedRowCellRenderer {
    @Override
    @NotNull
    public Component getTableCellRendererComponent(@NotNull JTable table, @Nullable Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      Color color = UIUtil.getTableFocusCellBackground();
      UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, table.getSelectionBackground());
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JLabel) {
        ((JLabel)component).setBorder(noFocusBorder);
      }
      UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, color);
      DynamicFeatureRow featureRow = myTableModel.myFeatures.get(row);
      component.setEnabled(isSelected || featureRow.isEnabled);
      return component;
    }
  }


  private class CheckBoxCellRenderer extends StripedRowCellRenderer {
    private final TableCellRenderer myDelegate;

    public CheckBoxCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      DynamicFeatureRow featureRow = myTableModel.myFeatures.get(row);
      component.setEnabled(featureRow.isEnabled);
      return component;
    }
  }
}
