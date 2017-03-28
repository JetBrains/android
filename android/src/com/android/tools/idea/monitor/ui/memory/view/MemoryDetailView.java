/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.monitor.ui.memory.view;

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.memory.model.AllocationTrackingSample;
import com.android.tools.idea.monitor.ui.memory.model.MemoryDataCache;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This represents the detail view of the memory monitor. This is responsible for managing the histogram or hprof views in response to user
 * actions.
 */
class MemoryDetailView implements Disposable {
  @NotNull private final JPanel myParent;
  @NotNull private final SeriesDataStore myDataStore;
  @NotNull private final Range myRange;
  @NotNull private final Choreographer myChoreographer;
  @NotNull private final EventDispatcher<ProfilerEventListener> myProfilerEventDispatcher;
  @NotNull private final List<HeapDumpInfoEntryFormatter> myHeapDumpEntries = new ArrayList<>();

  @Nullable
  private ClassHistogramView myClassHistogramView = null;

  @Nullable
  private MemoryDataCache myDataCache = null;

  @NotNull private ComboBox<AbstractInfoEntryFormatter> myMainSampleSelector;
  @NotNull private ComboBox<AbstractInfoEntryFormatter> myDiffSampleSelector;

  @NotNull private AbstractInfoEntryFormatter myCurrentMainEntry;
  @NotNull private AbstractInfoEntryFormatter myCurrentDiffEntry;

  // Default label for myMainSampleSelector.
  private final AbstractInfoEntryFormatter DEFAULT_MAIN_ENTRY =
    new AbstractInfoEntryFormatter() {
      @Override
      public String toString() {
        return myMainSampleSelector.getModel().getSize() == 1 && myMainSampleSelector.getSelectedItem() == this
               ? "No sample to display"
               : "Select a sample";
      }
    };

  // Default label for myDiffSampleSelector.
  private final AbstractInfoEntryFormatter DEFAULT_DIFF_ENTRY =
    new AbstractInfoEntryFormatter() {
      @Override
      public String toString() {
        if (myDiffSampleSelector.getSelectedItem() == null) {
          return "";
        }
        else if (myMainSampleSelector.getSelectedItem() == DEFAULT_MAIN_ENTRY) {
          if (myMainSampleSelector.getModel().getSize() > 1) {
            return "<-- Select a sample";
          }
          else {
            return "No sample to display";
          }
        }
        else if (myMainSampleSelector.getSelectedItem() instanceof HeapDumpInfoEntryFormatter) {
          if (myDiffSampleSelector.getModel().getSize() == 1) {
            return "No sample to diff against";
          }
          else if (myDiffSampleSelector.getSelectedItem() == this) {
            return "Select heap dump to diff";
          }
          else {
            return "Select this to analyze heap dump";
          }
        }
        return "Viewing allocations";
      }
    };

  MemoryDetailView(@NotNull JPanel parent,
                   @NotNull SeriesDataStore dataStore,
                   @Nullable MemoryDataCache dataCache,
                   @NotNull Range range,
                   @NotNull Choreographer choreographer,
                   @NotNull EventDispatcher<ProfilerEventListener> profilerEventDispatcher,
                   @NotNull EventDispatcher<MemoryProfilerUiManager.MemoryEventListener> memoryEventDispatcher) {
    myParent = parent;
    myDataStore = dataStore;
    myRange = range;
    myChoreographer = choreographer;
    myProfilerEventDispatcher = profilerEventDispatcher;

    memoryEventDispatcher.addListener(new MemoryProfilerUiManager.MemoryEventListener() {
      @Override
      public void newHeapDumpInfosRetrieved(HeapDumpInfo newInfo) {
        // Update the UI from the EDT thread.
        UIUtil.invokeLaterIfNeeded(() -> {
          HeapDumpInfoEntryFormatter entry = addHeapDumpInfo(newInfo);
          if (myMainSampleSelector.getSelectedItem() == DEFAULT_MAIN_ENTRY) {
            myMainSampleSelector.setSelectedItem(entry);
          }
        });
      }

      @Override
      public void newAllocationTrackingInfosRetrieved(AllocationTrackingSample newInfo) {
        UIUtil.invokeLaterIfNeeded(() -> {
          AllocationTrackingInfoEntryFormatter entry = addAllocationTrackingSample(newInfo);
          if (myMainSampleSelector.getSelectedItem() == DEFAULT_MAIN_ENTRY) {
            myMainSampleSelector.setSelectedItem(entry);
          }
        });
      }
    }, this);

    JPanel toolbar = new JBPanel(new HorizontalLayout(JBUI.scale(5)));
    myMainSampleSelector = new ComboBox<>();
    myMainSampleSelector.addItem(DEFAULT_MAIN_ENTRY);
    myMainSampleSelector.setSelectedItem(DEFAULT_MAIN_ENTRY);
    myCurrentMainEntry = DEFAULT_MAIN_ENTRY;

    myDiffSampleSelector = new ComboBox<>();
    myDiffSampleSelector.addItem(DEFAULT_DIFF_ENTRY);
    myDiffSampleSelector.setSelectedItem(DEFAULT_DIFF_ENTRY);
    myCurrentDiffEntry = DEFAULT_DIFF_ENTRY;

    toolbar.add(HorizontalLayout.LEFT, myMainSampleSelector);
    toolbar.add(HorizontalLayout.LEFT, myDiffSampleSelector);
    myParent.add(toolbar, BorderLayout.NORTH);

    if (dataCache != null) {
      notifyDataIsReady(dataCache);
    }
  }

