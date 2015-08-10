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
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.ScrubberLabelData;
import com.android.tools.idea.editors.gfxtrace.renderers.ScrubberCellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.atom.Range;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ScrubberController implements PathListener {
  @NotNull private static final Logger LOG = Logger.getInstance(ScrubberController.class);
  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final JBScrollPane myPane;
  @NotNull private final JBList myList;
  @NotNull private ScrubberCellRenderer myScrubberCellRenderer;
  @Nullable private List<ScrubberLabelData> myFrameData;
  @Nullable private DevicePath myRenderDevice;
  @Nullable private CapturePath myCapture;

  public ScrubberController(@NotNull GfxTraceEditor editor, @NotNull JBScrollPane scrubberScrollPane, @NotNull JBList scrubberComponent) {
    myEditor = editor;
    myEditor.addPathListener(this);
    myPane = scrubberScrollPane;
    myList = scrubberComponent;
    myScrubberCellRenderer = new ScrubberCellRenderer(this);

    Dimension minCellDimension = myScrubberCellRenderer.getCellDimensions();

    myList.setExpandableItemsEnabled(false); // Turn this off, since the "preview" will cause all the thumbnails to be loaded.
    myList.setMinimumSize(minCellDimension);
    myList.setVisibleRowCount(1);

    myList.getEmptyText().setText(GfxTraceEditor.SELECT_CAPTURE);

    resize(minCellDimension);
    myList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
      if (!listSelectionEvent.getValueIsAdjusting()) {
        Object selectedValue = myList.getSelectedValue();
        if ((myCapture != null) && (selectedValue != null)) {
          ScrubberLabelData labelData = (ScrubberLabelData)selectedValue;
          long last = labelData.getRange().getLast();
          myEditor.activatePath(myCapture.atoms().index(last));
        }
      }
      }
    });
  }

  @Nullable
  public List<ScrubberLabelData> prepareData(@NotNull AtomsPath path, @NotNull AtomList atoms) {
    List<ScrubberLabelData> generatedList = new ArrayList<ScrubberLabelData>();
    int frameCount = 0;
    Range range = new Range();
    range.setStart(0);
    for (int index = 0; index < atoms.getAtoms().length; ++index) {
      Atom atom = atoms.get(index);
      if (atom.getIsEndOfFrame()) {
        range.setEnd(index);
        ScrubberLabelData frameData =
          new ScrubberLabelData(path.index(index), range, Integer.toString(frameCount++), myScrubberCellRenderer.getDefaultIcon());
        generatedList.add(frameData);
        range = new Range();
        range.setStart(index);;
      }
    }
    LOG.warn("Found " + generatedList.size() + " frames for scrubber");
    return generatedList;
  }

  public void notifyDimensionChanged(@NotNull Dimension newDimension) {
    resize(newDimension);
    myEditor.notifyDimensionChanged(newDimension);
  }

  public void populateUi(@NotNull List<ScrubberLabelData> cells) {
    myFrameData = cells;

    DefaultListModel model = new DefaultListModel();
    model.ensureCapacity(myFrameData.size());
    for (ScrubberLabelData data : myFrameData) {
      model.addElement(data);
    }
    myList.setModel(model);

    if (myFrameData.size() == 0) {
      myList.getEmptyText().setText(StatusText.DEFAULT_EMPTY_TEXT);
    }

    myList.setCellRenderer(myScrubberCellRenderer);
  }

  public void selectFrame(long atomIndex) {
    if (myFrameData != null) {
      for (int i = 0; i < myFrameData.size(); ++i) {
        Range range = myFrameData.get(i).getRange();
        if (atomIndex >= range.getStart() && atomIndex < range.getEnd()) {
          myList.setSelectedIndex(i);
          myList.scrollRectToVisible(myList.getCellBounds(i, i));
          break;
        }
      }
    }
  }

  public void clear() {
    myList.setModel(new DefaultListModel());
    myList.setCellRenderer(new DefaultListCellRenderer());
  }

  private void resize(@NotNull Dimension newDimensions) {
    myList.setFixedCellWidth(newDimensions.width);
    myList.setFixedCellHeight(newDimensions.height);

    newDimensions.height += myPane.getHorizontalScrollBar().getUI().getPreferredSize(myPane).height;
    myPane.setMinimumSize(newDimensions);
  }

  @Override
  public void notifyPath(Path path) {
    boolean updateIcons = false;
    if (path instanceof DevicePath) {
      myRenderDevice = (DevicePath)path;
      updateIcons = true;
    }
    if (path instanceof CapturePath) {
      myCapture = (CapturePath)path;
      updateIcons = true;
    }
    if (path instanceof AtomPath) {
      AtomPath atomPath = (AtomPath)path;
      selectFrame(atomPath.getIndex());
    }
    if (updateIcons) {
      Futures.addCallback(myEditor.getClient().get(myCapture.atoms()), new LoadingCallback<AtomList>(LOG) {
        @Override
        public void onSuccess(@Nullable final AtomList atoms) {
          final List<ScrubberLabelData> cells = prepareData(myCapture.atoms(), atoms);
          EdtExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
              // Back in the UI thread here
              populateUi(cells);
            }
          });
        }
      });
    }
  }

  public ServiceClient getClient() {
    return myEditor.getClient();
  }

  public DevicePath getRenderDevice() {
    return myRenderDevice;
  }

  public boolean isLoading() {
    if (myFrameData == null) {
      return false;
    }
    for(ScrubberLabelData labelData : myFrameData) {
      if (labelData.isLoading()) {
        return true;
      }
    }
    return false;
  }
}
