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
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.utils.Pair;
import com.intellij.openapi.Disposable;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Model for confirmation step table.
 * <p/>
 * Inspiration source: PluginTableModel.
 */
public class SmwConfirmationTableModel extends AbstractTableModel implements Disposable {

  private boolean myIsDisposed;
  private final ColumnInfo[] myColumns;
  private final List<LineInfo> myLines = new ArrayList<LineInfo>();

  public SmwConfirmationTableModel(ColumnInfo... columns) {
    myColumns = columns;
  }

  @Override
  public int getRowCount() {
    return myLines.size();
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
  public LineInfo getObjectAt(int rowIndex) {
    return myLines.get(rowIndex);
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    //noinspection ConstantConditions,unchecked
    return myColumns[columnIndex].valueOf(myLines.get(rowIndex));
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    //noinspection unchecked
    return myColumns[columnIndex].isCellEditable(myLines.get(rowIndex));
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    //noinspection unchecked
    myColumns[columnIndex].setValue(myLines.get(rowIndex), aValue);
    for (int i = 0; i < myColumns.length; i++) {
      fireTableCellUpdated(rowIndex, i);
    }
  }

  public void fillModel(List<Pair<SmwSelectionAction, IListDescription>> selectedActions) {
    myLines.clear();

    // TODO compute install/updates dependencies.
    // also it's nice to sort the selection by placing dependencies first.
    // right now just use the selection as-is.

    boolean needHeader = true;
    for (Pair<SmwSelectionAction, IListDescription> action : selectedActions) {
      if (action.getFirst() != SmwSelectionAction.UPDATE &&
          action.getFirst() != SmwSelectionAction.INSTALL) {
        continue;
      }
      if (needHeader) {
        myLines.add(new LineInfo("===[ Packages to install/update ]===="));
        needHeader = false;
      }
      if (action.getFirst() == SmwSelectionAction.UPDATE) {
        myLines.add(new LineInfo((LocalPkgInfo)action.getSecond(), LineType.UPDATE));
      } else {
        myLines.add(new LineInfo((RemotePkgInfo)action.getSecond()));
      }
    }

    // Find all removable items and list them last
    needHeader = true;
    for (Pair<SmwSelectionAction, IListDescription> action : selectedActions) {
      if (action.getFirst() != SmwSelectionAction.REMOVE) {
        continue;
      }
      if (needHeader) {
        myLines.add(new LineInfo("===[ Packages to remove ]===="));
        needHeader = false;
      }
      myLines.add(new LineInfo((LocalPkgInfo)action.getSecond(), LineType.REMOVE));
    }

    fireTableDataChanged();
  }

  public void fillModel(@NonNull List<LocalPkgInfo> removeList,
                        @NonNull List<RemotePkgInfo> installList) {
    myLines.clear();
    fireTableDataChanged();
  }

  @Override
  public void dispose() {
    myIsDisposed = true;
  }

  // ----

  enum LineType {
    HEADER,
    REMOVE,
    UPDATE,
    INSTALL
  }

  static class LineInfo {
    private LineType myType;
    private String myHeader;
    private LocalPkgInfo myRemovedPkg;
    private LocalPkgInfo myUpdatePkg;
    private RemotePkgInfo myInstallNewPkg;
    private boolean myAccept;

    public LineInfo(@NonNull String header) {
      myType = LineType.HEADER;
      myHeader = header;
    }

    @SuppressWarnings("ConstantConditions")
    public LineInfo(@NonNull LocalPkgInfo info, LineType type) {
      assert type == LineType.REMOVE || type == LineType.UPDATE;
      myType = type;
      if (type == LineType.REMOVE) {
        myRemovedPkg = info;
      } else if (type == LineType.UPDATE) {
        myUpdatePkg = info;
      }
    }

    public LineInfo(@NonNull RemotePkgInfo info) {
      myType = LineType.INSTALL;
      myInstallNewPkg = info;
    }

    @NonNull
    public LineType getType() {
      return myType;
    }

    @Nullable
    public LocalPkgInfo getRemovedPkg() {
      return myRemovedPkg;
    }

    @Nullable
    public LocalPkgInfo getUpdatePkg() {
      return myUpdatePkg;
    }

    @Nullable
    public RemotePkgInfo getInstallNewPkg() {
      return myInstallNewPkg;
    }
  }


  // ------------

  public static class LabelColumnInfo extends ColumnInfo<LineInfo, String> {
    private SmwConfirmationTableModel myModel;

    public LabelColumnInfo(@NonNull String name) {
      super(name);
    }

    public void setModel(@NonNull SmwConfirmationTableModel model) {
      myModel = model;
    }

    @Override
    public String valueOf(LineInfo item) {
      // TODO placeholder.
      switch (item.myType) {
        case HEADER:
          return item.myHeader;

        case REMOVE:
          return "Remove " + item.myRemovedPkg.getListDescription();

        case INSTALL:
          return "New " + item.myInstallNewPkg.getListDescription();

        case UPDATE:
          assert item.myUpdatePkg.getUpdate() != null;
          return "Update to " + item.myUpdatePkg.getUpdate().getListDescription();
      }

      return ""; // should not happen
    }

    @Override
    public boolean isCellEditable(LineInfo item) {
      return false;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(LineInfo item) {
      return new DefaultTableCellRenderer();
    }
  }

  // ------------

  public static class InstallColumnInfo extends ColumnInfo<LineInfo, Boolean> {

    public InstallColumnInfo(@NonNull String name) {
      super(name);
    }

    @Override
    public Boolean valueOf(LineInfo item) {
      return item.myAccept;
    }

    @Override
    public boolean isCellEditable(LineInfo item) {
      return item.myType != LineType.HEADER;
    }

    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public TableCellEditor getEditor(LineInfo item) {
      return new BooleanTableCellEditor();
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(LineInfo o) {
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
    public void setValue(LineInfo item, Boolean value) {
      item.myAccept = value.booleanValue();
      super.setValue(item, value);
    }
  }
}
