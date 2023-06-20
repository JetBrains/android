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

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TableUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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

  private JPanel myDependencyUndoPanel;
  private JLabel myDependencyUndoLabel;
  private LinkLabel myDependencyUndoLink;

  public DynamicFeaturesParameters() {
    // Additional text should show as "gray"
    myAdditionalTextLabel.setForeground(NamedColorUtil.getInactiveTextColor());

    // Setup table: custom mode, ensure table header/grid/separators are not displayed
    myTable.setModel(myTableModel);
    myTable.setTableHeader(null);
    myTableScrollPane.setColumnHeaderView(null);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);

    // Setup column rendering:
    //  - First column is a check box
    //  - Second column is a label (i.e. the feature name)
    //  - Third column is a label containing feature dependency names, if any
    TableColumnModel columnModel = myTable.getColumnModel();

    TableColumn checkMarkColumn = columnModel.getColumn(DynamicFeaturesTableModel.CHECK_MARK_COLUMN_INDEX);
    checkMarkColumn.setCellRenderer(new CheckBoxCellRenderer(myTable.getDefaultRenderer(Boolean.class)));
    TableUtil.setupCheckboxColumn(myTable, DynamicFeaturesTableModel.CHECK_MARK_COLUMN_INDEX);

    columnModel.getColumn(DynamicFeaturesTableModel.FEATURE_NAME_COLUMN_INDEX).setCellRenderer(new FeatureNameCellRenderer());

    columnModel.getColumn(DynamicFeaturesTableModel.DEPENDENCY_LABEL_COLUMN_INDEX).setCellRenderer(new DependencyLabelCellRenderer());

    // Set up the undoable notice that appears when enabling/disabling a feature causes secondary features' status to change
    myDependencyUndoLabel.setIcon(AllIcons.General.BalloonInformation);
    myDependencyUndoLink.setIcon(null); // Hide the default >> icon that shows up

    // Hide the undo panel now (rather than in the .form) so its size will be used in computing the size of the run dialog. Otherwise
    // making this panel visible will shove other form elements down and cause a scrollbar to appear.
    myDependencyUndoPanel.setVisible(false);

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
  JPanel getUndoPanel() {
    return myDependencyUndoPanel;
  }

  @TestOnly
  JLabel getUndoLabel() {
    return myDependencyUndoLabel;
  }

  @TestOnly
  LinkLabel getUndoLink() {
    return myDependencyUndoLink;
  }

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
    java.util.List<Module> features = getModuleSystem(module).getDynamicFeatureModules();
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

  @NotNull
  private DynamicFeatureRow createRow(@NotNull Module module, AvailableDeployTypes deployType) {
    if (deployType == AvailableDeployTypes.INSTANT_AND_INSTALLED) {
      AndroidModel model = AndroidModel.get(module);
      if (model != null && model.isInstantAppCompatible()) {
        return new DynamicFeatureRow(module, isFeatureEnabled(module.getName()));
      } else {
        return new DynamicFeatureRow(module, isFeatureEnabled(module.getName()), true, FeatureType.NON_INSTANT_DYNAMIC_FEATURE);
      }
    } else {
      return new DynamicFeatureRow(module, isFeatureEnabled(module.getName()));
    }
  }

  public void addBaseModule(@NotNull Module module) {
    if (StudioFlags.UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS.get()) {
      AndroidModel model = AndroidModel.get(module);
      if (model == null) {
        return;
      }
      Module baseFeature = DynamicAppUtils.getBaseFeature(module);
      if (baseFeature == null && model.isBaseSplit()) {
        baseFeature = module;
      }
      else {
        return;
      }
      myTableModel.addRow(new DynamicFeatureRow(baseFeature, true, false, FeatureType.BASE_FEATURE));
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
    List<String> featurenames = ContainerUtil.map(features, x -> x.getName());
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
    @NotNull private final Module module;
    public boolean isChecked;
    public boolean isEnabled;
    public FeatureType featureType;

    @NotNull private final String featureName;

    // List of dynamic feature names that depend directly on this module
    @NotNull private final ImmutableSet<String> dependentFeatureNames;

    // List of dynamic feature names that this module directly depends on
    @NotNull private final ImmutableSet<String> dependencyFeatureNames;

    public DynamicFeatureRow(@NotNull Module module, boolean isChecked, boolean isEnabled, FeatureType featureType) {
      this.module = module;
      this.isChecked = isChecked;
      this.isEnabled = isEnabled;
      this.featureType = featureType;

      this.featureName = module.getName();

      if (featureType == FeatureType.BASE_FEATURE) {
        dependentFeatureNames = ImmutableSet.of(); // Base feature dependencies are handled differently
        dependencyFeatureNames = ImmutableSet.of();
      } else {
        dependentFeatureNames = DynamicAppUtils.getFeatureModulesDependingOnFeature(module).stream().map(Module::getName).collect(
          ImmutableSet.toImmutableSet());
        dependencyFeatureNames = DynamicAppUtils.getFeatureModuleDependenciesForFeature(module).stream().map(Module::getName).collect(
          ImmutableSet.toImmutableSet());
      }
    }

    public DynamicFeatureRow(@NotNull Module module, boolean isChecked) {
      this(module, isChecked, true, FeatureType.DYNAMIC_FEATURE);
    }

    public void setEnabled(boolean enabled) {
      isEnabled = enabled;
    }

    public void setChecked(boolean checked) {
      isChecked = checked;
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

    @NotNull
    public Module getModule() {
      return module;
    }

    @NotNull
    public String getFeatureName() {
      return featureName;
    }

    @Nullable
    public String getDependentFeatureLabel() {
      if (dependentFeatureNames.isEmpty()) {
        return null;
      }

      return "Required by " + String.join(", ", dependentFeatureNames);
    }

    public boolean isBaseFeature() {
      return this.featureType == FeatureType.BASE_FEATURE;
    }
  }

  private class DynamicFeaturesTableModel extends AbstractTableModel {
    public static final int COLUMN_COUNT = 3;

    public static final int CHECK_MARK_COLUMN_INDEX = 0;
    public static final int FEATURE_NAME_COLUMN_INDEX = 1;
    public static final int DEPENDENCY_LABEL_COLUMN_INDEX = 2;

    private List<DynamicFeatureRow> myFeatures = new ArrayList<>();
    private Map<String, DynamicFeatureRow> rowsByName = new HashMap<String, DynamicFeatureRow>();

    private UndoHandler myUndoHandler;

    public DynamicFeaturesTableModel() {
      myUndoHandler = new UndoHandler();
      myDependencyUndoLink.setListener(myUndoHandler, null);
    }

    public void clear() {
      myFeatures.clear();
    }

    public void addRow(@NotNull DynamicFeatureRow row) {
      myFeatures.add(row);
      rowsByName.put(row.getFeatureName(), row);
    }

    @Override
    public int getRowCount() {
      return myFeatures.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMN_COUNT;
    }

    @Override
    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUMN_INDEX) {
        return myFeatures.get(rowIndex).isChecked;
      } else if (columnIndex == FEATURE_NAME_COLUMN_INDEX) {
        return myFeatures.get(rowIndex).getFeatureName();
      } else if (columnIndex == DEPENDENCY_LABEL_COLUMN_INDEX) {
        return myFeatures.get(rowIndex).getDependentFeatureLabel();
      }

      return null;
    }

    @Override
    public void setValueAt(@Nullable Object aValue, int rowIndex, int columnIndex) {
      DynamicFeatureRow row = myFeatures.get(rowIndex);
      if (columnIndex == CHECK_MARK_COLUMN_INDEX) {
        boolean shouldEnable = aValue == null || ((Boolean)aValue).booleanValue();
        myUndoHandler.clearUndo();

        if (shouldEnable) {
          Set<DynamicFeatureRow> newRowsChecked = includeDynamicFeature(row);
          if (newRowsChecked.size() > 1) { // Dependencies were checked automatically
            myUndoHandler.saveRowsSelected(row.featureName, newRowsChecked);
          }
        } else {
          Set<DynamicFeatureRow> newRowsUnchecked = excludeDynamicFeature(row);
          if (newRowsUnchecked.size() > 1) { // Dependent modules were unchecked automatically
            myUndoHandler.saveRowsDeselected(row.featureName, newRowsUnchecked);
          }
        }
        // Refresh the whole table since multiple dependencies may have been updated
        fireAllRowsUpdated();
      } else {
        fireTableRowsUpdated(rowIndex, rowIndex);
      }
    }

    private void fireAllRowsUpdated() {
      fireTableRowsUpdated(0, getRowCount()- 1);
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

    /** Enables a dynamic feature and all of its dependencies. Returns the set of feature rows whose inclusion status changed. */
    private Set<DynamicFeatureRow> includeDynamicFeature(DynamicFeatureRow row) {
      Set<DynamicFeatureRow> resultSet = new HashSet<>();
      if (!row.isChecked) {
        resultSet.add(row);
      }

      row.setChecked(true);
      myDisabledDynamicFeatures.remove(row.featureName);

      for (String depName : row.dependencyFeatureNames) {
        // Recursively include first-level deps and any of their dependencies
        resultSet.addAll(includeDynamicFeature(rowsByName.get(depName)));
      }

      return resultSet;
    }

    /** Disables a dynamic feature and all of its dependents. Returns the set of feature rows whose inclusion status changed. */
    private Set<DynamicFeatureRow> excludeDynamicFeature(DynamicFeatureRow row) {
      Set<DynamicFeatureRow> resultSet = new HashSet<>();
      if (row.isChecked) {
        resultSet.add(row);
      }

      row.setChecked(false);
      myDisabledDynamicFeatures.add(row.featureName);

      for (String childName : row.dependentFeatureNames) {
        // Recursively exclude first-level dependencies on this module, and any dependencies on those
        resultSet.addAll(excludeDynamicFeature(rowsByName.get(childName)));
      }

      return resultSet;
    }


    private class UndoHandler implements LinkListener {
      // Collection of rows whose check box should be inverted during an undo
      private Set<DynamicFeatureRow> undoRows;

      // Removes the undoable action and hides the undo UI
      void clearUndo() {
        undoRows = null;
        myDependencyUndoPanel.setVisible(false);
      }

      /**
       * Records an action where a set of feature rows were enabled so that it can be undone later
       *
       * @param clickedFeatureName  the name of the feature clicked by the user to precipitate the change
       * @param selectedRows        the set of rows enabled as a result of the change
       */
      void saveRowsSelected(String clickedFeatureName, Set<DynamicFeatureRow> selectedRows) {
        int additionalAffectedCount = selectedRows.size() - 1;

        if (additionalAffectedCount == 1) {
          myDependencyUndoLabel
            .setText(String.format(Locale.US, "1 module required by %s has been selected", clickedFeatureName));
        } else {
          myDependencyUndoLabel
            .setText(String.format(Locale.US, "%d modules required by %s have been selected", additionalAffectedCount, clickedFeatureName));
        }

        undoRows = selectedRows;
        myDependencyUndoPanel.setVisible(true);
      }

      /**
       * Records an action where a set of feature rows were disabled so that it can be undone later
       *
       * @param clickedFeatureName  the name of the feature clicked by the user to precipitate the change
       * @param selectedRows        the set of rows disabled as a result of the change
       */
      void saveRowsDeselected(String clickedFeatureName, Set<DynamicFeatureRow> deselectedRows) {
        int additionalAffectedCount = deselectedRows.size() - 1;

        if (additionalAffectedCount == 1) {
          myDependencyUndoLabel
            .setText(String.format(Locale.US, "1 module requiring %s has been deselected", clickedFeatureName));
        } else {
          myDependencyUndoLabel
            .setText(String.format(Locale.US, "%d modules requiring %s have been deselected", additionalAffectedCount, clickedFeatureName));
        }

        undoRows = deselectedRows;
        myDependencyUndoPanel.setVisible(true);
      }


      @Override
      public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
        if (undoRows != null) { // This shouldn't be null but we'll handle it anyway
          for (DynamicFeatureRow row : undoRows) {
            if (row.isChecked) {
              row.isChecked = false;
              myDisabledDynamicFeatures.add(row.featureName);
            } else {
              row.isChecked = true;
              myDisabledDynamicFeatures.remove(row.featureName);
            }

            fireAllRowsUpdated();
          }

          clearUndo();
        }
      }
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

  @VisibleForTesting
  class FeatureNameCellRenderer extends StripedRowCellRenderer {
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
      this.setText(featureRow.getDisplayName());
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

  private class DependencyLabelCellRenderer extends StripedRowCellRenderer {
    @Override
    @NotNull
    public Component getTableCellRendererComponent(@NotNull JTable table, @Nullable Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      // Disable undesirable focus border if the row is selected
      Color color = UIUtil.getTableFocusCellBackground();
      UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, table.getSelectionBackground());
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JLabel) {
        ((JLabel)component).setBorder(noFocusBorder);
      }
      UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, color);

      // Populate the list of dependent feature names, if any
      DynamicFeatureRow featureRow = myTableModel.myFeatures.get(row);
      String dependentFeatureLabel = featureRow.getDependentFeatureLabel();
      if (dependentFeatureLabel != null) {
        this.setText(dependentFeatureLabel);
      }

      // Set text to gray to de-emphasize the list of dependent features
      if (!isSelected) { // Only do this for non-selected rows, gray on blue is hard to read
        this.setForeground(NamedColorUtil.getInactiveTextColor());
      }

      return component;
    }
  }
}
