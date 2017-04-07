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

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.DataAdapter;
import com.android.tools.datastore.Poller;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.memory.model.AllocationTrackingSample;
import com.android.tools.idea.monitor.ui.memory.model.MemoryDataCache;
import com.android.tools.idea.monitor.ui.memory.model.MemoryPoller;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class MemoryProfilerUiManager extends BaseProfilerUiManager {

  @NotNull
  private final Project myProject;

  @NotNull
  private final Client myClient;

  @NotNull
  private final EventDispatcher<MemoryEventListener> myMemoryEventDispatcher;

  @Nullable
  private MemoryDataCache myDataCache;

  private JButton myTriggerHeapDumpButton;

  private JToggleButton myAllocationTrackerButton;

  @Nullable
  private MemoryDetailView myMemoryDetailView;

  private volatile boolean myAllowAllocationTracking = false;

  public MemoryProfilerUiManager(@NotNull Range timeCurrentRangeUs,
                                 @NotNull Choreographer choreographer,
                                 @NotNull SeriesDataStore datastore,
                                 @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher,
                                 @NotNull Project project,
                                 @NotNull Client client) {
    super(timeCurrentRangeUs, choreographer, datastore, eventDispatcher);
    myProject = project;
    myClient = client;
    myMemoryEventDispatcher = EventDispatcher.create(MemoryEventListener.class);
  }

  @NotNull
  @Override
  public Set<Poller> createPollers(int pid) {
    myDataCache = new MemoryDataCache(myMemoryEventDispatcher);
    MemoryPoller poller = new MemoryPoller(myDataStore, myDataCache, pid);

    Map<SeriesDataType, DataAdapter> adapters = poller.createAdapters();
    for (Map.Entry<SeriesDataType, DataAdapter> entry : adapters.entrySet()) {
      // TODO these need to be de-registered
      myDataStore.registerAdapter(entry.getKey(), entry.getValue());
    }

    if (myMemoryDetailView != null) {
      myMemoryDetailView.notifyDataIsReady(myDataCache);
    }

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
    assert myPollerSet != null;

    myTriggerHeapDumpButton = new JButton(AndroidIcons.Ddms.DumpHprof);
    MemoryPoller poller = (MemoryPoller)Iterables.getOnlyElement(myPollerSet);
    myTriggerHeapDumpButton.addActionListener(e -> {
      if (poller.requestHeapDump()) {
        myEventDispatcher.getMulticaster().profilerExpanded(ProfilerType.MEMORY);
      }
    });
    toolbar.add(myTriggerHeapDumpButton, HorizontalLayout.LEFT);

    myAllowAllocationTracking = true;
    myAllocationTrackerButton = new JToggleButton(AndroidIcons.Ddms.AllocationTracker, false);
    myClient.enableAllocationTracker(false);

    myAllocationTrackerButton.addActionListener(e -> {
      boolean selected = myAllocationTrackerButton.isSelected();
      if (selected) {
        if (!myAllowAllocationTracking) {
          myAllocationTrackerButton.setSelected(false);
          return;
        }

        myAllowAllocationTracking = false;
        myClient.enableAllocationTracker(true);
        final long startTime = TimeUnit.NANOSECONDS
          .toMicros(myDataStore.mapAbsoluteDeviceToRelativeTime(TimeUnit.MICROSECONDS.toNanos(myDataStore.getLatestTimeUs())));

        AndroidDebugBridge.addClientChangeListener(new AndroidDebugBridge.IClientChangeListener() {
          @Override
          public void clientChanged(@NonNull Client client, int changeMask) {
            if (client == myClient && (changeMask & Client.CHANGE_HEAP_ALLOCATIONS) != 0) {
              final byte[] data = client.getClientData().getAllocationsData();
              final long endTime = TimeUnit.NANOSECONDS
                .toMicros(myDataStore.mapAbsoluteDeviceToRelativeTime(TimeUnit.MICROSECONDS.toNanos(myDataStore.getLatestTimeUs())));

              UIUtil.invokeLaterIfNeeded(() -> {
                myEventDispatcher.getMulticaster().profilerExpanded(ProfilerType.MEMORY);
                if (myProject.isDisposed()) {
                  return;
                }

                if (myDataCache != null) {
                  myDataCache.addAllocationTrackingData(new AllocationTrackingSample(startTime, endTime, data));
                }
              });

              myAllowAllocationTracking = true;
              // Remove self from listeners.
              AndroidDebugBridge.removeClientChangeListener(this);
            }
          }
        });
      }
      else {
        myClient.requestAllocationDetails();
        myClient.enableAllocationTracker(false);
      }
      myAllocationTrackerButton.updateUI();
    });
    toolbar.add(myAllocationTrackerButton, HorizontalLayout.LEFT);
  }

  @Override
  public void setupDetailedViewUi(@NotNull JPanel toolbar, @NotNull JPanel detailPanel) {
    myMemoryDetailView =
      new MemoryDetailView(detailPanel, myDataStore, myDataCache, myTimeCurrentRangeUs, myChoreographer, myEventDispatcher,
                           myMemoryEventDispatcher);
    Disposer.register(myProject, myMemoryDetailView);
  }

  @Override
  public void resetProfiler(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(toolbar, overviewPanel, detailPanel);

    if (myMemoryDetailView != null) {
      Disposer.dispose(myMemoryDetailView);
      myMemoryDetailView = null;
    }

    if (myTriggerHeapDumpButton != null) {
      toolbar.remove(myTriggerHeapDumpButton);
      myTriggerHeapDumpButton = null;
    }

    if (myAllocationTrackerButton != null) {
      toolbar.remove(myAllocationTrackerButton);
      myAllocationTrackerButton = null;
    }
  }

  public interface MemoryEventListener extends EventListener {
    void newHeapDumpInfosRetrieved(HeapDumpInfo newInfo);

    void newAllocationTrackingInfosRetrieved(AllocationTrackingSample newInfo);
  }
}
