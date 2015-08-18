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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.intellij.util.ui.AbstractTableCellEditor;

import javax.swing.JTable;
import java.awt.Component;

/**
 * Wrapper for AbstractTableCellEditors used for providing JTable's cell editors
 * in a typed manner: objects coming to and out of cell editor have specific types
 * instead of being just Objects.
 * <p/>
 * Usage: subclass TypedCellEditor with desired type parameters instead of
 * AbstractTableCellEditor, override typed methods. Abstract methods of
 * AbstractTableCellEditor are implemented here by delegating most of the work
 * to typed methods, and declared as "final".
 * <p/>
 * Using this class won't provide additional type safety in any other places of
 * JTable (e.g., you can still use JTable setDefaultEditor for wrong class), but
 * at least provides some safety when defining cell editors.
 * @param <I> type parameter of objects passed from model to editor
 * @param <O> type parameter of objects passed from editor to model
 */
public abstract class TypedCellEditor<I, O> extends AbstractTableCellEditor {
  /**
   * Analogue of getTableCellEditorComponent, with the only difference that
   * parameter "value" is typed (has generic type I instead of Object)
   */
  public abstract Component getEditorComponent(JTable table, I value, boolean isSelected, int row, int column);

  /**
   * Analogue of getCellEditorValue, with the only difference that result
   * is typed (has generic type O instead of Object)
   */
  public abstract O getEditorValue();

  /**
   * Implementation of AbstractTableCellEditor's getTableCellEditorComponent
   * @param value value, expected to have type I. If that's not the case, ClassCastException would be thrown
   */
  @Override
  public final Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    //noinspection unchecked
    return getEditorComponent(table, (I)value, isSelected, row, column);
  }

  @Override
  public final Object getCellEditorValue() {
    return getEditorValue();
  }
}
