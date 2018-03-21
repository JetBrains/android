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
import com.android.tools.idea.editors.strings.StringResourceKey;
import com.android.tools.idea.editors.strings.StringResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.List;

public class StringResourceTableModel extends AbstractTableModel {
  public static final int KEY_COLUMN = 0;
  public static final int RESOURCE_FOLDER_COLUMN = 1;
  public static final int UNTRANSLATABLE_COLUMN = 2;
  public static final int DEFAULT_VALUE_COLUMN = 3;
  public static final int FIXED_COLUMN_COUNT = 4;

  private final StringResourceRepository myRepository;
  private final StringResourceData myData;
  private final List<StringResourceKey> myKeys;
  private final List<Locale> myLocales;

  StringResourceTableModel() {
    myRepository = StringResourceRepository.create();
    myData = null;
    myKeys = Collections.emptyList();
    myLocales = Collections.emptyList();
  }

  public StringResourceTableModel(@NotNull StringResourceRepository repository, @NotNull AndroidFacet facet) {
    myRepository = repository;

    StringResourceData data = repository.getData(facet);
    myData = data;
    myKeys = data.getKeys();
    myLocales = data.getLocaleList();
  }

  @NotNull
  public StringResourceRepository getRepository() {
    return myRepository;
  }

  @Nullable
  public StringResourceData getData() {
    return myData;
  }

  @NotNull
  public StringResource getStringResourceAt(int row) {
    return myData.getStringResource(getKey(row));
  }

  @NotNull
  public List<StringResourceKey> getKeys() {
    return myKeys;
  }

  @NotNull
  public StringResourceKey getKey(int row) {
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
  public void setValueAt(@NotNull Object value, int row, int column) {
    assert myData != null && myKeys != null;

    switch (column) {
      case KEY_COLUMN:
        String oldName = getKey(row).getName();
        String newName = (String)value;
        if (!StringUtil.equals(oldName, newName)) {
          myData.changeKeyName(getKey(row), newName);
        }
        break;
      case RESOURCE_FOLDER_COLUMN:
        break;
      case UNTRANSLATABLE_COLUMN:
        Boolean doNotTranslate = (Boolean)value;
        if (myData.setTranslatable(getKey(row), !doNotTranslate)) {
          fireTableCellUpdated(row, column);
        }

        break;
      case DEFAULT_VALUE_COLUMN:
        if (getStringResourceAt(row).setDefaultValue((String)value)) {
          fireTableCellUpdated(row, column);
        }

        break;
      default:
        Locale locale = getLocale(column);
        assert locale != null;

        if (getStringResourceAt(row).putTranslation(locale, (String)value)) {
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
        return getKey(row).getName();
      case RESOURCE_FOLDER_COLUMN:
        return getStringResourceAt(row).getResourceFolder();
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
      case RESOURCE_FOLDER_COLUMN:
        return "Resource Folder";
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
    switch (column) {
      case KEY_COLUMN:
        return true;
      case RESOURCE_FOLDER_COLUMN:
        return false;
      case UNTRANSLATABLE_COLUMN:
        return true;
      case DEFAULT_VALUE_COLUMN:
        return !getStringResourceAt(row).getDefaultValueAsString().contains("\n");
      default:
        Locale locale = getLocale(column);
        assert locale != null;

        return !getStringResourceAt(row).getTranslationAsString(locale).contains("\n");
    }
  }

  @Nullable
  public String getCellProblem(int row, int column) {
    switch (column) {
      case KEY_COLUMN:
        return myData.validateKey(getKey(row));
      case RESOURCE_FOLDER_COLUMN:
      case UNTRANSLATABLE_COLUMN:
        return null;
      case DEFAULT_VALUE_COLUMN:
        return getStringResourceAt(row).validateDefaultValue();
      default:
        Locale locale = getLocale(column);
        assert locale != null;

        return getStringResourceAt(row).validateTranslation(locale);
    }
  }
}
