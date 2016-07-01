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
package com.android.tools.idea.monitor.ui.network.view;

import com.android.tools.adtui.AccordionLayout;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.network.model.HttpDataCache;
import com.android.tools.idea.monitor.ui.network.model.HttpDataPoller;
import com.android.tools.idea.monitor.ui.network.model.NetworkDataPoller;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Set;

public final class NetworkProfilerUiManager extends BaseProfilerUiManager {
  public static final int NETWORK_CONNECTIVITY_HEIGHT = 40;

  private NetworkRadioSegment myRadioSegment;

  private NetworkCaptureSegment myCaptureSegment;

  private NetworkDetailedView myDetailedView = new NetworkDetailedView();

  @NotNull
  private final HttpDataCache myDataCache;

  public NetworkProfilerUiManager(@NotNull Range xRange, @NotNull Choreographer choreographer,
                                  @NotNull SeriesDataStore dataStore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(xRange, choreographer, dataStore, eventDispatcher);
    myDataCache = new HttpDataCache(myDataStore.getDeviceProfilerService().getDevice());
  }

  @NotNull
  @Override
  public Set<Poller> createPollers(int pid) {
    return Sets.newHashSet(new NetworkDataPoller(myDataStore, pid), new HttpDataPoller(myDataStore, myDataCache, pid));
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range xRange,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new NetworkSegment(xRange, dataStore, eventDispatcher);
  }

  // TODO: Revisit for L4 design, this was intended for L3.
  @Override
  public void setupDetailedViewUi(@NotNull JPanel toolbar, @NotNull JPanel detailPanel) {
    super.setupDetailedViewUi(toolbar, detailPanel);
    detailPanel.add(myDetailedView, BorderLayout.CENTER);
  }

  @Override
  public boolean isDetailedViewVerticallySplit() {
    return false;
  }

  // TODO: Revisit for L3 design, this was intended for L2.
  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(toolbar, overviewPanel);
    myRadioSegment = new NetworkRadioSegment(myTimeViewRange, myDataStore, myEventDispatcher);
    setupAndRegisterSegment(myRadioSegment, NETWORK_CONNECTIVITY_HEIGHT, NETWORK_CONNECTIVITY_HEIGHT, NETWORK_CONNECTIVITY_HEIGHT);
    overviewPanel.add(myRadioSegment);

    myCaptureSegment = new NetworkCaptureSegment(myTimeViewRange, myDataStore, httpData -> {
      String responseFilePath = httpData.getHttpResponseBodyPath();
      File file = !StringUtil.isEmptyOrSpaces(responseFilePath) ? myDataCache.getFile(responseFilePath) : null;
      if (file != null) {
        myDetailedView.showConnectionDetails(file);
        myEventDispatcher.getMulticaster().profilerExpanded(ProfilerType.NETWORK);
      }
    }, myEventDispatcher);
    setupAndRegisterSegment(myCaptureSegment, DEFAULT_MONITOR_MIN_HEIGHT, DEFAULT_MONITOR_PREFERRED_HEIGHT, DEFAULT_MONITOR_MAX_HEIGHT);
    overviewPanel.add(myCaptureSegment);

    myChoreographer.register(myCaptureSegment);
    setSegmentState(overviewPanel, myCaptureSegment, AccordionLayout.AccordionState.MAXIMIZE);
  }

  @Override
  public void resetProfiler(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(toolbar, overviewPanel, detailPanel);

    overviewPanel.remove(myRadioSegment);
    overviewPanel.remove(myCaptureSegment);

    detailPanel.remove(myDetailedView);

    myChoreographer.unregister(myCaptureSegment);
  }
}
