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
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.List;

/**
 * Displays a list of loadable cells in a scrollable panel.
 */
public abstract class CellList<T extends CellWidget.Data> extends CellWidget<T, JBScrollPane> {
  public enum Orientation {
    HORIZONTAL(JList.HORIZONTAL_WRAP), VERTICAL(JList.VERTICAL_WRAP);

    public final int listWrap;

    Orientation(int listWrap) {
      this.listWrap = listWrap;
    }

    @NotNull
    public JBScrollPane createScrollPane() {
      return (this == VERTICAL) ? new JBScrollPane() : new JBScrollPane() {{
        final JScrollBar scrollBar = getHorizontalScrollBar();

        // We will handle mouse wheel scrolling ourselves.
        setWheelScrollingEnabled(false);
        addMouseWheelListener(new MouseAdapter() {
          @Override
          public void mouseWheelMoved(MouseWheelEvent evt) {
            int scrollAmount = evt.getScrollAmount() * evt.getWheelRotation() * scrollBar.getBlockIncrement();
            int position = Math.max(scrollBar.getMinimum(), Math.min(scrollBar.getMaximum(), scrollBar.getValue() + scrollAmount));
            scrollBar.setValue(position);
          }
        });
      }};
    }
  }

  @NotNull private final JBList myList;

  public CellList(Orientation orientation, String emptyText, CellRenderer.CellLoader<T> loader) {
    super(createComponent(orientation, emptyText), loader);
    myList = (JBList)myComponent.getViewport().getView();

    myList.setCellRenderer(myRenderer);
    Dimension cellSize = myRenderer.getInitialCellSize();
    myList.setFixedCellWidth(cellSize.width);
    myList.setFixedCellHeight(cellSize.height);
  }

  private static JBScrollPane createComponent(Orientation orientation, String emptyText) {
    JBList list = new JBList();
    list.setLayoutOrientation(orientation.listWrap);
    list.setExpandableItemsEnabled(false); // Turn this off, since the "preview" will cause all the thumbnails to be loaded.
    list.setVisibleRowCount(1);
    list.getEmptyText().setText(emptyText);

    JBScrollPane scrollPane = orientation.createScrollPane();
    scrollPane.setViewportView(list);
    return scrollPane;
  }

  public void setEmptyText(String text) {
    myList.getEmptyText().setText(text);
  }

  @Override
  public void setData(@NotNull List<T> data) {
    super.setData(data);
    DefaultListModel model = new DefaultListModel();
    model.ensureCapacity(data.size());
    for (T item : data) {
      model.addElement(item);
    }
    myList.setModel(model);

    if (data.size() == 0) {
      myList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    }
  }

  @Override
  public int getSelectedItem() {
    return myList.getSelectedIndex();
  }

  @Override
  protected void addSelectionListener(JBScrollPane component, final SelectionListener<T> selectionListener) {
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        if (!listSelectionEvent.getValueIsAdjusting()) {
          Object selectedValue = myList.getSelectedValue();
          if (selectedValue != null) {
            selectionListener.selected((T)selectedValue);
          }
        }
      }
    });
  }

  @Override
  protected void setSelectedIndex(JBScrollPane component, int index) {
    myList.setSelectedIndex(index);
    myList.scrollRectToVisible(myList.getCellBounds(index, index));
  }
}
