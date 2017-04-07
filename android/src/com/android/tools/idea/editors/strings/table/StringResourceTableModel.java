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
import com.android.tools.idea.editors.strings.StringResource;
import com.android.tools.idea.editors.strings.StringResourceData;
import com.android.tools.idea.rendering.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.List;

public class StringResourceTableModel extends AbstractTableModel {
  public static final int KEY_COLUMN = 0;
  public static final int UNTRANSLATABLE_COLUMN = 1;
  public static final int DEFAULT_VALUE_COLUMN = 2;
  public static final int FIXED_COLUMN_COUNT = 3;

  private final StringResourceData myData;
  private final List<String> myKeys;
  private final List<Locale> myLocales;

  StringResourceTableModel() {
    myData = null;
    myKeys = Collections.emptyList();
    myLocales = Collections.emptyList();
  }

  public StringResourceTableModel(@NotNull StringResourceData data) {
    myData = data;
    myKeys = data.getKeys();
    myLocales = data.getLocales();
  }

  @Nullable
  public StringResourceData getData() {
    return myData;
  }

  @NotNull
  public StringResource getStringResourceAt(int row) {
    return myData.getStringResource(myKeys.get(row));
  }

  @NotNull
  public String getKey(int row) {
    return myKeys.get(row);
  }

  @Nullable
  public Locale getLocale(int column) {
    assert 0 <= column && column < getColumnCount() : column;
    return column < FIXED_COLUMN_COUNT ? null : myLocales.get(column - FIXED_COLUMN_COUNT);
  }

  @Override
  public int getRowCount() {
    return myKeys.size();
  }

  @Override
  public int getColumnCount() {
    return FIXED_COLUMN_COUNT + myLocales.size();
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    assert myData != null && myKeys != null;

    switch (column) {
      case KEY_COLUMN:
        myData.changeKeyName(myKeys.get(row), (String)value);
        fireTableRowsUpdated(0, myKeys.size());

        break;
      case UNTRANSLATABLE_COLUMN:
        Boolean doNotTranslate = (Boolean)value;
        if (myData.setTranslatable(getKey(row), !doNotTranslate)) {
          fireTableCellUpdated(row, column);
        }

        break;
      default:
        if (myData.setTranslation(getKey(row), getLocale(column), (String)value)) {
          fireTableCellUpdated(row, column);
        }

        break;
    }
  }

  @NotNull
  @Override
  public Object getValueAt(int row, int column) {
    switch (column) {
      case KEY_COLUMN:
        return getKey(row);
      case UNTRANSLATABLE_COLUMN:
        return !getStringResourceAt(row).isTranslatable();
      case DEFAULT_VALUE_COLUMN:
        return getStringResourceAt(row).getDefaultValueAsString();
      default:
        Locale locale = getLocale(column);
        assert locale != null;

        return getStringResourceAt(row).getTranslationAsString(locale);
    }
  }

  @NotNull
  @Override
  public String getColumnName(int column) {
    switch (column) {
      case KEY_COLUMN:
        return "Key";
      case UNTRANSLATABLE_COLUMN:
        return "Untranslatable";
      case DEFAULT_VALUE_COLUMN:
        return "Default Value";
      default:
        return LocaleMenuAction.getLocaleLabel(getLocale(column), false);
    }
  }

  @Override
  public Class getColumnClass(int column) {
    return column == UNTRANSLATABLE_COLUMN ? Boolean.class : String.class;
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return true;
  }

  @Nullable
  public String getCellProblem(int row, int column) {
    return column == KEY_COLUMN ? myData.validateKey(getKey(row)) : myData.validateTranslation(getKey(row), getLocale(column));
  }
}
