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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.editors.strings.StringResourceDataController;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.StringResourceData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;

public class StringResourceTableModel extends AbstractTableModel {
  private final StringResourceDataController myController;

  public StringResourceTableModel(@NotNull StringResourceDataController controller) {
    myController = controller;
  }

  @NotNull
  public StringResourceDataController getController() {
    return myController;
  }

  @NotNull
  public String keyOfRow(int row) {
    return myController.getData().getKeys().get(row);
  }

  public int rowOfKey(@NotNull String key) {
    return myController.getData().getKeys().indexOf(key);
  }

  @Nullable
  public Locale localeOfColumn(int column) {
    return column < ConstantColumn.COUNT ? null : myController.getData().getLocales().get(column - ConstantColumn.COUNT);
  }

  public int columnOfLocale(@NotNull Locale locale) {
    int index = myController.getData().getLocales().indexOf(locale);
    return index >= 0 ? index + ConstantColumn.COUNT : index;
  }

  @Override
  public int getRowCount() {
    return myController.getData().getKeys().size();
  }

  @Override
  public int getColumnCount() {
    return myController.getData().getLocales().size() + ConstantColumn.COUNT;
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    String key = keyOfRow(row);
    myController.selectData(key, null);
    myController.setUntranslatable((Boolean) value);
  }

  /**
   * Gets a clipped version of the value at (row, column), appropriate for displaying in a single-line JTable cell.
   * @param row The row index
   * @param column The column index
   * @return The clipped value
   */
  @NotNull
  @Override
  public Object getValueAt(int row, int column) {
    Object value = getValue(row, column);
    return value instanceof String ? clip(String.valueOf(value)) : value;
  }

  /**
   * Gets the value at (row, column), which may span multiple lines.
   * @param row The row index
   * @param column The column index
   * @return The value
   */
  @NotNull
  public Object getValue(int row, int column) {
    if (column >= ConstantColumn.COUNT) {
        Locale locale = localeOfColumn(column);
        return myController.getData().getTranslations().contains(keyOfRow(row), locale)
               ? StringResourceData.resourceToString(myController.getData().getTranslations().get(keyOfRow(row), locale)) : "";
    }
    switch (ConstantColumn.values()[column]) {
      case KEY:
        return keyOfRow(row);
      case DEFAULT_VALUE:
        return myController.getData().getDefaultValues().containsKey(keyOfRow(row))
               ? StringResourceData.resourceToString(myController.getData().getDefaultValues().get(keyOfRow(row))) : "";
      case UNTRANSLATABLE:
        return myController.getData().getUntranslatableKeys().contains(keyOfRow(row));
      default:
        return "";
    }
  }

  /**
   * Clips a value to a single line to fit in a default JTable cell
   */
  private static String clip(String str) {
    int end = str.indexOf('\n');
    return end < 0 ? str : str.substring(0, end) + "[...]";
  }

  @Override
  public String getColumnName(int column) {
    if (column >= ConstantColumn.COUNT) {
      // The names of the translation columns are set by TranslationHeaderCellRenderer.
      // The value returned here will be shown only if the renderer somehow cannot set the names.
      return LocaleMenuAction.getLocaleLabel(localeOfColumn(column), false);
    }
    return ConstantColumn.values()[column].name;
  }

  @Override
  public Class getColumnClass(int column) {
    if (column >= ConstantColumn.COUNT) {
      return ConstantColumn.DEFAULT_VALUE.sampleData.getClass();
    } else {
      return ConstantColumn.values()[column].sampleData.getClass();
    }
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return ConstantColumn.indexMatchesColumn(column, ConstantColumn.UNTRANSLATABLE);
  }

  @Nullable
  public String getCellProblem(int row, int column) {
    if (String.valueOf(getValueAt(row, column)).isEmpty()) {
      if (column < ConstantColumn.COUNT) {
        return ConstantColumn.values()[column].name + " should not be empty";
      } else if (!myController.getData().getUntranslatableKeys().contains(keyOfRow(row))) {
        return "Translation for " + keyOfRow(row) + " should not be empty";
      }
    } else if (myController.getData().getUntranslatableKeys().contains(keyOfRow(row)) && column >= ConstantColumn.COUNT) {
      return "Key " + keyOfRow(row) + " should not be translated";
    }
    return null;
  }

  @Nullable
  public String getKeyProblem(int row) {
    for (int column = 0, n = getColumnCount(); column < n; ++column) {
      if (getCellProblem(row, column) != null) {
        if (column < ConstantColumn.COUNT) {
          return ConstantColumn.values()[column].name + " is missing";
        } else if (myController.getData().getUntranslatableKeys().contains(keyOfRow(row))) {
          return "Key should not be translated into " + getColumnName(column);
        } else {
          return "Translation for " + getColumnName(column) + " is missing";
        }
      }
    }
    return null;
  }
}