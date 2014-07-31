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
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.Map;

public class StringResourceTableModel extends AbstractTableModel {
  private final List<String> myKeys;
  private final List<Locale> myLocales;
  private final Map<String, String> myDefaultValues;
  private final Table<String, Locale, String> myTranslations;

  public StringResourceTableModel(@NotNull StringResourceData data) {
    myKeys = data.getKeys();
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
    return myLocales.size() + ConstantColumn.values().length;
  }

  @NotNull
  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    if (columnIndex >= ConstantColumn.values().length) {
      Locale locale = myLocales.get(columnIndex - ConstantColumn.values().length);
      return myTranslations.contains(myKeys.get(rowIndex), locale) ? clip(myTranslations.get(myKeys.get(rowIndex), locale)) : "";
    }
    switch (ConstantColumn.values()[columnIndex]) {
      case KEY:
        return myKeys.get(rowIndex);
      case DEFAULT_VALUE:
        return myDefaultValues.containsKey(myKeys.get(rowIndex)) ? clip(myDefaultValues.get(myKeys.get(rowIndex))) : "";
      default:
        return "";
    }
  }

  /* Clips a value to a single line to fit in a default JTable cell
   * TODO Take this out when we can handle multi-line values correctly
   */
  private static String clip(String str) {
    str = str.trim();
    int end = str.indexOf('\n');
    return end < 0 ? str : str.substring(0, end) + "[...]";
  }

  @Override
  public String getColumnName(int column) {
    if (column >= ConstantColumn.values().length) {
      /* The names of the translation columns are set by TranslationHeaderCellRenderer. The value returned here will be shown only if
       * the renderer somehow cannot set the names.
       */
      return LocaleMenuAction.getLocaleLabel(myLocales.get(column - ConstantColumn.values().length), false);
    }
    return ConstantColumn.values()[column].name;
  }
}