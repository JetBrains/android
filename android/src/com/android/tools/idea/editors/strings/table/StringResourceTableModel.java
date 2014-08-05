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
import com.android.tools.idea.rendering.StringResourceData;
import com.android.tools.idea.rendering.Locale;
import com.google.common.collect.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.Map;

public class StringResourceTableModel extends AbstractTableModel {
  private final List<String> myKeys;
  private final List<String> myUntranslatableKeys;
  private final List<Locale> myLocales;
  private final Map<String, String> myDefaultValues;
  private final Table<String, Locale, String> myTranslations;

  public StringResourceTableModel(@NotNull StringResourceData data) {
    myKeys = data.getKeys();
    myUntranslatableKeys = data.getUntranslatableKeys();
    myLocales = data.getLocales();
    myDefaultValues = data.getDefaultValues();
    myTranslations = data.getTranslations();
  }

  @Override
  public int getRowCount() {
    return myKeys.size();
  }

  @Override
  public int getColumnCount() {
    return myLocales.size() + ConstantColumn.COUNT;
  }

  @NotNull
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return getClippedValue(rowIndex, columnIndex);
  }

  @NotNull
  public Object getClippedValue(int rowIndex, int columnIndex) {
    Object value = getValue(rowIndex, columnIndex);
    return value instanceof String ? clip(String.valueOf(value)) : value;
  }

  @NotNull
  public Object getValue(int rowIndex, int columnIndex) {
    if (columnIndex >= ConstantColumn.COUNT) {
        Locale locale = myLocales.get(columnIndex - ConstantColumn.COUNT);
        return myTranslations.contains(myKeys.get(rowIndex), locale) ? myTranslations.get(myKeys.get(rowIndex), locale).trim() : "";
    }
    switch (ConstantColumn.values()[columnIndex]) {
      case KEY:
        return myKeys.get(rowIndex);
      case DEFAULT_VALUE:
        return myDefaultValues.containsKey(myKeys.get(rowIndex)) ? myDefaultValues.get(myKeys.get(rowIndex)).trim() : "";
      case UNTRANSLATABLE:
        return myUntranslatableKeys.contains(myKeys.get(rowIndex));
      default:
        return "";
    }
  }

  /**
   * Clips a value to a single line to fit in a default JTable cell
   * TODO Take this out when we can handle multi-line values correctly
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
      return LocaleMenuAction.getLocaleLabel(myLocales.get(column - ConstantColumn.COUNT), false);
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

  @Nullable
  public String getCellProblem(int row, int column) {
    if (String.valueOf(getValueAt(row, column)).isEmpty()) {
      if (column < ConstantColumn.COUNT) {
        return ConstantColumn.values()[column].name + " should not be empty";
      } else if (!myUntranslatableKeys.contains(myKeys.get(row))) {
        return "Translation for " + myKeys.get(row) + " should not be empty";
      }
    } else if (myUntranslatableKeys.contains(myKeys.get(row)) && column >= ConstantColumn.COUNT) {
      return "Key " + myKeys.get(row) + " should not be translated";
    }
    return null;
  }

  @Nullable
  public String getKeyProblem(int row) {
    for (int column = 0, n = getColumnCount(); column < n; ++column) {
      if (getCellProblem(row, column) != null) {
        if (column < ConstantColumn.COUNT) {
          return ConstantColumn.values()[column].name + " is missing";
        } else if (myUntranslatableKeys.contains(myKeys.get(row))) {
          return "Key should not be translated into " + getColumnName(column);
        } else {
          return "Translation for " + getColumnName(column) + " is missing";
        }
      }
    }
    return null;
  }
}