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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.ScrubberFrameData;
import com.android.tools.idea.editors.gfxtrace.renderers.ScrubberCellRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.ScrubberLabel;
import com.android.tools.rpclib.rpc.AtomGroup;
import com.android.tools.rpclib.rpc.Client;
import com.android.tools.rpclib.rpc.Hierarchy;
import com.android.tools.rpclib.schema.AtomReader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
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
  @Nullable private List<ScrubberFrameData> myCachedLabelData;

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

    resize(minCellDimension);
  }

  @Nullable
  public static List<ScrubberFrameData> prepareData(@NotNull Hierarchy hierarchy, @NotNull AtomReader atomReader) {
    try {
      AtomGroup root = hierarchy.getRoot();
      List<ScrubberFrameData> generatedList = new ArrayList<ScrubberFrameData>(root.getSubGroups().length);
      int frameCount = 0;
      for (AtomGroup frame : root.getSubGroups()) {
        assert (frame.getRange().getCount() > 0);
        long atomId = frame.getRange().getFirst() + frame.getRange().getCount() - 1l;
        if (atomReader.read(atomId).info.getIsEndOfFrame()) {
          ScrubberFrameData frameData =
            new ScrubberFrameData().setAtomId(atomId).setHierarchyReference(frame).setLabel(Integer.toString(frameCount++));
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
  public void commitData(@NotNull GfxContextChangeState state) {
    myCachedLabelData = state.myScrubberList;
  }

  public void populateUi(@NotNull Client client) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert (myCachedLabelData != null);
    assert (myEditor.getContext() != null);
    assert (myEditor.getCaptureId() != null);

    if (myEditor.getDeviceId() == null) {
      // If there is no device selected, don't do anything.
      return;
    }

    DefaultListModel model = new DefaultListModel();
    model.ensureCapacity(myCachedLabelData.size());
    for (ScrubberFrameData data : myCachedLabelData) {
      model.addElement(getBlankLabel(data, false));
    }
    setModel(model);

    ImageFetcher imageFetcher = new ImageFetcher(client);
    imageFetcher.prepareFetch(myEditor.getDeviceId(), myEditor.getCaptureId(), myEditor.getContext());
    myScrubberCellRenderer.setup(imageFetcher);
    myList.setCellRenderer(myScrubberCellRenderer);
  }

  @Nullable
  public AtomGroup getFrameSelectionReference() {
    ScrubberFrameData data = getSelectedLabelData();
    if (data == null) {
      return null;
    }

    AtomGroup hierarchyGroup = data.getHierarchyReference();
    assert (hierarchyGroup != null);
    return hierarchyGroup;
  }

  public void selectFrame(long atomId) {
    assert (myCachedLabelData != null);

    int i = 0;
    for (ScrubberFrameData data : myCachedLabelData) {
      AtomGroup group = data.getHierarchyReference();
      if (group != null && atomId >= group.getRange().getFirst() && atomId < group.getRange().getFirst() + group.getRange().getCount()) {
        select(i);
        break;
      }
      ++i;
    }
  }

  @Override
  public void clear() {
    myScrubberCellRenderer.clear();
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

  @NotNull
  private JComponent getBlankLabel(@NotNull ScrubberFrameData data, boolean isSelected) {
    return myScrubberCellRenderer.getBlankLabel(data, isSelected);
  }

  private void setModel(@NotNull ListModel model) {
    myList.setModel(model);
  }

  @Nullable
  private ScrubberFrameData getSelectedLabelData() {
    Object selectedValue = myList.getSelectedValue();
    if (selectedValue != null) {
      return ((ScrubberLabel)selectedValue).getUserData();
    }
    return null;
  }

  private void select(int index) {
    myList.setSelectedIndex(index);
    myList.scrollRectToVisible(myList.getCellBounds(index, index));
  }
}
