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

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.ScrubberLabelData;
import com.android.tools.idea.editors.gfxtrace.renderers.ScrubberCellRenderer;
import com.android.tools.idea.editors.gfxtrace.rpc.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.rpc.Client;
import com.android.tools.idea.editors.gfxtrace.rpc.Hierarchy;
import com.android.tools.idea.editors.gfxtrace.schema.AtomReader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScrubberController implements ScrubberCellRenderer.DimensionChangeListener, GfxController {
  @NotNull private static final Logger LOG = Logger.getInstance(ScrubberController.class);
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBScrollPane myPane;
  @NotNull private final JBList myList;
  @NotNull private ScrubberCellRenderer myScrubberCellRenderer;
  @Nullable private List<ScrubberLabelData> myFrameData;

  public ScrubberController(@NotNull GfxTraceEditor editor, @NotNull JBScrollPane scrubberScrollPane, @NotNull JBList scrubberComponent) {
    myEditor = editor;

    myPane = scrubberScrollPane;
    myList = scrubberComponent;
    myScrubberCellRenderer = new ScrubberCellRenderer();
    myScrubberCellRenderer.addDimensionChangeListener(this);

    Dimension minCellDimension = myScrubberCellRenderer.getCellDimensions();

    myList.setExpandableItemsEnabled(false); // Turn this off, since the "preview" will cause all the thumbnails to be loaded.
    myList.setMinimumSize(minCellDimension);
    myList.setVisibleRowCount(1);

    myList.getEmptyText().setText(SELECT_CAPTURE);

    resize(minCellDimension);
  }

  @Nullable
  public List<ScrubberLabelData> prepareData(@NotNull Hierarchy hierarchy, @NotNull AtomReader atomReader) {
    try {
      AtomGroup root = hierarchy.getRoot();
      List<ScrubberLabelData> generatedList = new ArrayList<ScrubberLabelData>(root.getSubGroups().length);
      int frameCount = 0;
      for (AtomGroup frame : root.getSubGroups()) {
        assert (frame.getRange().getCount() > 0);
        long atomId = frame.getRange().getFirst() + frame.getRange().getCount() - 1l;
        if (atomReader.read(atomId).info.getIsEndOfFrame()) {
          ScrubberLabelData frameData =
            new ScrubberLabelData(atomId, frame, Integer.toString(frameCount++), myScrubberCellRenderer.getDefaultIcon());
          generatedList.add(frameData);
        }
      }
      return generatedList;
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  public void notifyDimensionChanged(@NotNull Dimension newDimension) {
    resize(newDimension);
    myEditor.notifyDimensionChanged(newDimension);
  }

  @Override
  public void startLoad() {
    myList.getEmptyText().setText("");
  }

  @Override
  public void commitData(@NotNull GfxContextChangeState state) {
    myFrameData = state.myScrubberList;
  }

  public void populateUi(@NotNull Client client) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert (myFrameData != null);
    assert (myEditor.getContext() != null);
    assert (myEditor.getCaptureId() != null);

    if (myEditor.getDeviceId() == null) {
      // If there is no device selected, don't do anything.
      return;
    }

    DefaultListModel model = new DefaultListModel();
    model.ensureCapacity(myFrameData.size());
    for (ScrubberLabelData data : myFrameData) {
      model.addElement(data);
    }
    setModel(model);

    if (myFrameData.size() == 0) {
      myList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    }

    ImageFetcher imageFetcher = new ImageFetcher(client);
    imageFetcher.prepareFetch(myEditor.getDeviceId(), myEditor.getCaptureId(), myEditor.getContext());
    myScrubberCellRenderer.setup(imageFetcher);
    myList.setCellRenderer(myScrubberCellRenderer);
  }

  @Nullable
  public AtomGroup getFrameSelectionReference() {
    ScrubberLabelData data = getSelectedLabelData();
    if (data == null) {
      return null;
    }

    return data.getHierarchyReference();
  }

  public void selectFrame(long atomId) {
    assert (myFrameData != null);

    int i = 0;
    for (ScrubberLabelData data : myFrameData) {
      AtomGroup group = data.getHierarchyReference();
      if (atomId >= group.getRange().getFirst() && atomId < group.getRange().getFirst() + group.getRange().getCount()) {
        select(i);
        break;
      }
      ++i;
    }
  }

  @Override
  public void clear() {
    myScrubberCellRenderer.clearState();
    myList.setModel(new DefaultListModel());
    myList.setCellRenderer(new DefaultListCellRenderer());
  }

  @Override
  public void clearCache() {
    myScrubberCellRenderer.clearCache();
    myList.clearSelection();
  }

  private void resize(@NotNull Dimension newDimensions) {
    myList.setFixedCellWidth(newDimensions.width);
    myList.setFixedCellHeight(newDimensions.height);

    newDimensions.height += myPane.getHorizontalScrollBar().getUI().getPreferredSize(myPane).height;
    myPane.setMinimumSize(newDimensions);
  }

  private void setModel(@NotNull ListModel model) {
    myList.setModel(model);
  }

  @Nullable
  private ScrubberLabelData getSelectedLabelData() {
    Object selectedValue = myList.getSelectedValue();
    if (selectedValue != null) {
      return (ScrubberLabelData)selectedValue;
    }
    return null;
  }

  private void select(int index) {
    myList.setSelectedIndex(index);
    myList.scrollRectToVisible(myList.getCellBounds(index, index));
  }
}
