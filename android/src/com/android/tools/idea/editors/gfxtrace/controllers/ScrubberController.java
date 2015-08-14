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
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ScrubberController implements ScrubberCellRenderer.DimensionChangeListener, PathListener {
  @NotNull private static final Logger LOG = Logger.getInstance(ScrubberController.class);
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBScrollPane myPane;
  @NotNull private final JBList myList;
  @NotNull private ScrubberCellRenderer myScrubberCellRenderer;
  @Nullable private List<ScrubberLabelData> myFrameData;

  public ScrubberController(@NotNull GfxTraceEditor editor, @NotNull JBScrollPane scrubberScrollPane, @NotNull JBList scrubberComponent) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myPane = scrubberScrollPane;
    myList = scrubberComponent;
    myScrubberCellRenderer = new ScrubberCellRenderer();
    myScrubberCellRenderer.addDimensionChangeListener(this);

    Dimension minCellDimension = myScrubberCellRenderer.getCellDimensions();

    myList.setExpandableItemsEnabled(false); // Turn this off, since the "preview" will cause all the thumbnails to be loaded.
    myList.setMinimumSize(minCellDimension);
    myList.setVisibleRowCount(1);

    myList.getEmptyText().setText(GfxTraceEditor.SELECT_CAPTURE);

    resize(minCellDimension);
  }

  @Nullable
  public List<ScrubberLabelData> prepareData(@NotNull AtomGroup root, @NotNull AtomList atoms) {
    List<ScrubberLabelData> generatedList = new ArrayList<ScrubberLabelData>(root.getSubGroups().length);
    int frameCount = 0;
    for (AtomGroup frame : root.getSubGroups()) {
      assert (frame.isValid());
      long atomIndex = frame.getRange().getEnd() - 1l;
      if (atoms.get(atomIndex).getIsEndOfFrame()) {
        // TODO: get an atom path from an atom index, needs a capture path from somewhere
        ScrubberLabelData frameData =
          new ScrubberLabelData(/*atomIndex*/null, frame, Integer.toString(frameCount++), myScrubberCellRenderer.getDefaultIcon());
        generatedList.add(frameData);
      }
    }
    return generatedList;
  }

  @Override
  public void notifyDimensionChanged(@NotNull Dimension newDimension) {
    resize(newDimension);
    myEditor.notifyDimensionChanged(newDimension);
  }

  public void populateUi(@NotNull ServiceClient client) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert (myFrameData != null);

    DefaultListModel model = new DefaultListModel();
    model.ensureCapacity(myFrameData.size());
    for (ScrubberLabelData data : myFrameData) {
      model.addElement(data);
    }
    setModel(model);

    if (myFrameData.size() == 0) {
      myList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    }

    myScrubberCellRenderer.setup(client);
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

  public void selectFrame(long atomIndex) {
    assert (myFrameData != null);

    int i = 0;
    for (ScrubberLabelData data : myFrameData) {
      AtomGroup group = data.getHierarchyReference();
      if (atomIndex >= group.getRange().getStart() && atomIndex < group.getRange().getEnd()) {
        select(i);
        break;
      }
      ++i;
    }
  }

  public void clear() {
    myScrubberCellRenderer.clearState();
    myList.setModel(new DefaultListModel());
    myList.setCellRenderer(new DefaultListCellRenderer());
  }

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

  @Override
  public void notifyPath(Path path) {
    // TODO: on capture change update the scrubber list
    myList.getEmptyText().setText("");
  }
}
