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
import java.util.List;

public class StringResourceTableModel extends AbstractTableModel {
  private final StringResourceData myData;
  private final List<String> myKeys;
  private final List<Locale> myLocales;

  StringResourceTableModel() {
    myData = null;
    myKeys = null;
    myLocales = null;
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
    return myData.getStringResource(keyOfRow(row));
  }

  @NotNull
  public String keyOfRow(int row) {
    return myKeys == null ? "" : myKeys.get(row);
  }

  @Nullable
  public Locale localeOfColumn(int column) {
    return (column < ConstantColumn.COUNT || myLocales == null) ? null : myLocales.get(column - ConstantColumn.COUNT);
  }

  @Override
  public int getRowCount() {
    return myKeys == null ? 0 : myKeys.size();
  }

  @Override
  public int getColumnCount() {
    return myLocales == null ? 0 : myLocales.size() + ConstantColumn.COUNT;
  }

  @Override
  public void setValueAt(Object value, int row, int column) {
    assert myData != null && myKeys != null;

    if (ConstantColumn.KEY.ordinal() == column) {
      myData.changeKeyName(myKeys.get(row), (String)value);
      fireTableRowsUpdated(0, myKeys.size());
    }
    else if (ConstantColumn.UNTRANSLATABLE.ordinal() == column) {
      Boolean doNotTranslate = (Boolean)value;
      if (myData.setTranslatable(keyOfRow(row), !doNotTranslate)) {
        fireTableCellUpdated(row, column);
      }
    }
    else {
      if (myData.setTranslation(keyOfRow(row), localeOfColumn(column), (String)value)) {
        fireTableCellUpdated(row, column);
      }
    }
  }

  @NotNull
  @Override
  public Object getValueAt(int row, int column) {
    if (myData == null) {
      return "";
    }

    String key = keyOfRow(row);

    if (column >= ConstantColumn.COUNT) {
      Locale locale = localeOfColumn(column);
      return locale == null ? "" : myData.getStringResource(key).getTranslationAsString(locale);
    }
    switch (ConstantColumn.values()[column]) {
      case KEY:
        return key;
      case DEFAULT_VALUE:
        return myData.getStringResource(key).getDefaultValueAsString();
      case UNTRANSLATABLE:
        return !myData.getStringResource(key).isTranslatable();
      default:
        return "";
    }
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
    }
    else {
      return ConstantColumn.values()[column].sampleData.getClass();
    }
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return true;
  }

  @Nullable
  public String getCellProblem(int row, int column) {
    if (myData == null) {
      return null;
    }

    String key = keyOfRow(row);
    if (ConstantColumn.KEY.ordinal() == column) {
      return myData.validateKey(key);
    }
    else {
      Locale l = localeOfColumn(column);
      return myData.validateTranslation(key, l);
    }
  }
}