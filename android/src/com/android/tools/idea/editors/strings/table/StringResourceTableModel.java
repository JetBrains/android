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

import com.android.ide.common.resources.Locale;
import com.android.tools.idea.editors.strings.StringResource;
import com.android.tools.idea.editors.strings.StringResourceData;
import com.android.tools.idea.editors.strings.VirtualFiles;
import com.android.tools.idea.editors.strings.model.StringResourceKey;
import com.android.tools.idea.editors.strings.model.StringResourceRepository;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.SameThreadExecutor;
import java.util.Collections;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class StringResourceTableModel extends AbstractTableModel {
  public static final int KEY_COLUMN = 0;
  public static final int RESOURCE_FOLDER_COLUMN = 1;
  public static final int UNTRANSLATABLE_COLUMN = 2;
  public static final int DEFAULT_VALUE_COLUMN = 3;
  public static final int FIXED_COLUMN_COUNT = 4;

  private final StringResourceRepository myRepository;
  private final @Nullable Project myProject;
  private final @Nullable StringResourceData myData;

  private List<StringResourceKey> myKeys;
  private List<Locale> myLocales;

  public StringResourceTableModel(@NotNull StringResourceRepository repository, @NotNull Project project) {
    this(repository, project, StringResourceData.create(project, repository));
  }

  StringResourceTableModel() {
    this(StringResourceRepository.empty(), null, null);
  }

  @VisibleForTesting
  StringResourceTableModel(@Nullable StringResourceRepository repository, @Nullable Project project, @Nullable StringResourceData data) {
    myRepository = repository;
    myProject = project;
    myData = data;
    myKeys = data == null ? Collections.emptyList() : data.getKeys();
    myLocales = data == null ? Collections.emptyList() : data.getLocaleList();
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
        // Changing a key's name runs a rename refactoring which insists on being called inside invokeLater
        ApplicationManager.getApplication().invokeLater(() -> {
          myData.setKeyName(getKey(row), (String)value);

          myKeys = myData.getKeys();
          myLocales = myData.getLocaleList();

          // This change will cause a rescan of string resources which may cause a change in the number of locales in the table.
          // Change the header row just in case see b/364592051 for an example.
          fireTableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW));

          fireTableRowsUpdated(0, myKeys.size() - 1);
        });
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
        Futures.addCallback(getStringResourceAt(row).setDefaultValue((String)value), new FutureCallback<>() {
          @Override
          public void onSuccess(@Nullable Boolean changed) {
            if (changed != null && changed) {
              fireTableCellUpdated(row, column);
            }
          }

          @Override
          public void onFailure(@NotNull Throwable t) {
          }
        }, SameThreadExecutor.INSTANCE);
        break;

      default:
        Locale locale = getLocale(column);
        assert locale != null;

        Futures.addCallback(getStringResourceAt(row).putTranslation(locale, (String)value), new FutureCallback<Boolean>() {
          @Override
          public void onSuccess(@Nullable Boolean changed) {
            if (changed != null && changed) {
              fireTableCellUpdated(row, column);
            }
          }

          @Override
          public void onFailure(@NotNull Throwable t) {
          }
        }, SameThreadExecutor.INSTANCE);
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
        return computeResourceFolderString(getKey(row));
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
        Locale locale = getLocale(column);
        String columnName;
        try {
          columnName = Locale.getLocaleLabel(locale, false);
        } catch (AssertionError e) {
          // Locale code is littered with asserts, including some assuming that a locale has valid values. While we would hope that's the
          // case, we don't want the editor to just error out if someone puts in a bad resource folder name.
          columnName = locale.toString();
          Logger.getInstance(StringResourceTableModel.class).warn("Failed to get label for locale '" + columnName + "'", e);
        }
        return columnName;
    }
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return column == UNTRANSLATABLE_COLUMN ? Boolean.class : String.class;
  }

  @Override
  @SuppressWarnings("DuplicateBranchesInSwitch")
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

  @NotNull
  private String computeResourceFolderString(StringResourceKey key) {
    assert myProject != null;
    return key.getDirectory() == null ? "" : VirtualFiles.toString(key.getDirectory(), myProject);
  }

  /** Returns whether the column at the given index is a string value, i.e. a default value or translation. */
  public static boolean isStringValueColumn(int index) {
    return index >= DEFAULT_VALUE_COLUMN;
  }
}
