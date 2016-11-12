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

import com.android.ddmlib.AllocationInfo;
import com.android.ddmlib.AllocationsParser;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.model.Range;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.memory.model.AllocationTrackingSample;
import com.android.tools.idea.monitor.ui.memory.model.MemoryDataCache;
import com.android.tools.idea.monitor.ui.memory.model.MemoryInfoTreeNode;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.android.tools.profiler.proto.MemoryProfiler.MemoryData.HeapDumpSample;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This represents the histogram view of the memory monitor detail view.
 */
class ClassHistogramView implements Disposable {
  @NotNull
  private final JPanel myParent;
  @NotNull
  private MemoryInfoTreeNode myRoot;
  @NotNull
  private MemoryDetailSegment myMemoryDetailSegment;
  @Nullable
  private HeapDump myMainHeapDump;
  @Nullable
  private HeapDump myDiffHeapDump;

  ClassHistogramView(@NotNull Disposable parentDisposable,
                     @NotNull JPanel parentPanel,
                     @NotNull Range timeCurrentRangeUs,
                     @NotNull Choreographer choreographer,
                     @NotNull EventDispatcher<ProfilerEventListener> profilerEventDispatcher) {
    Disposer.register(parentDisposable, this);

    myParent = parentPanel;
    myRoot = new MemoryInfoTreeNode("Root");

    myMemoryDetailSegment = new MemoryDetailSegment(timeCurrentRangeUs, myRoot, profilerEventDispatcher);
    List<Animatable> animatables = new ArrayList<>();
    myMemoryDetailSegment.createComponentsList(animatables);
    choreographer.register(animatables);
    myMemoryDetailSegment.initializeComponents();

    myParent.add(myMemoryDetailSegment, BorderLayout.CENTER);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(ClassHistogramView.class);
  }

  void generateClassHistogramFromHeapDumpSamples(@NotNull MemoryDataCache dataCache,
                                                 @Nullable HeapDumpSample mainHeapDumpSample,
                                                 @Nullable HeapDumpSample diffHeapDumpSample) {
    // TODO make this method asynchronous

    if (myMainHeapDump == null || myMainHeapDump.getSample() != mainHeapDumpSample) {
      if (myMainHeapDump != null) {
        myMainHeapDump.dispose();
        myMainHeapDump = null;
      }

      if (mainHeapDumpSample != null) {
        try {
          myMainHeapDump = new HeapDump(dataCache, mainHeapDumpSample);
        }
        catch (IOException exception) {
          getLog().info("Error generating Snapshot from heap dump file.", exception);
          return;
        }
      }
    }

    if (myDiffHeapDump == null || myDiffHeapDump.getSample() != diffHeapDumpSample) {
      if (myDiffHeapDump != null) {
        myDiffHeapDump.dispose();
        myDiffHeapDump = null;
      }

      if (diffHeapDumpSample != null) {
        try {
          myDiffHeapDump = new HeapDump(dataCache, diffHeapDumpSample);
        }
        catch (IOException exception) {
          getLog().info("Error generating Snapshot from heap dump file.", exception);
          return;
        }
      }
    }

    HeapDump positiveHeapDump = myDiffHeapDump != null ? myDiffHeapDump : myMainHeapDump;
    HeapDump negativeHeapDump = myDiffHeapDump != null ? myMainHeapDump : null;

    Map<String, Integer> instanceMap = new HashMap<>();
    // Compute the positive delta from the next heap dump
    if (positiveHeapDump != null) {
      for (Heap heap : positiveHeapDump.mySnapshot.getHeaps()) {
        for (ClassObj classObj : heap.getClasses()) {
          String className = classObj.getClassName();
          int instanceCount = classObj.getInstanceCount() + instanceMap.getOrDefault(className, 0);
          instanceMap.put(className, instanceCount);
        }
      }
    }

    // Subtract the negative delta from the main heap dump
    if (negativeHeapDump != null) {
      for (Heap heap : negativeHeapDump.mySnapshot.getHeaps()) {
        for (ClassObj classObj : heap.getClasses()) {
          String className = classObj.getClassName();
          int instanceCount = instanceMap.getOrDefault(className, 0) - classObj.getInstanceCount();
          instanceMap.put(className, instanceCount);
        }
      }
    }

    generateClassHistogram(instanceMap);
  }

  boolean generateClassHistogramFromAllocationTracking(@NotNull AllocationTrackingSample sample) {
    // TODO move/implement detection + fixup of .alloc file into addAllocationTracking, and make this asynchronous

    // Dispose loaded hprof files as we're in allocation tracking mode.
    if (myMainHeapDump != null) {
      myMainHeapDump.dispose();
      myMainHeapDump = null;
    }

    if (myDiffHeapDump != null) {
      myDiffHeapDump.dispose();
      myDiffHeapDump = null;
    }

    ByteBuffer data = ByteBuffer.wrap(sample.getData());
    data.order(ByteOrder.BIG_ENDIAN);
    if (AllocationsParser.hasOverflowedNumEntriesBug(data)) {
      getLog().info("Allocations file has overflow bug.");
      return false;
    }

    AllocationInfo[] allocationInfos = AllocationsParser.parse(data);
    Map<String, Integer> instanceMap = new HashMap<>();
    for (AllocationInfo info : allocationInfos) {
      instanceMap.put(info.getAllocatedClass(), instanceMap.getOrDefault(info.getAllocatedClass(), 0) + 1);
    }

    generateClassHistogram(instanceMap);

    return true;
  }

  /**
   * Updates a {@link MemoryDetailSegment} to show the allocations (and changes)
   */
  private void generateClassHistogram(@NotNull Map<String, Integer> instanceMap) {
    myRoot.setCount(0);
    myRoot.removeAllChildren();

    int maxInstanceCount = Integer.MIN_VALUE;
    for (Map.Entry<String, Integer> entry : instanceMap.entrySet()) {
      int instanceCount = entry.getValue();
      if (instanceCount != 0) {
        MemoryInfoTreeNode child = new MemoryInfoTreeNode(entry.getKey());
        child.setCount(instanceCount);
        myMemoryDetailSegment.insertNode(myRoot, child);
        maxInstanceCount = Math.max(maxInstanceCount, Math.abs(instanceCount));
      }
    }

    myRoot.setCount(maxInstanceCount);
    myMemoryDetailSegment.refreshNode(myRoot);
  }

  @Override
  public void dispose() {
    if (myMainHeapDump != null) {
      myMainHeapDump.dispose();
      myMainHeapDump = null;
    }
    if (myDiffHeapDump != null) {
      myDiffHeapDump.dispose();
      myDiffHeapDump = null;
    }
    myParent.remove(myMemoryDetailSegment);
  }

  private static class HeapDump {
    @NotNull private final HeapDumpSample mySample;
    @NotNull private final Snapshot mySnapshot;

    public HeapDump(@NotNull MemoryDataCache dataCache, @NotNull HeapDumpSample sample) throws IOException {
      mySample = sample;
      mySnapshot = Snapshot.createSnapshot(new InMemoryBuffer(dataCache.getHeapDumpData(sample).asReadOnlyByteBuffer()));
    }

    @NotNull
    public HeapDumpSample getSample() {
      return mySample;
    }

    public void dispose() {
      mySnapshot.dispose();
    }
  }
}
