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

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * Wrapper for {@link AbstractTableCellEditor} and {@link TableCellRenderer}
 * implementations used for providing JTable's cell editors and renderers
 * in a typed manner: objects coming to and out of cell editor have
 * specific types instead of being just Objects.
 * <p/>
 * Both wrappers for getting editor and renderer component are included
 * because implementation of corresponding interfaces are usually provided
 * by the same class.
 * <p/>
 * Usage: subclass {@link TypedCellRendererEditor} with desired type parameters
 * instead of AbstractTableCellEditor, override typed methods. Abstract
 * methods of AbstractTableCellEditor are implemented here by delegating
 * most of the work to typed methods, and declared as "final".
 * <p/>
 * Using this class won't provide additional type safety in any other places of
 * JTable (e.g., you can still use JTable setDefaultEditor for wrong class), but
 * at least provides some safety when defining cell editors.
 *
 * @param <I> type parameter of objects passed from model to editor both for rendering and editing
 * @param <O> type parameter of objects passed from editor to model
 */
public abstract class TypedCellRendererEditor<I, O> extends AbstractTableCellEditor implements TableCellRenderer {
  /**
   * Analogue of getTableCellEditorComponent, with the only difference that
   * parameter "value" is typed (has generic type I instead of Object)
   */
  public abstract Component getEditorComponent(JTable table, I value, boolean isSelected, int row, int column);

  /**
   * Analogue of getTableCellRendererComponent, with the only difference that
   * parameter "value" is typed (has generic type I instead of Object)
   */
  public abstract Component getRendererComponent(JTable table, I value, boolean isSelected, boolean hasFocus, int row, int column);

  /**
   * Analogue of getCellEditorValue, with the only difference that result
   * is typed (has generic type O instead of Object)
   */
  public abstract O getEditorValue();

  /**
   * Implementation of {@link AbstractTableCellEditor#getTableCellEditorComponent(JTable, Object, boolean, int, int)}
   * @param value value, expected to have type I. If that's not the case, ClassCastException would be thrown
   */
  @Override
  public final Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    //noinspection unchecked
    return getEditorComponent(table, (I)value, isSelected, row, column);
  }

  /**
   * Implementation of {@link TableCellRenderer#getTableCellRendererComponent(JTable, Object, boolean, boolean, int, int)}
   * @param value value, expected to have type I. If that's not the case, ClassCastException would be thrown
   */
  @Override
  public final Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    //noinspection unchecked
    return getRendererComponent(table, (I)value, isSelected, hasFocus, row, column);
  }

  @Override
  public final Object getCellEditorValue() {
    return getEditorValue();
  }
}
