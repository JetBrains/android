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
package com.android.tools.idea.editors.theme.attributes;

import org.jetbrains.annotations.Nullable;
import spantable.CellSpanModel;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Table model which uses delegate model (which happens to be {@link AttributesTableModel} in the single use-case) with:
 *   1. Row-wide labels
 *   2. Additional "attributes", like theme's parent, which aren't attributes but which we want to edit in uniform w/ attributes way
 */
public class AugmentedModel extends AbstractTableModel implements CellSpanModel {
  private final static Logger LOG = Logger.getInstance(AugmentedModel.class);

  private final CellSpanModel myDelegate;
  private final List<TableLabel> myLabels;
  private String myParentName;

  private final List<ParentChangedListener> myParentChangedListeners = new ArrayList<ParentChangedListener>();

  public interface ParentChangedListener {
    void parentChanged(final String newParent);
  }

  public String getParentName() {
    return myParentName;
  }

  public void addParentChangedListener(final ParentChangedListener listener) {
    myParentChangedListeners.add(listener);
  }

  public void removeParentChangedListener(final ParentChangedListener listener) {
    myParentChangedListeners.remove(listener);
  }

  /**
   * Constructor
   * @param delegate Model delegate. Its .getRowSpan should always return 1
   * @param labels List of labels, should be in increasing order of .getRowPosition()
   */
  public AugmentedModel(final CellSpanModel delegate, final List<TableLabel> labels, final @Nullable String parentName) {
    myDelegate = delegate;
    myLabels = labels;
    myParentName = parentName;
  }

  public RowContents getRowContents(final int rowIndex) {
    if (rowIndex == 0) {
      return new ParentAttribute();
    }

    int offset = 1;
    for (final TableLabel label : myLabels) {
      final int labelRowIndex = label.getRowPosition() + offset;

      if (labelRowIndex < rowIndex) {
        offset++;
      } else if (labelRowIndex == rowIndex) {
        return new LabelContents(label);
      } else { // labelRowIndex > rowIndex
        return new DelegateContents(rowIndex - offset);
      }
    }

    return new DelegateContents(rowIndex - offset);
  }

  @Override
  public int getRowCount() {
    return myDelegate.getRowCount() + myLabels.size() + 1;
  }

  @Override
  public int getColumnCount() {
    return myDelegate.getColumnCount();
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return getRowContents(rowIndex).getValueAt(columnIndex);
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    getRowContents(rowIndex).setValueAt(columnIndex, aValue);
  }

  @Override
  public int getColumnSpan(int row, int column) {
    return getRowContents(row).getColumnSpan(column);
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return getRowContents(rowIndex).isCellEditable(columnIndex);
  }

  @Override
  public int getRowSpan(int row, int column) {
    return 1;
  }

  @Override
  public Class<?> getCellClass(int row, int column) {
    return getRowContents(row).getCellClass(column);
  }

  /**
   * Basically a union type, RowContents = LabelContents | DelegateContents | ParentAttribute
   */
  private interface RowContents {
    int getColumnSpan(int column);
    Object getValueAt(int column);
    void setValueAt(int column, Object value);
    Class<?> getCellClass(int column);
    boolean isCellEditable(int column);
  }

  private class ParentAttribute implements RowContents {
    @Override
    public int getColumnSpan(int column) {
      return 1;
    }

    @Override
    public Object getValueAt(int column) {
      if (column == 0) {
        return "Theme Parent";
      } else {
        return myParentName == null ? "[no parent]" : myParentName;
      }
    }

    @Override
    public void setValueAt(int column, Object value) {
      if (column == 0) {
        throw new RuntimeException("Tried to setValue at parent attribute label");
      } else {
        myParentName = (String) value;
        for (final ParentChangedListener listener : myParentChangedListeners) {
          listener.parentChanged(myParentName);
        }
      }
    }

    @Override
    public Class<?> getCellClass(int column) {
      return String.class;
    }

    @Override
    public boolean isCellEditable(int column) {
      return (column == 1 && myParentName != null);
    }
  }

  private class LabelContents implements RowContents {
    private final TableLabel myLabel;

    private LabelContents(TableLabel label) {
      myLabel = label;
    }

    @Override
    public int getColumnSpan(int column) {
      return column == 0 ? getColumnCount() : 0;
    }

    @Override
    public Object getValueAt(int column) {
      return myLabel;
    }

    @Override
    public Class<?> getCellClass(int column) {
      return TableLabel.class;
    }

    @Override
    public boolean isCellEditable(int column) {
      return false;
    }

    @Override
    public void setValueAt(int column, Object value) {
      throw new RuntimeException(String.format("Tried to setValue at immutable label row of LabelledModel, column = %1$d" + column));
    }
  }

  private class DelegateContents implements RowContents {
    private final int myRowIndex;

    public DelegateContents(int rowIndex) {
      myRowIndex = rowIndex;
    }

    @Override
    public int getColumnSpan(int column) {
      return myDelegate.getColumnSpan(myRowIndex, column);
    }

    @Override
    public Object getValueAt(int column) {
      return myDelegate.getValueAt(myRowIndex, column);
    }

    @Override
    public Class<?> getCellClass(int column) {
      return myDelegate.getCellClass(myRowIndex, column);
    }

    @Override
    public boolean isCellEditable(int column) {
      return myDelegate.isCellEditable(myRowIndex, column);
    }

    @Override
    public void setValueAt(int column, Object value) {
      myDelegate.setValueAt(value, myRowIndex, column);
    }
  }
}
