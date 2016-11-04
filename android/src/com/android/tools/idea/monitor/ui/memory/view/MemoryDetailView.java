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
import com.android.tools.profiler.proto.MemoryProfiler;
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
  @NotNull private final List<HeapDumpSampleEntryFormatter> myHeapDumpEntries = new ArrayList<>();

  @Nullable
  private ClassHistogramView myClassHistogramView = null;

  @Nullable
  private MemoryDataCache myDataCache = null;

  @NotNull private ComboBox<AbstractSampleEntryFormatter> myMainSampleSelector;
  @NotNull private ComboBox<AbstractSampleEntryFormatter> myDiffSampleSelector;

  @NotNull private AbstractSampleEntryFormatter myCurrentMainEntry;
  @NotNull private AbstractSampleEntryFormatter myCurrentDiffEntry;

  // Default label for myMainSampleSelector.
  private final AbstractSampleEntryFormatter DEFAULT_MAIN_ENTRY =
    new AbstractSampleEntryFormatter() {
      @Override
      public String toString() {
        return myMainSampleSelector.getModel().getSize() == 1 && myMainSampleSelector.getSelectedItem() == this
               ? "No sample to display"
               : "Select a sample";
      }
    };

  // Default label for myDiffSampleSelector.
  private final AbstractSampleEntryFormatter DEFAULT_DIFF_ENTRY =
    new AbstractSampleEntryFormatter() {
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
        else if (myMainSampleSelector.getSelectedItem() instanceof HeapDumpSampleEntryFormatter) {
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
      public void newHeapDumpSamplesRetrieved(MemoryProfiler.MemoryData.HeapDumpSample newSample) {
        // Update the UI from the EDT thread.
        UIUtil.invokeLaterIfNeeded(() -> {
          HeapDumpSampleEntryFormatter entry = addHeapDumpSample(newSample);
          if (myMainSampleSelector.getSelectedItem() == DEFAULT_MAIN_ENTRY) {
            myMainSampleSelector.setSelectedItem(entry);
          }
        });
      }

      @Override
      public void newAllocationTrackingSampleRetrieved(AllocationTrackingSample newSample) {
        UIUtil.invokeLaterIfNeeded(() -> {
          AllocationTrackingSampleEntryFormatter entry = addAllocationTrackingSample(newSample);
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

      assert myMainSampleSelector.getSelectedItem() instanceof AbstractSampleEntryFormatter;
      AbstractSampleEntryFormatter selectedEntry = (AbstractSampleEntryFormatter)myMainSampleSelector.getSelectedItem();
      updateOnMainEntrySelection(dataCache, selectedEntry);
    });

    myDiffSampleSelector.addActionListener(e -> {
      if (myDiffSampleSelector.getSelectedItem() == null) {
        return; // Swing deselection event before new selection
      }

      assert myDiffSampleSelector.getSelectedItem() instanceof AbstractSampleEntryFormatter;
      AbstractSampleEntryFormatter selectedEntry = (AbstractSampleEntryFormatter)myDiffSampleSelector.getSelectedItem();
      updateOnDiffSelection(dataCache, selectedEntry);
    });

    dataCache.executeOnHeapDumpData((sample, file) -> addHeapDumpSample(sample));
    dataCache.executeOnAllocationTrackingSamples(this::addAllocationTrackingSample);

    myDataCache = dataCache;
  }

  private void updateOnMainEntrySelection(@NotNull MemoryDataCache dataCache,
                                          @NotNull AbstractSampleEntryFormatter selectedMainEntry) {
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

    if (myCurrentMainEntry instanceof AllocationTrackingSampleEntryFormatter) {
      // Clear the diff combo box and change it to the default.
      myDiffSampleSelector.removeAllItems();
      myDiffSampleSelector.addItem(DEFAULT_DIFF_ENTRY);
      myDiffSampleSelector.setSelectedItem(DEFAULT_DIFF_ENTRY);
      myCurrentDiffEntry = DEFAULT_DIFF_ENTRY;

      AllocationTrackingSampleEntryFormatter selectedSample = (AllocationTrackingSampleEntryFormatter)myCurrentMainEntry;
      if (!myClassHistogramView.generateClassHistogramFromAllocationTracking(selectedSample.getSample())) {
        myMainSampleSelector.removeItem(myCurrentMainEntry);
        myMainSampleSelector.insertItemAt(DEFAULT_MAIN_ENTRY, 0);
        myMainSampleSelector.setSelectedItem(DEFAULT_MAIN_ENTRY);
        myCurrentMainEntry = DEFAULT_MAIN_ENTRY;
      }
    }
    else if (myCurrentMainEntry instanceof HeapDumpSampleEntryFormatter) {
      HeapDumpSampleEntryFormatter selectedHeapDumpEntry = (HeapDumpSampleEntryFormatter)myCurrentMainEntry;
      // Repopulate the diff combo box.
      myDiffSampleSelector.removeAllItems();
      myDiffSampleSelector.addItem(DEFAULT_DIFF_ENTRY);

      myHeapDumpEntries.forEach(entry -> {
        if (entry != selectedHeapDumpEntry) {
          myDiffSampleSelector.addItem(entry);
        }
      });

      HeapDumpSampleEntryFormatter selectedDiffHeapDump =
        myCurrentDiffEntry instanceof HeapDumpSampleEntryFormatter && myCurrentDiffEntry != selectedHeapDumpEntry
        ? (HeapDumpSampleEntryFormatter)myCurrentDiffEntry
        : null;
      myCurrentDiffEntry = selectedDiffHeapDump == null ? DEFAULT_DIFF_ENTRY : selectedDiffHeapDump;
      myDiffSampleSelector.setSelectedItem(myCurrentDiffEntry);

      myClassHistogramView.generateClassHistogramFromHeapDumpSamples(dataCache, selectedHeapDumpEntry.getSample(),
                                                                     selectedDiffHeapDump == null
                                                                     ? null
                                                                     : selectedDiffHeapDump.getSample());
    }
  }

  private void updateOnDiffSelection(@NotNull MemoryDataCache dataCache,
                                     @NotNull AbstractSampleEntryFormatter selectedDiffEntry) {
    if (myCurrentDiffEntry == selectedDiffEntry) {
      return;
    }

    myCurrentDiffEntry = selectedDiffEntry;

    if (myClassHistogramView == null) {
      myClassHistogramView = new ClassHistogramView(this, myParent, myRange, myChoreographer, myProfilerEventDispatcher);
    }

    if (myCurrentDiffEntry == DEFAULT_DIFF_ENTRY) {
      if (myCurrentMainEntry instanceof HeapDumpSampleEntryFormatter) {
        myClassHistogramView
          .generateClassHistogramFromHeapDumpSamples(dataCache, ((HeapDumpSampleEntryFormatter)myCurrentMainEntry).getSample(), null);
      }
    }
    else {
      assert myCurrentMainEntry instanceof HeapDumpSampleEntryFormatter;
      assert myCurrentDiffEntry instanceof HeapDumpSampleEntryFormatter;
      myClassHistogramView
        .generateClassHistogramFromHeapDumpSamples(dataCache, ((HeapDumpSampleEntryFormatter)myCurrentMainEntry).getSample(),
                                                   ((HeapDumpSampleEntryFormatter)myCurrentDiffEntry).getSample());
    }
  }

  @NotNull
  private HeapDumpSampleEntryFormatter addHeapDumpSample(@NotNull MemoryProfiler.MemoryData.HeapDumpSample sample) {
    HeapDumpSampleEntryFormatter sampleFormatter = new HeapDumpSampleEntryFormatter(sample);
    myHeapDumpEntries.add(sampleFormatter);

    myMainSampleSelector.addItem(sampleFormatter);
    if (myCurrentMainEntry instanceof HeapDumpSampleEntryFormatter) {
      myDiffSampleSelector.addItem(sampleFormatter);
      if (myCurrentDiffEntry == DEFAULT_DIFF_ENTRY) {
        myDiffSampleSelector.setSelectedItem(sampleFormatter);
      }
    }

    return sampleFormatter;
  }

  @NotNull
  private AllocationTrackingSampleEntryFormatter addAllocationTrackingSample(@NotNull AllocationTrackingSample sample) {
    AllocationTrackingSampleEntryFormatter formatter = new AllocationTrackingSampleEntryFormatter(sample);
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

  private static class AbstractSampleEntryFormatter {
    @Override
    public String toString() {
      return null;
    }
  }

  private class HeapDumpSampleEntryFormatter extends AbstractSampleEntryFormatter {
    private final MemoryProfiler.MemoryData.HeapDumpSample mySample;

    public HeapDumpSampleEntryFormatter(@NotNull MemoryProfiler.MemoryData.HeapDumpSample sample) {
      mySample = sample;
    }

    @NotNull
    public MemoryProfiler.MemoryData.HeapDumpSample getSample() {
      return mySample;
    }

    @Override
    public String toString() {
      long startTimeUs = TimeUnit.NANOSECONDS.toMicros(myDataStore.mapAbsoluteDeviceToRelativeTime(mySample.getStartTime()));
      return String.format("Heap at %s", TimeAxisFormatter.DEFAULT.getFormattedString((double)startTimeUs, (double)startTimeUs, true));
    }
  }

  private static class AllocationTrackingSampleEntryFormatter extends AbstractSampleEntryFormatter {
    private final AllocationTrackingSample mySample;

    public AllocationTrackingSampleEntryFormatter(@NotNull AllocationTrackingSample sample) {
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