  void notifyDataIsReady(@NotNull MemoryDataCache dataCache) {
    if (myDataCache == dataCache) {
      return;
    }

    myMainSampleSelector.addActionListener(e -> {
      if (myMainSampleSelector.getSelectedItem() == null) {
        return; // Swing deselection event before new selection
      }

      assert myMainSampleSelector.getSelectedItem() instanceof AbstractInfoEntryFormatter;
      AbstractInfoEntryFormatter selectedEntry = (AbstractInfoEntryFormatter)myMainSampleSelector.getSelectedItem();
      updateOnMainEntrySelection(dataCache, selectedEntry);
    });

    myDiffSampleSelector.addActionListener(e -> {
      if (myDiffSampleSelector.getSelectedItem() == null) {
        return; // Swing deselection event before new selection
      }

      assert myDiffSampleSelector.getSelectedItem() instanceof AbstractInfoEntryFormatter;
      AbstractInfoEntryFormatter selectedEntry = (AbstractInfoEntryFormatter)myDiffSampleSelector.getSelectedItem();
      updateOnDiffSelection(dataCache, selectedEntry);
    });

    dataCache.executeOnHeapDumpData((info, file) -> addHeapDumpInfo(info));
    dataCache.executeOnAllocationTrackingSamples(this::addAllocationTrackingSample);

    myDataCache = dataCache;
  }

  private void updateOnMainEntrySelection(@NotNull MemoryDataCache dataCache,
                                          @NotNull AbstractInfoEntryFormatter selectedMainEntry) {
    if (myCurrentMainEntry == selectedMainEntry) {
      return;
    }

    myCurrentMainEntry = selectedMainEntry;

    if (myCurrentMainEntry != DEFAULT_MAIN_ENTRY) {
      myMainSampleSelector.removeItem(DEFAULT_MAIN_ENTRY);
    }

    if (myClassHistogramView == null) {
      myClassHistogramView = new ClassHistogramView(this, myParent, myRange, myChoreographer, myProfilerEventDispatcher);
    }

    if (myCurrentMainEntry instanceof AllocationTrackingInfoEntryFormatter) {
      // Clear the diff combo box and change it to the default.
      myDiffSampleSelector.removeAllItems();
      myDiffSampleSelector.addItem(DEFAULT_DIFF_ENTRY);
      myDiffSampleSelector.setSelectedItem(DEFAULT_DIFF_ENTRY);
      myCurrentDiffEntry = DEFAULT_DIFF_ENTRY;

      AllocationTrackingInfoEntryFormatter selectedSample = (AllocationTrackingInfoEntryFormatter)myCurrentMainEntry;
      if (!myClassHistogramView.generateClassHistogramFromAllocationTracking(selectedSample.getSample())) {
        myMainSampleSelector.removeItem(myCurrentMainEntry);
        myMainSampleSelector.insertItemAt(DEFAULT_MAIN_ENTRY, 0);
        myMainSampleSelector.setSelectedItem(DEFAULT_MAIN_ENTRY);
        myCurrentMainEntry = DEFAULT_MAIN_ENTRY;
      }
    }
    else if (myCurrentMainEntry instanceof HeapDumpInfoEntryFormatter) {
      HeapDumpInfoEntryFormatter selectedHeapDumpEntry = (HeapDumpInfoEntryFormatter)myCurrentMainEntry;
      // Repopulate the diff combo box.
      myDiffSampleSelector.removeAllItems();
      myDiffSampleSelector.addItem(DEFAULT_DIFF_ENTRY);

      myHeapDumpEntries.forEach(entry -> {
        if (entry != selectedHeapDumpEntry) {
          myDiffSampleSelector.addItem(entry);
        }
      });

      HeapDumpInfoEntryFormatter selectedDiffHeapDump =
        myCurrentDiffEntry instanceof HeapDumpInfoEntryFormatter && myCurrentDiffEntry != selectedHeapDumpEntry
        ? (HeapDumpInfoEntryFormatter)myCurrentDiffEntry
        : null;
      myCurrentDiffEntry = selectedDiffHeapDump == null ? DEFAULT_DIFF_ENTRY : selectedDiffHeapDump;
      myDiffSampleSelector.setSelectedItem(myCurrentDiffEntry);

      myClassHistogramView.generateClassHistogramFromHeapDumpInfos(dataCache, selectedHeapDumpEntry.getInfo(),
                                                                     selectedDiffHeapDump == null
                                                                     ? null
                                                                     : selectedDiffHeapDump.getInfo());
    }
  }

