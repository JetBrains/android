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

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.google.common.collect.ImmutableList;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Displays a list of loadable cells in a scrollable panel.
 */
public abstract class CellList<T extends CellList.Data> extends JPanel {
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

  public static class Data implements LoadingCallback.LoadingDone {
    private LoadingState loadingState = LoadingState.NOT_LOADED;

    public boolean isSelected = false;

    public boolean requiresLoading() {
      return loadingState == LoadingState.NOT_LOADED;
    }

    public boolean isLoading() {
      return loadingState == LoadingState.LOADING;
    }

    public boolean isLoaded() {
      return loadingState == LoadingState.LOADED;
    }

    public void startLoading() {
      loadingState = LoadingState.LOADING;
    }

    @Override
    public void stopLoading() {
      loadingState = LoadingState.LOADED;
    }

    private enum LoadingState {
      NOT_LOADED, LOADING, LOADED;
    }
  }

  @NotNull private final JBScrollPane myScrollPane;
  @NotNull private final JBList myList = new JBList();
  @NotNull private final CellRenderer<T> myRenderer;
  @NotNull private List<T> myData = Collections.emptyList();
  @NotNull private AtomicBoolean myFireSelectionEvents = new AtomicBoolean(true);

  public CellList(Orientation orientation, CellRenderer.CellLoader<T> loader) {
    super(new BorderLayout());
    myScrollPane = orientation.createScrollPane();
    myRenderer = createCellRenderer(loader);

    myList.setLayoutOrientation(orientation.listWrap);
    myScrollPane.setViewportView(myList);
    myList.setExpandableItemsEnabled(false); // Turn this off, since the "preview" will cause all the thumbnails to be loaded.
    myList.setVisibleRowCount(1);
    myList.getEmptyText().setText(GfxTraceEditor.SELECT_ATOM);
    Dimension cellSize = myRenderer.getInitialCellSize();
    myList.setFixedCellWidth(cellSize.width);
    myList.setFixedCellHeight(cellSize.height);
    add(myScrollPane, BorderLayout.CENTER);
  }

  protected abstract CellRenderer<T> createCellRenderer(CellRenderer.CellLoader<T> loader);

  public void addSelectionListener(final SelectionListener listener) {
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        if (myFireSelectionEvents.get() && !listSelectionEvent.getValueIsAdjusting()) {
          Object selectedValue = myList.getSelectedValue();
          if (selectedValue != null) {
            listener.selected((T)selectedValue);
          }
        }
      }
    });
  }

  public Iterable<T> items() {
    return myData;
  }

  public void setData(@NotNull List<T> data) {
    myData = data;
    DefaultListModel model = new DefaultListModel();
    model.ensureCapacity(myData.size());
    for (T item : myData) {
      model.addElement(item);
    }
    myList.setModel(model);

    if (myData.size() == 0) {
      myList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    }

    myList.setCellRenderer(myRenderer);
  }

  public void selectItem(int index, boolean fireEvents) {
    boolean previousValue = myFireSelectionEvents.getAndSet(fireEvents);
    try {
      myList.setSelectedIndex(index);
    } finally {
      myFireSelectionEvents.set(previousValue);
    }
    myList.scrollRectToVisible(myList.getCellBounds(index, index));
  }

  public interface SelectionListener<T> {
    void selected(T item);
  }
}
