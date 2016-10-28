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

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.datastore.DataAdapter;
import com.android.tools.datastore.Poller;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.datastore.profilerclient.DeviceProfilerService;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.datastore.profilerclient.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.memory.model.MemoryDataCache;
import com.android.tools.idea.monitor.ui.memory.model.MemoryInfoTreeNode;
import com.android.tools.idea.monitor.ui.memory.model.MemoryPoller;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public final class MemoryProfilerUiManager extends BaseProfilerUiManager {

  public interface MemoryEventListener extends EventListener {
    void newHeapDumpSamplesRetrieved(MemoryProfiler.MemoryData.HeapDumpSample newSample);
  }

  private static final Logger LOG = Logger.getInstance(MemoryProfilerUiManager.class);

  // Provides an empty HeapDumpSample object so users can diff a heap dump against epoch.
  private static final MemoryProfiler.MemoryData.HeapDumpSample EMPTY_HEAP_DUMP_SAMPLE =
    MemoryProfiler.MemoryData.HeapDumpSample.newBuilder().build();

  @NotNull
  private final EventDispatcher<MemoryEventListener> myMemoryEventDispatcher;
  private JButton myTriggerHeapDumpButton;
  private MemoryDataCache myDataCache;
  private JPanel myDetailedViewToolbar;
  private MemoryInfoTreeNode myRoot;
  private MemoryDetailSegment myMemoryDetailSegment;

  private JComboBox<MemoryProfiler.MemoryData.HeapDumpSample> myPrevHeapDumpSelector;
  private JComboBox<MemoryProfiler.MemoryData.HeapDumpSample> myNextHeapDumpSelector;
  private File myPrevHeapDumpFile;
  private Snapshot myPrevHeapDump;
  private File myNextHeapDumpFile;
  private Snapshot myNextHeapDump;

  public MemoryProfilerUiManager(@NotNull Range timeCurrentRangeUs, @NotNull Choreographer choreographer,
                                 @NotNull SeriesDataStore datastore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(timeCurrentRangeUs, choreographer, datastore, eventDispatcher);
    myMemoryEventDispatcher = EventDispatcher.create(MemoryEventListener.class);
  }

  @NotNull
  @Override
  public Set<Poller> createPollers(int pid) {
    DeviceProfilerService profilerService = myDataStore.getDeviceProfilerService();
    myDataCache = new MemoryDataCache(profilerService.getDevice(), myMemoryEventDispatcher);
    MemoryPoller poller = new MemoryPoller(myDataStore, myDataCache, pid);

    Map<SeriesDataType, DataAdapter> adapters = poller.createAdapters();
    for (Map.Entry<SeriesDataType, DataAdapter> entry : adapters.entrySet()) {
      // TODO these need to be de-registered
      myDataStore.registerAdapter(entry.getKey(), entry.getValue());
    }

    myMemoryEventDispatcher.addListener(newSample -> {
      // Update the UI from the EDT thread.
      ApplicationManager.getApplication().invokeLater(() -> {
        myPrevHeapDumpSelector.addItem(newSample);
        myNextHeapDumpSelector.addItem(newSample);
        if (myNextHeapDumpSelector.getSelectedItem() != null && myNextHeapDumpSelector.getSelectedItem() != EMPTY_HEAP_DUMP_SAMPLE) {
          myPrevHeapDumpSelector.setSelectedItem(myNextHeapDumpSelector.getSelectedItem());
        }
        myNextHeapDumpSelector.setSelectedItem(newSample);
      });
    });

    return Sets.newHashSet(poller);
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range timeCurrentRangeUs,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new MemorySegment(timeCurrentRangeUs, dataStore, eventDispatcher);
  }

  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(toolbar, overviewPanel);

    myTriggerHeapDumpButton = new JButton(AndroidIcons.Ddms.DumpHprof);
    MemoryPoller poller = (MemoryPoller)Iterables.getOnlyElement(myPollerSet);
    myTriggerHeapDumpButton.addActionListener(e -> {
      if (poller.requestHeapDump()) {
        myEventDispatcher.getMulticaster().profilerExpanded(ProfilerType.MEMORY);
      }
    });
    toolbar.add(myTriggerHeapDumpButton, HorizontalLayout.LEFT);
  }

  @Override
  public void setupDetailedViewUi(@NotNull JPanel toolbar, @NotNull JPanel detailPanel) {
    myDetailedViewToolbar = createDetailToolbar();
    detailPanel.add(myDetailedViewToolbar, BorderLayout.NORTH);

    if (myRoot == null) {
      myRoot = new MemoryInfoTreeNode("Root");
    }

    if (myMemoryDetailSegment == null) {
      myMemoryDetailSegment = new MemoryDetailSegment(myTimeCurrentRangeUs, myRoot, myEventDispatcher);
      List<Animatable> animatables = new ArrayList<>();
      myMemoryDetailSegment.createComponentsList(animatables);
      myChoreographer.register(animatables);
      myMemoryDetailSegment.initializeComponents();
    }

    detailPanel.add(myMemoryDetailSegment, BorderLayout.CENTER);
  }

  @Override
  public void resetProfiler(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(toolbar, overviewPanel, detailPanel);

    detailPanel.removeAll();
    myDetailedViewToolbar = null;

    myPrevHeapDumpSelector = null;
    myNextHeapDumpSelector = null;
    myPrevHeapDumpFile = null;
    myNextHeapDumpFile = null;
    if (myPrevHeapDump != null) {
      myPrevHeapDump.dispose();
      myPrevHeapDump = null;
    }
    if (myNextHeapDump != null) {
      myNextHeapDump.dispose();
      myNextHeapDump = null;
    }

    if (myTriggerHeapDumpButton != null) {
      toolbar.remove(myTriggerHeapDumpButton);
      myTriggerHeapDumpButton = null;
    }
  }

  private JPanel createDetailToolbar() {
    JPanel toolbar = new JBPanel(new HorizontalLayout(JBUI.scale(5)));
    myPrevHeapDumpSelector = new JComboBox<>();
    myPrevHeapDumpSelector.addItem(EMPTY_HEAP_DUMP_SAMPLE);
    myPrevHeapDumpSelector.addActionListener(e -> {
      File file = myDataCache.getHeapDumpFile((MemoryProfiler.MemoryData.HeapDumpSample)myPrevHeapDumpSelector.getSelectedItem());
      if (myPrevHeapDumpFile == file) {
        return;
      }

      if (myPrevHeapDump != null) {
        myPrevHeapDump.dispose();
        myPrevHeapDump = null;
      }

      try {
        myPrevHeapDump = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
      }
      catch (IOException exception) {
        LOG.info("Error generating Snapshot from heap dump file.", exception);
      }

      generateClassHistogram(myMemoryDetailSegment, myRoot, myPrevHeapDump, myNextHeapDump);
    });

    myNextHeapDumpSelector = new JComboBox<>();
    myNextHeapDumpSelector.addItem(EMPTY_HEAP_DUMP_SAMPLE);
    myNextHeapDumpSelector.addActionListener(e -> {
      File file = myDataCache.getHeapDumpFile((MemoryProfiler.MemoryData.HeapDumpSample)myNextHeapDumpSelector.getSelectedItem());
      if (myNextHeapDumpFile == file) {
        return;
      }

      if (myNextHeapDump != null) {
        myNextHeapDump.dispose();
        myNextHeapDump = null;
      }

      try {
        myNextHeapDump = Snapshot.createSnapshot(new MemoryMappedFileBuffer(file));
      }
      catch (IOException exception) {
        LOG.info("Error generating Snapshot from heap dump file.", exception);
      }

      generateClassHistogram(myMemoryDetailSegment, myRoot, myPrevHeapDump, myNextHeapDump);
    });

    toolbar.add(HorizontalLayout.LEFT, myPrevHeapDumpSelector);
    toolbar.add(HorizontalLayout.RIGHT, myNextHeapDumpSelector);
    return toolbar;
  }

  /**
   * Updates a {@link MemoryDetailSegment} that shows a delta in class instances between prevHeapDump and nextHeapDump
   */
  private static void generateClassHistogram(@NotNull MemoryDetailSegment detailSegment,
                                             @NotNull MemoryInfoTreeNode root,
                                             @Nullable Snapshot prevHeapDump,
                                             @Nullable Snapshot nextHeapDump) {
    root.setCount(0);
    root.removeAllChildren();
    Map<String, Integer> instanceMap = new HashMap<>();

    // Compute the positive delta from the next heap
    if (nextHeapDump != null) {
      for (Heap heap : nextHeapDump.getHeaps()) {
        for (ClassObj classObj : heap.getClasses()) {
          String className = classObj.getClassName();
          int instanceCount = classObj.getInstanceCount() + (instanceMap.containsKey(className) ? instanceMap.get(className) : 0);
          instanceMap.put(className, instanceCount);
        }
      }
    }

    // Subtract the negative delta from the previous heap
    if (prevHeapDump != null) {
      for (Heap heap : prevHeapDump.getHeaps()) {
        for (ClassObj classObj : heap.getClasses()) {
          String className = classObj.getClassName();
          int instanceCount = (instanceMap.containsKey(className) ? instanceMap.get(className) : 0) - classObj.getInstanceCount();
          instanceMap.put(className, instanceCount);
        }
      }
    }

    int maxInstanceCount = Integer.MIN_VALUE;
    for (Map.Entry<String, Integer> entry : instanceMap.entrySet()) {
      int instanceCount = entry.getValue();
      if (instanceCount != 0) {
        MemoryInfoTreeNode child = new MemoryInfoTreeNode(entry.getKey());
        child.setCount(instanceCount);
        detailSegment.insertNode(root, child);
        maxInstanceCount = Math.max(maxInstanceCount, Math.abs(instanceCount));
      }
    }

    root.setCount(maxInstanceCount);
    detailSegment.refreshNode(root);
  }
}