  private void updateOnDiffSelection(@NotNull MemoryDataCache dataCache,
                                     @NotNull AbstractInfoEntryFormatter selectedDiffEntry) {
    if (myCurrentDiffEntry == selectedDiffEntry) {
      return;
    }

    myCurrentDiffEntry = selectedDiffEntry;

    if (myClassHistogramView == null) {
      myClassHistogramView = new ClassHistogramView(this, myParent, myRange, myChoreographer, myProfilerEventDispatcher);
    }

    if (myCurrentDiffEntry == DEFAULT_DIFF_ENTRY) {
      if (myCurrentMainEntry instanceof HeapDumpInfoEntryFormatter) {
        myClassHistogramView
          .generateClassHistogramFromHeapDumpInfos(dataCache, ((HeapDumpInfoEntryFormatter)myCurrentMainEntry).getInfo(), null);
      }
    }
    else {
      assert myCurrentMainEntry instanceof HeapDumpInfoEntryFormatter;
      assert myCurrentDiffEntry instanceof HeapDumpInfoEntryFormatter;
      myClassHistogramView
        .generateClassHistogramFromHeapDumpInfos(dataCache, ((HeapDumpInfoEntryFormatter)myCurrentMainEntry).getInfo(),
                                                 ((HeapDumpInfoEntryFormatter)myCurrentDiffEntry).getInfo());
    }
  }

  @NotNull
  private HeapDumpInfoEntryFormatter addHeapDumpInfo(@NotNull HeapDumpInfo info) {
    HeapDumpInfoEntryFormatter infoFormatter = new HeapDumpInfoEntryFormatter(info);
    myHeapDumpEntries.add(infoFormatter);

    myMainSampleSelector.addItem(infoFormatter);
    if (myCurrentMainEntry instanceof HeapDumpInfoEntryFormatter) {
      myDiffSampleSelector.addItem(infoFormatter);
      if (myCurrentDiffEntry == DEFAULT_DIFF_ENTRY) {
        myDiffSampleSelector.setSelectedItem(infoFormatter);
      }
    }

    return infoFormatter;
  }

  @NotNull
  private AllocationTrackingInfoEntryFormatter addAllocationTrackingSample(@NotNull AllocationTrackingSample sample) {
    AllocationTrackingInfoEntryFormatter formatter = new AllocationTrackingInfoEntryFormatter(sample);
    myMainSampleSelector.addItem(formatter);

    return formatter;
  }

  @Override
  public void dispose() {
    myMainSampleSelector.removeAllItems();
    myDiffSampleSelector.removeAllItems();
    myClassHistogramView = null;
    myParent.removeAll();
  }

  private static class AbstractInfoEntryFormatter {
    @Override
    public String toString() {
      return null;
    }
  }

  private class HeapDumpInfoEntryFormatter extends AbstractInfoEntryFormatter {
    private final HeapDumpInfo myInfo;

    public HeapDumpInfoEntryFormatter(@NotNull HeapDumpInfo info) {
      myInfo = info;
    }

    @NotNull
    public HeapDumpInfo getInfo() {
      return myInfo;
    }

    @Override
    public String toString() {
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(myDataStore.mapAbsoluteDeviceToRelativeTime(myInfo.getStartTime()));
      return String.format("Heap at %s", TimeAxisFormatter.DEFAULT.getFormattedString((double)startTimeUs, (double)startTimeUs, true));
    }
  }

  private static class AllocationTrackingInfoEntryFormatter extends AbstractInfoEntryFormatter {
    private final AllocationTrackingSample mySample;

    public AllocationTrackingInfoEntryFormatter(@NotNull AllocationTrackingSample sample) {
      mySample = sample;
    }

    @NotNull
    public AllocationTrackingSample getSample() {
      return mySample;
    }

    @Override
    public String toString() {
      double startTimeUs = (double)mySample.getStartTime();
      double endTimeUs = (double)mySample.getEndTime();
      return "Allocations from " +
             TimeAxisFormatter.DEFAULT.getFormattedString(endTimeUs, startTimeUs, true) +
             " to " +
             TimeAxisFormatter.DEFAULT.getFormattedString(endTimeUs, endTimeUs, true);
    }
  }
}
