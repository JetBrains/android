/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.updater.configure;

import com.android.tools.idea.sdk.LogWrapper;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.RemoteSdk;
import com.android.tools.idea.sdk.remote.internal.sources.*;
import com.android.utils.StdLogger;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.table.IconTableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.*;

/**
 * Table model representing an {@link SdkSources}. Sources can be added, deleted, enabled, and disabled.
 */
class SourcesTableModel extends ListTableModel<SourcesTableModel.Row> implements EditableModel {
  private SdkSources mySources;
  private Map<String, SdkSource> myInitialItems = Maps.newHashMap();
  private SdkState mySdkState;
  
  SourcesTableModel() {
    super();
    setColumnInfos(new ColumnInfo[]{new ColumnInfo<Row, Boolean>("Enabled") {
      @Nullable
      @Override
      public Boolean valueOf(Row row) {
        return row.myEnabled;
      }

      @Override
      public int getWidth(JTable table) {
        return 60;
      }

      @Override
      public Class getColumnClass() {
        return Boolean.class;
      }

      @Override
      public boolean isCellEditable(Row row) {
        return Strings.isNullOrEmpty(row.mySource.getFetchError());
      }

      @Override
      public void setValue(Row row, Boolean value) {
        row.myEnabled = value;
        row.mySource.setEnabled(value);  // source enablement changes are saved right away so they can be reflected in the other tabs
        myUrlEnabledMap.put(row.mySource.getUrl(), value);
        mySources.notifyChangeListeners();
      }

      @Nullable
      @Override
      public TableCellRenderer getRenderer(final Row row) {
        String error = row.mySource.getFetchError();
        if (!Strings.isNullOrEmpty(error)) {
          return new IconTableCellRenderer<Boolean>() {
            @Nullable
            @Override
            protected Icon getIcon(@NotNull Boolean value, JTable table, int row) {
              return AllIcons.General.Error;
            }

            @Override
            public String getText() {
              return null;
            }

            @Override
            public String getToolTipText() {
              return row.mySource.getFetchError();
            }

            @Override
            public int getHorizontalAlignment() {
              return SwingConstants.CENTER;
            }
          };
        }
        return super.getRenderer(row);
      }
    }, new ColumnInfo<Row, String>("Name") {
      @Nullable
      @Override
      public String valueOf(Row r) {
        return r.mySource.getUiName();
      }
    }, new ColumnInfo<Row, String>("URL") {
      @Nullable
      @Override
      public String valueOf(Row r) {
        return r.mySource.getUrl();
      }
    }});
  }

  public void setSourceEnabled(int i, boolean enabled) {
    Row r = getItem(i);
    r.myEnabled = enabled;
    myUrlEnabledMap.put(r.mySource.getUrl(), enabled);
    fireTableRowsUpdated(i, i);
    mySources.notifyChangeListeners();
  }

  public void setSdkState(SdkState state) {
    mySdkState = state;
    mySources = state.getRemoteSdk().fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, new LogWrapper(Logger.getInstance(getClass())));
    for (SdkSource s : mySources.getAllSources()) {
      myInitialItems.put(s.getUrl(), s);
    }
    refreshSources();
  }

  private Map<String, Boolean> myUrlEnabledMap = Maps.newHashMap();

  public void refreshSources() {
    ArrayList<Row> items = Lists.newArrayList();
    for (SdkSource source : mySources.getAllSources()) {
      Boolean enabled = myUrlEnabledMap.get(source.getUrl());
      if (enabled == null) {
        enabled = source.isEnabled();
        myUrlEnabledMap.put(source.getUrl(), enabled);
      }
      items.add(new Row(source, enabled));
    }
    setItems(items);
  }

  public void editRow(int index) {
    Row r = getRowValue(index);
    EditSourceDialog input = showEditDialog(r.mySource);
    if (input.isOK()) {
      removeRow(index);
      createSource(input.getUrl(), input.getUiName());
    }
  }

  @Override
  public void addRow() {
    EditSourceDialog input = showEditDialog(null);
    if (input.isOK()) {
      createSource(input.getUrl(), input.getUiName());
    }
  }

  @NotNull
  private EditSourceDialog showEditDialog(SdkSource source) {
    EditSourceDialog input = new EditSourceDialog(mySources, source);
    input.show();
    return input;
  }

  private void createSource(String url, String uiName) {
    SdkSource newSource;
    // use url suffix to decide whether this is a SysImg or Addon;
    // see SdkSources.loadUserAddons() for another check like this
    if (url.endsWith(SdkSysImgConstants.URL_DEFAULT_FILENAME)) {
      newSource = new SdkSysImgSource(url, uiName);
    }
    else {
      newSource = new SdkAddonSource(url, uiName);
    }
    mySources.add(SdkSourceCategory.USER_ADDONS, newSource);
    mySources.notifyChangeListeners();
  }

  @Override
  public void removeRow(int idx) {
    mySources.remove(getRowValue(idx).mySource);
    super.removeRow(idx);
    mySources.notifyChangeListeners();
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {}

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    return false;
  }

  public boolean isEditable(int row) {
    return Sets.newHashSet(mySources.getSources(SdkSourceCategory.USER_ADDONS)).contains(getRowValue(row).mySource);
  }

  public void reset() {
    if (isSourcesModified()) {
      mySources.loadUserAddons(new StdLogger(StdLogger.Level.WARNING));
    }
    for (Row row : getItems()) {
      row.myEnabled = row.mySource.isEnabled();
    }
    refreshSources();
  }

  public boolean isSourcesModified() {
    if (getItems().size() != myInitialItems.size()) {
      return true;
    }
    for (Row row : getItems()) {
      SdkSource orig = myInitialItems.get(row.mySource.getUrl());
      if (orig == null || !Objects.equal(orig.getUiName(), row.mySource.getUiName()) || (orig.isEnabled() != row.myEnabled)) {
        return true;
      }
    }
    return false;
  }

  public void save() {
    if (isSourcesModified()) {
      mySources.saveUserAddons(new StdLogger(StdLogger.Level.WARNING));
      for (Row row : getItems()) {
        row.mySource.setEnabled(row.myEnabled);
      }
    }
  }

  protected static class Row implements SdkUpdaterConfigPanel.MultiStateRow {
    SdkSource mySource;
    boolean myEnabled;

    public Row(SdkSource source, boolean enabled) {
      mySource = source;
      myEnabled = enabled;
    }

    @Override
    public void cycleState() {
      myEnabled = !myEnabled;
    }
  }
}
