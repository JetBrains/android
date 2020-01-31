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
package com.android.tools.idea.gradle.editor.ui;

import com.android.annotations.NonNull;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * {@link ComboBox} customized for working with gradle entity values.
 */
public class GradleEditorComboBox extends ComboBox {

  @Nullable private WeakReference<JTable> myTableRef;

  public GradleEditorComboBox(ComboBoxModel model) {
    super(model);
    setEditor(new GradleEditorComboBoxEditor());
    fixMaxRepresentationIfNecessary();
    getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (myTableRef == null) {
          return;
        }
        if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_ESCAPE) {
          JTable table = myTableRef.get();
          if (table != null && table.isEditing()) {
            Component editorComponent = table.getEditorComponent();
            if (editorComponent != null && UIUtil.isAncestor(editorComponent, GradleEditorComboBox.this)) {
              TableCellEditor cellEditor = table.getCellEditor();
              if (cellEditor != null) {
                cellEditor.stopCellEditing();
              }
            }
          }
        }
      }
    });
  }

  public void setTable(@NonNull JTable table) {
    myTableRef = new WeakReference<JTable>(table);
  }

  /**
   * We experience weird behavior (most likely a bug at standard os x aqua combo box ui stuff) when the combo box is editable and used
   * at the table cell editor - <a href="https://dl.dropboxusercontent.com/u/1648086/screenshot/idea/combobox-dots.png">example</a>.
   * <p/>
   * Debugging shows that AquaComboBoxButton paints selected combo box' element at the given CellRendererPane
   * (AquaComboBoxButton.doRendererPaint()). That text is too wide for the button rectangle, that's why it's truncated to '...' by swing.
   * <p/>
   * What we do here is to apply dummy CellRendererPane to the AquaComboBoxButton which does nothing on request to paint.
   */
  private void fixMaxRepresentationIfNecessary() {
    if (!"AquaComboBoxUI".equals(getUI().getClass().getSimpleName())) {
      return;
    }
    JButton arrowButton = UIUtil.findComponentOfType(this, JButton.class);
    if (arrowButton == null) {
      return;
    }
    try {
      for (Field field : arrowButton.getClass().getDeclaredFields()) {
        field.setAccessible(true);
        if (field.getGenericType() == CellRendererPane.class) {
          field.set(arrowButton, new DummyCellRendererPane());
          break;
        }
      }
    }
    catch (Throwable ignore) {
    }
  }

  private static class DummyCellRendererPane extends CellRendererPane {
    @Override
    public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
    }
  }
}
