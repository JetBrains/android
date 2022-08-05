/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import static com.android.tools.idea.diagnostics.heap.HeapTraverseUtil.processMask;

import com.android.tools.idea.diagnostics.hprof.util.HeapReportUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public final class HeapSnapshotStatistics {

  public static int COMPONENT_NOT_FOUND = -1;

  @NotNull
  private final HeapObjectsStatistics myNonComponentStats = new HeapObjectsStatistics("non-component");
  @NotNull
  private final HeapObjectsStatistics myTotalStats = new HeapObjectsStatistics("total");
  @NotNull
  private final List<HeapObjectsStatistics> myComponentStats = new ArrayList<>();
  @NotNull
  private final Int2ObjectMap<HeapObjectsStatistics> myMaskToSharedComponentStats = new Int2ObjectOpenHashMap<>();
  @NotNull
  private final ComponentsSet myComponentsSet;

  public HeapSnapshotStatistics(@NotNull final ComponentsSet componentSet) {
    myComponentsSet = componentSet;
    for (ComponentsSet.Component component : componentSet.getComponents()) {
      myComponentStats.add(new HeapObjectsStatistics(component.getComponentName()));
    }
  }

  @NotNull
  public List<HeapObjectsStatistics> getComponentStats() {
    return myComponentStats;
  }

  public void addObjectSizeToSharedComponent(int sharedMask, long size) {
    if (!myMaskToSharedComponentStats.containsKey(sharedMask)) {
      List<String> componentNames = new ArrayList<>();
      processMask(sharedMask, (index) -> componentNames.add(myComponentsSet.getComponents().get(index).getComponentName()));
      myMaskToSharedComponentStats.put(sharedMask, new HeapObjectsStatistics("Shared: " + String.join(", ", componentNames)));
    }
    myMaskToSharedComponentStats.get(sharedMask).addOwnedObject(size);
  }

  public void addOwnedObjectSizeToComponent(int componentId, long size) {
    myComponentStats.get(componentId).addOwnedObject(size);
  }

  public void addObjectToTotal(long size) {
    myTotalStats.addOwnedObject(size);
  }

  public void addRetainedObjectSizeToComponent(int componentID, long size) {
    myComponentStats.get(componentID).addRetainedObject(size);
  }

  public void addNonComponentObject(long size) {
    myNonComponentStats.addOwnedObject(size);
  }

  void print(@NotNull final PrintWriter out) {
    out.printf("Total: [%s/%d]\n", HeapReportUtils.INSTANCE.toShortStringAsCount(myTotalStats.myOwnedTotalSizeOfObjects),
               myTotalStats.myOwnedObjectsNumber);

    out.printf("Component %s [%s/%d]\n", myNonComponentStats.myComponentName,
               HeapReportUtils.INSTANCE.toShortStringAsCount(myNonComponentStats.myOwnedTotalSizeOfObjects),
               myNonComponentStats.myOwnedObjectsNumber);

    for (HeapObjectsStatistics stat : myComponentStats) {
      out.printf("Component %s [%s/%d]\nRetained size: [%s/%d]\n", stat.myComponentName,
                 HeapReportUtils.INSTANCE.toShortStringAsCount(stat.myOwnedTotalSizeOfObjects), stat.myOwnedObjectsNumber,
                 HeapReportUtils.INSTANCE.toShortStringAsCount(stat.myRetainedTotalSizeOfObjects), stat.myRetainedObjectsNumber);
    }

    for (Map.Entry<Integer, HeapObjectsStatistics> entry : myMaskToSharedComponentStats.entrySet()) {
      out.printf("Component %s [%s/%d]\n", entry.getValue().myComponentName,
                 HeapReportUtils.INSTANCE.toShortStringAsCount(entry.getValue().myOwnedTotalSizeOfObjects),
                 entry.getValue().myOwnedObjectsNumber);
    }
  }

  @NotNull
  public ComponentsSet getComponentsSet() {
    return myComponentsSet;
  }

  static class HeapObjectsStatistics {
    private int myOwnedObjectsNumber = 0;
    private long myOwnedTotalSizeOfObjects = 0;

    private int myRetainedObjectsNumber = 0;
    private long myRetainedTotalSizeOfObjects = 0;

    @NotNull
    private final String myComponentName;

    private HeapObjectsStatistics(@NotNull final String componentName) {
      myComponentName = componentName;
    }

    private void addOwnedObject(long size) {
      myOwnedObjectsNumber++;
      myOwnedTotalSizeOfObjects += size;
    }

    public void addRetainedObject(long size) {
      myRetainedObjectsNumber++;
      myRetainedTotalSizeOfObjects += size;
    }

    int getOwnedObjectsNumber() {
      return myOwnedObjectsNumber;
    }

    long getOwnedTotalSizeOfObjects() {
      return myOwnedTotalSizeOfObjects;
    }

    int getRetainedObjectsNumber() {
      return myRetainedObjectsNumber;
    }

    long getRetainedTotalSizeOfObjects() {
      return myRetainedTotalSizeOfObjects;
    }

    @NotNull
    String getComponentName() {
      return myComponentName;
    }
  }
}
