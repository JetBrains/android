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
package com.android.tools.idea.monitor.ui;

import com.android.tools.adtui.AccordionLayout;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Helps manage the coordination between different UI segments when switching between Level1/2/3 views.
 */
public abstract class BaseProfilerUiManager {

  public enum ProfilerType {
    EVENT,
    NETWORK,
    MEMORY,
    CPU,
    GPU
  }

  private static final int MONITOR_MAX_HEIGHT = JBUI.scale(Short.MAX_VALUE);

  private static final int MONITOR_PREFERRED_HEIGHT = JBUI.scale(200);

  protected BaseSegment myOverviewSegment;

  @NotNull
  protected final Range myXRange;

  @NotNull
  protected final Choreographer myChoreographer;

  @NotNull
  protected final SeriesDataStore myDataStore;

  @NotNull
  protected final EventDispatcher<ProfilerEventListener> myEventDispatcher;

  protected Poller myPoller;

  public BaseProfilerUiManager(@NotNull Range xRange, @NotNull Choreographer choreographer,
                               @NotNull SeriesDataStore datastore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    myXRange = xRange;
    myChoreographer = choreographer;
    myDataStore = datastore;
    myEventDispatcher = eventDispatcher;
  }

  @Nullable
  public abstract Poller createPoller(int pid);

  public void startMonitoring(int pid) {
    assert myPoller == null;
    myPoller = createPoller(pid);
    if (myPoller != null) {
      ApplicationManager.getApplication().executeOnPooledThread(myPoller);
    }
  }

  public void stopMonitoring() {
    if (myPoller == null) {
      return;
    }
    myPoller.stop();
    myPoller = null;
  }

  /**
   * Sets up the profiler's Level1 view in the overviewPanel.
   */
  public void setupOverviewUi(@NotNull JPanel overviewPanel) {
    myOverviewSegment = createOverviewSegment(myXRange, myDataStore, myEventDispatcher);

    setupAndRegisterSegment(myOverviewSegment);
    overviewPanel.add(myOverviewSegment);
  }

  /**
   * Sets up the profiler's Level2 view in the overviewPanel.
   */
  public void setupExtendedOverviewUi(@NotNull JPanel overviewPanel) {
    if (myOverviewSegment == null) {
      return;
    }

    setSegmentState(overviewPanel, myOverviewSegment, AccordionLayout.AccordionState.MAXIMIZE);
    myOverviewSegment.toggleView(true);
  }

  /**
   * Sets up the profiler's Level3 view in the detailPanel which should appear under the overviewPanel.
   */
  public void setupDetailedViewUi(@NotNull JPanel detailPanel) {
  }

  /**
   * Resets the profiler back to its Level1 view. Each manager is responsible for destroying and de-referencing any UI content
   * in the overviewPanel/detailPanel that should not appear in Level1.
   */
  public void resetProfiler(@NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    if (myOverviewSegment == null) {
      return;
    }

    setSegmentState(overviewPanel, myOverviewSegment, AccordionLayout.AccordionState.PREFERRED);
    myOverviewSegment.toggleView(false);
  }

  @NotNull
  protected abstract BaseSegment createOverviewSegment(@NotNull Range xRange,
                                                       @NotNull SeriesDataStore dataStore,
                                                       @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher);

  protected void setupAndRegisterSegment(@NotNull BaseSegment segment) {
    segment.setMinimumSize(new Dimension(0, 0));
    segment.setPreferredSize(new Dimension(0, MONITOR_PREFERRED_HEIGHT));
    segment.setMaximumSize(new Dimension(0, MONITOR_MAX_HEIGHT));
    segment.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));

    List<Animatable> segmentAnimatables = new ArrayList<>();
    segment.createComponentsList(segmentAnimatables);

    myChoreographer.register(segmentAnimatables);
    segment.initializeComponents();
  }

  protected static void setSegmentState(@NotNull JPanel accordionPanel,
                                        @NotNull BaseSegment segment,
                                        @NotNull AccordionLayout.AccordionState state) {
    AccordionLayout layout = (AccordionLayout)accordionPanel.getLayout();
    if (layout != null) {
      layout.setState(segment, state);
    }
  }
}
