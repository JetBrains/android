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
package com.android.tools.idea.sdk.wizard;

import com.android.annotations.NonNull;
import com.android.sdklib.internal.repository.IListDescription;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.UpdateResult;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.SdkLifecycleListener;
import com.android.tools.idea.sdk.SdkState;
import com.android.utils.Pair;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Model for Selection step table.
 * <p/>
 * Implementation detail: Implements {@link Disposable} in order to drop the message bus connection
 * automatically when disposed, and also do prevent in-flight async updates after disposal.
 * <p/>
 * Inspiration source: PluginTableModel.
 */
public class SmwSelectionTableModel extends AbstractTableModel implements Disposable {

  @NonNull private final List<Object> myInfos = new ArrayList<Object>();
  @NonNull private final Set<Object> myChanged = new HashSet<Object>();

  private boolean myIsDisposed;
  private final ColumnInfo[] myColumns;

  public SmwSelectionTableModel(ColumnInfo... columns) {
    myColumns = columns;
  }

  @Override
  public int getRowCount() {
    return myInfos.size();
  }

  @Override
  public int getColumnCount() {
    return myColumns.length;
  }

  @NonNull
  public ColumnInfo getColumnInfo(int columnIndex) {
    return myColumns[columnIndex];
  }

  @NonNull
  public Object getObjectAt(int rowIndex) {
    return myInfos.get(rowIndex);
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    //noinspection ConstantConditions,unchecked
    return myColumns[columnIndex].valueOf(myInfos.get(rowIndex));
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    //noinspection unchecked
    return myColumns[columnIndex].isCellEditable(myInfos.get(rowIndex));
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    //noinspection unchecked
    myColumns[columnIndex].setValue(myInfos.get(rowIndex), aValue);
    for (int i = 0; i < myColumns.length; i++) {
      fireTableCellUpdated(rowIndex, i);
    }
  }

  public void linkToSdkState(@NonNull final SdkState sdkState) {
    final Runnable resetInUiThread = new Runnable() {
      @Override
      public void run() {
        if (!myIsDisposed) {
          resetTable(sdkState);
        }
      }
    };

    resetInUiThread.run();

    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    app.getMessageBus().connect(this).subscribe(SdkLifecycleListener.TOPIC, new SdkLifecycleListener() {
      @Override
      public void localSdkLoaded(@NonNull AndroidSdkData sdkData) {
        // Only update if the exact same SDK data has changed
        if (sdkState.getSdkData() == sdkData) {
          // TODO only update local pkg infos that have changed
          // Right now just reset the table model from scratch.
          app.invokeLater(resetInUiThread, ModalityState.any());
        }
      }

      @Override
      public void remoteSdkLoaded(@NonNull AndroidSdkData sdkData) {
        // Skip. Just wait for updatesComputed.
      }

      @Override
      public void updatesComputed(@NonNull AndroidSdkData sdkData) {
        // Only update if the exact same SDK data has changed
        if (sdkState.getSdkData() == sdkData) {
          // TODO only update updates pkg infos that have changed.
          // Right now just reset the table model from scratch.
          app.invokeLater(resetInUiThread, ModalityState.any());
        }
      }
    });
  }

  private void resetTable(SdkState sdkState) {
    myInfos.clear();
    myChanged.clear();
    Collections.addAll(myInfos, sdkState.getLocalPkgInfos());

    UpdateResult updates = sdkState.getUpdates();
    if (updates != null) {
      myInfos.addAll(updates.getNewPkgs());
    }

    fireTableDataChanged();
  }

  private SmwSelectionAction computeAction(Object item) {
    boolean changed = myChanged.contains(item);

    if (item instanceof LocalPkgInfo) {
      LocalPkgInfo local = (LocalPkgInfo)item;

      if (changed) {
        return SmwSelectionAction.REMOVE;
      }

      if (local.hasUpdate()) {
        return SmwSelectionAction.UPDATE;
      }
    } else if (item instanceof RemotePkgInfo) {
      if (changed) {
        return SmwSelectionAction.INSTALL;
      } else {
        return SmwSelectionAction.NEW_REMOTE;
      }
    }

    return SmwSelectionAction.KEEP_LOCAL;
  }

  public List<Pair<SmwSelectionAction, Object>> getActions() {
    List<Pair<SmwSelectionAction, Object>> actions = new ArrayList<Pair<SmwSelectionAction, Object>>(myChanged.size());
    for (Object item : myChanged) {
      SmwSelectionAction action = computeAction(item);
      switch (action) {
        case INSTALL:
        case UPDATE:
        case REMOVE:
          actions.add(Pair.of(action, item));
        default:
          // no-op for keep / new remote (shouldn't be in the list anyway)
      }
    }
    return actions;
  }

  @Override
  public void dispose() {
    myIsDisposed = true;
  }

  // ------------

  public static class LabelColumnInfo extends ColumnInfo<Object, String> {
    private SmwSelectionTableModel myModel;

    public LabelColumnInfo(@NonNull String name) {
      super(name);
    }

    public void setModel(@NonNull SmwSelectionTableModel model) {
      myModel = model;
    }

    @Override
    public String valueOf(Object item) {
      // TODO edit display text to something more user-friendly. This is just a placeholder.

      String desc = "";
      if (item instanceof IListDescription) {
        desc = ((IListDescription)item).getListDescription();
      }

      SmwSelectionAction action = myModel == null ? null : myModel.computeAction(item);

      if (action != null) {
        if (action == SmwSelectionAction.INSTALL) {
          desc = "[Install] " + desc;

        } else if (action == SmwSelectionAction.REMOVE) {
          desc = "[Remove] " + desc;

        } else if (action == SmwSelectionAction.UPDATE) {
          assert item instanceof LocalPkgInfo;
          assert ((LocalPkgInfo) item).getUpdate() != null;
          //noinspection ConstantConditions
          desc = "[Update] " + ((LocalPkgInfo) item).getUpdate().getListDescription();
        }
      }

      return desc;
    }

    @Override
    public boolean isCellEditable(Object item) {
      return false;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(Object item) {
      return new DefaultTableCellRenderer();
    }
  }

  // ------------

  public static class InstallColumnInfo extends ColumnInfo<Object, Boolean> {
    private SmwSelectionTableModel myModel;

    public InstallColumnInfo(@NonNull String name) {
      super(name);
    }

    public void setModel(@NonNull SmwSelectionTableModel model) {
      myModel = model;
    }

    private boolean isChanged(Object item) {
      return myModel != null && myModel.myChanged.contains(item);
    }

    @Override
    public Boolean valueOf(Object item) {
      boolean installed = item instanceof LocalPkgInfo;
      if (isChanged(item)) {
        installed = !installed;
      }
      return installed;
    }

    @Override
    public boolean isCellEditable(Object item) {
      return item instanceof LocalPkgInfo || item instanceof RemotePkgInfo;
    }

    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public TableCellEditor getEditor(Object item) {
      return new BooleanTableCellEditor();
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(Object o) {
      return new BooleanTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          return super.getTableCellRendererComponent(table, value == null ? Boolean.TRUE : value, isSelected, hasFocus, row, column);
        }
      };
    }

    @Override
    public int getWidth(JTable table) {
      return new JCheckBox().getPreferredSize().width;
    }

    @Override
    public void setValue(Object item, Boolean value) {
      if (myModel != null) {
        boolean installed = item instanceof LocalPkgInfo;
        if (value.booleanValue() != installed) {
          myModel.myChanged.add(item);
        } else {
          myModel.myChanged.remove(item);
        }
      }
      super.setValue(item, value);
    }
  }
}
