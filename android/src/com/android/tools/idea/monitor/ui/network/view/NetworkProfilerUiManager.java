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
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.Poller;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.network.model.HttpDataCache;
import com.android.tools.idea.monitor.ui.network.model.NetworkCaptureModel;
import com.android.tools.idea.monitor.ui.network.model.NetworkDataPoller;
import com.android.tools.idea.monitor.ui.network.model.RpcNetworkCaptureModel;
import com.google.common.collect.Sets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
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

  private NetworkDetailedView myDetailedView;

  @NotNull
  private final HttpDataCache myDataCache;

  public NetworkProfilerUiManager(@NotNull Range timeCurrentRangeUs, @NotNull Choreographer choreographer,
                                  @NotNull SeriesDataStore dataStore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher,
                                  @NotNull Project project) {
    super(timeCurrentRangeUs, choreographer, dataStore, eventDispatcher);
    myDataCache = new HttpDataCache(myDataStore.getDeviceProfilerService().getDevice());
    myDetailedView = new NetworkDetailedView(project);
  }

  @NotNull
  @Override
  public Set<Poller> createPollers(int pid) {
    return Sets.newHashSet(new NetworkDataPoller(myDataStore, pid));
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range timeCurrentRangeUs,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new NetworkSegment(timeCurrentRangeUs, dataStore, eventDispatcher);
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
    myRadioSegment = new NetworkRadioSegment(myTimeCurrentRangeUs, myDataStore, myEventDispatcher);
    setupAndRegisterSegment(myRadioSegment, NETWORK_CONNECTIVITY_HEIGHT, NETWORK_CONNECTIVITY_HEIGHT, NETWORK_CONNECTIVITY_HEIGHT);
    overviewPanel.add(myRadioSegment);
    NetworkCaptureModel captureModel = new RpcNetworkCaptureModel(myDataStore.getDeviceProfilerService(), myDataCache);
    myCaptureSegment = new NetworkCaptureSegment(myTimeCurrentRangeUs, captureModel, httpData -> {
      String responseFilePath = httpData.getHttpResponsePayloadId();
      // TODO: Refactor to get virtual file directly from data cache.
      File file = !StringUtil.isEmptyOrSpaces(responseFilePath) ? myDataCache.getFile(responseFilePath) : null;
      VirtualFile virtualFile = file != null ? LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) : null;
      if (virtualFile != null) {
        myDetailedView.showConnectionDetails(virtualFile);
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

    if (myRadioSegment != null) {
      overviewPanel.remove(myRadioSegment);
      myRadioSegment = null;
    }

    if (myCaptureSegment != null) {
      overviewPanel.remove(myCaptureSegment);
      myChoreographer.unregister(myCaptureSegment);
      myCaptureSegment = null;
    }

    if (myDetailedView != null) {
      detailPanel.remove(myDetailedView);
    }
  }
}
