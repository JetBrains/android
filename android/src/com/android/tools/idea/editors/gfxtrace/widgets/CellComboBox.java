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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * Shows a list of loadable cells in a drop down.
 */
public abstract class CellComboBox<T extends CellWidget.Data> extends CellWidget<T, ComboBox> {
  public CellComboBox(CellRenderer.CellLoader<T> loader) {
    super(new ComboBox() {
      @Override
      public Object getPrototypeDisplayValue() {
        // Every item is a prototype value. This will prevent it from loading all items to determine the size.
        ComboBoxModel model = getModel();
        return (model.getSize() == 0) ? null : model.getElementAt(0);
      }
    }, loader);
    myComponent.setRenderer(myRenderer);
    myComponent.setMaximumRowCount(5);
    JList list = myComponent.getPopup().getList();
    Dimension initialSize = myRenderer.getInitialCellSize();
    list.setFixedCellHeight(initialSize.height);
    list.setFixedCellWidth(initialSize.width);
  }

  @Override
  public int getSelectedItem() {
    return myComponent.getSelectedIndex();
  }

  @Override
  protected void addSelectionListener(ComboBox component, final SelectionListener<T> selectionListener) {
    component.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
          selectionListener.selected((T)itemEvent.getItem());
        }
      }
    });
  }

  @Override
  protected void setSelectedIndex(ComboBox component, int index) {
    component.setSelectedIndex(index);
  }

  @Override
  public void setData(@NotNull List<T> data) {
    super.setData(data);
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    for (Data item : data) {
      model.addElement(item);
    }
    myComponent.setModel(model);
  }
}
