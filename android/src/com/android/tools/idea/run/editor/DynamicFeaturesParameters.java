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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.DynamicAppUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.run.editor.DynamicFeaturesParameters.DynamicFeaturesTableModel.FEATURE_NAME;

public class DynamicFeaturesParameters {
  @NotNull
  private final DynamicFeaturesTableModel myTableModel = new DynamicFeaturesTableModel();
  @NotNull
  private final LabeledComponent<JBTable> myDynamicFeaturesLabeledComponent = new LabeledComponent<>();
  @NotNull
  private final Set<String> myDisabledDynamicFeatures = new HashSet<>();

  public DynamicFeaturesParameters() {
    JBTable table = new JBTable(myTableModel);

    myDynamicFeaturesLabeledComponent.setLabelLocation(BorderLayout.WEST);
    myDynamicFeaturesLabeledComponent.getLabel().setVerticalAlignment(SwingConstants.TOP);
    myDynamicFeaturesLabeledComponent.setText("Dynamic features:");
    myDynamicFeaturesLabeledComponent.setComponent(table);

    table.setShowGrid(false);
    table.setIntercellSpacing(new Dimension(0, 0));
    table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    table.setColumnSelectionAllowed(false);
    table
      .setPreferredScrollableViewportSize(new Dimension(200, table.getRowHeight() * JBTable.PREFERRED_SCROLLABLE_VIEWPORT_HEIGHT_IN_ROWS));

    TableColumnModel columnModel = table.getColumnModel();
    TableColumn column = columnModel.getColumn(DynamicFeaturesTableModel.CHECK_MARK);
    column.setCellRenderer(new EnabledCellRenderer(table.getDefaultRenderer(Boolean.class)));
    TableUtil.setupCheckboxColumn(column);
    columnModel.getColumn(FEATURE_NAME).setCellRenderer(new FeatureNameCellRenderer());

    disable();
  }

  @NotNull
  public LabeledComponent<JBTable> getComponent() {
    return myDynamicFeaturesLabeledComponent;
  }

  @NotNull
  public List<String> getDisabledDynamicFeatures() {
    return ImmutableList.copyOf(myDisabledDynamicFeatures);
  }

  public void setDisabledDynamicFeatures(List<String> disabledDynamicFeatures) {
    myDisabledDynamicFeatures.clear();
    myDisabledDynamicFeatures.addAll(disabledDynamicFeatures);

    // Update enabled/disabled state of features in active model
    myTableModel.myFeatures.forEach(x -> x.isChecked = isFeatureEnabled(x.name));
    myTableModel.fireTableDataChanged();
  }

  public void setActiveModule(@Nullable Module module) {
    if (module == null) {
      disable();
      return;
    }

    List<Module> features = DynamicAppUtils.getDependentFeatureModules(module);
    if (features.isEmpty()) {
      disable();
      return;
    }

    setFeatureList(features);
  }

  public void setFeatureList(@NotNull List<Module> features) {
    myTableModel.clear();
    features.stream()
      .sorted((o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true))
      .map(f -> new DynamicFeatureRow(f.getName(), isFeatureEnabled(f.getName())))
      .forEach(row -> myTableModel.addRow(row));
    enable();
  }

  private boolean isFeatureEnabled(@NotNull String name) {
    return !myDisabledDynamicFeatures.contains(name);
  }

  private void enable() {
    myTableModel.fireTableDataChanged();
    myDynamicFeaturesLabeledComponent.setVisible(true);
  }

  private void disable() {
    myTableModel.clear();
    myTableModel.fireTableDataChanged();
    myDynamicFeaturesLabeledComponent.setVisible(false);
  }

  public static class DynamicFeatureRow {
    public String name;
    public boolean isChecked;

    public DynamicFeatureRow(@NotNull String name, boolean isChecked) {
      this.name = name;
      this.isChecked = isChecked;
    }
  }

  public class DynamicFeaturesTableModel extends AbstractTableModel {
    public static final int CHECK_MARK = 0;
    public static final int FEATURE_NAME = 1;

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
      if (columnIndex == CHECK_MARK) {
        return myFeatures.get(rowIndex).isChecked;
      }
      else if (columnIndex == FEATURE_NAME) {
        return myFeatures.get(rowIndex).name;
      }
      return null;
    }

    @Override
    public void setValueAt(@Nullable Object aValue, int rowIndex, int columnIndex) {
      DynamicFeatureRow row = myFeatures.get(rowIndex);
      if (columnIndex == CHECK_MARK) {
        row.isChecked = aValue == null || ((Boolean)aValue).booleanValue();
        if (row.isChecked)
          myDisabledDynamicFeatures.remove(row.name);
        else
          myDisabledDynamicFeatures.add(row.name);
      }
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    @Override
    @NotNull
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK) {
        return true;
      }
      return false;
    }
  }

  private class FeatureNameCellRenderer extends DefaultTableCellRenderer {
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
      component.setEnabled(isSelected || featureRow.isChecked);
      return component;
    }
  }


  private static class EnabledCellRenderer extends DefaultTableCellRenderer {
    private final TableCellRenderer myDelegate;

    public EnabledCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setEnabled(true);
      return component;
    }
  }
}
