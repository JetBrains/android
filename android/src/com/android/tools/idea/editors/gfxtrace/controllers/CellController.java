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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.util.List;

public abstract class CellController<T extends CellController.Data> extends Controller {
  public static class Data implements LoadingCallback.LoadingDone {
    @NotNull public final String label;
    @NotNull public ImageIcon icon;
    public long loadstartTime = 0;
    public boolean isLoading = false;
    public boolean isSelected = false;

    public Data(@NotNull String label, @NotNull ImageIcon icon) {
      this.label = label;
      this.icon = icon;
    }

    @Override
    public void stopLoading() {
      isLoading = false;
    }

    public boolean isLoaded() {
      return !isLoading && (loadstartTime != 0);
    }
  }

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

  @NotNull private static final Logger LOG = Logger.getInstance(CellController.class);
  @NotNull protected final JBScrollPane myScrollPane;
  @NotNull protected final JBList myList = new JBList();
  @NotNull protected final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull protected final CellRenderer myRenderer;
  @Nullable protected List<T> myData;

  public CellController(@NotNull GfxTraceEditor editor, @NotNull Orientation orientation) {
    super(editor);
    myList.setLayoutOrientation(orientation.listWrap);
    myScrollPane = orientation.createScrollPane();
    myScrollPane.setViewportView(myList);
    myPanel.add(myScrollPane, BorderLayout.CENTER);
    myRenderer = new CellRenderer(this);
    Dimension minCellDimension = myRenderer.getCellDimensions();
    myList.setExpandableItemsEnabled(false); // Turn this off, since the "preview" will cause all the thumbnails to be loaded.
    myList.setMinimumSize(minCellDimension);
    myList.setVisibleRowCount(1);
    myList.getEmptyText().setText(GfxTraceEditor.SELECT_ATOM);
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        if (listSelectionEvent.getValueIsAdjusting()) return;
        Object selectedValue = myList.getSelectedValue();
        if (selectedValue == null) return;
        selected((T)selectedValue);
      }
    });
  }

  protected void populateUi(@NotNull List<T> cells) {
    myData = cells;

    DefaultListModel model = new DefaultListModel();
    model.ensureCapacity(myData.size());
    for (T data : myData) {
      model.addElement(data);
    }
    myList.setModel(model);

    if (myData.size() == 0) {
      myList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    }

    myList.setCellRenderer(myRenderer);
    resize(myRenderer.getCellDimensions());
  }

  abstract void selected(@NotNull T cell);

  abstract boolean loadCell(@NotNull T cell);

  public boolean startLoad(@NotNull CellController.Data cell) {
    return loadCell((T)cell);
  }

  protected void loadCellImage(final Data cell, final ServiceClient client, final Path imagePath) {
    Futures.addCallback(FetchedImage.load(client, imagePath), new LoadingCallback<FetchedImage>(LOG, cell) {
      @Override
      public void onSuccess(final FetchedImage fetchedImage) {
        EdtExecutor.INSTANCE.execute(new Runnable() {
          @Override
          public void run() {
            // Back in the UI thread here
            cell.icon = fetchedImage.icon;
            cell.stopLoading();
            if (myRenderer.updateKnownSize(fetchedImage.dimensions)) {
              resize(myRenderer.getCellDimensions());
            }
            myList.repaint();
          }
        });
      }
    });
  }

  public void selectItem(int index) {
    myList.setSelectedIndex(index);
    myList.scrollRectToVisible(myList.getCellBounds(index, index));
  }

  @Override
  public void clear() {
    myList.setModel(new DefaultListModel());
    myList.setCellRenderer(new DefaultListCellRenderer());
  }

  protected void resize(@NotNull Dimension newDimensions) {
    myList.setFixedCellWidth(newDimensions.width);
    myList.setFixedCellHeight(newDimensions.height);
    if (myList.getLayoutOrientation() == JList.HORIZONTAL_WRAP) {
      newDimensions.height += myScrollPane.getHorizontalScrollBar().getUI().getPreferredSize(myScrollPane).height;
    }
    else {
      newDimensions.width += myScrollPane.getHorizontalScrollBar().getUI().getPreferredSize(myScrollPane).width;
    }
    myScrollPane.setMinimumSize(newDimensions);
  }
}
