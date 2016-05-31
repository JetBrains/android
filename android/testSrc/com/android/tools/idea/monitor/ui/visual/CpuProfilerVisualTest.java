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

package com.android.tools.idea.monitor.ui.visual;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.RangedDiscreteSeries;
import com.android.tools.adtui.visual.VisualTest;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.cpu.view.CpuUsageSegment;
import com.android.tools.idea.monitor.ui.cpu.view.ThreadsSegment;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CpuProfilerVisualTest extends VisualTest {

  private static final String CPU_PROFILER_NAME = "CPU Profiler";

  private static final int UPDATE_THREAD_SLEEP_DELAY_MS = 100;
  /**
   * The active threads should be copied into this array when getThreadGroup().enumerate() is called.
   * It is initialized with a safe size.
   */
  private static final Thread[] ACTIVE_THREADS = new Thread[1000];

  private SeriesDataStore mDataStore;

  private CpuUsageSegment mCPULevel1Segment;

  private CpuUsageSegment mCPULevel2Segment;

  private ThreadsSegment mThreadsSegment;

  private long mStartTimeMs;

  @Override
  protected void initialize() {
    mDataStore = new VisualTestSeriesDataStore();
    super.initialize();
  }

  @Override
  protected void reset() {
    if (mDataStore != null) {
      mDataStore.reset();
    }

    super.reset();
  }

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
    mCPULevel2Segment.registerComponents(components);
  }

  @Override
  public String getName() {
    return CPU_PROFILER_NAME;
  }

  @Override
  protected List<Animatable> createComponentsList() {
    mStartTimeMs = System.currentTimeMillis();
    Range timeRange = new Range();
    AnimatedTimeRange AnimatedTimeRange = new AnimatedTimeRange(timeRange, mStartTimeMs);

    //TODO Update test data for CpuUsageSegment to be exactly what it was.
    mCPULevel2Segment = new CpuUsageSegment(timeRange, mDataStore);
    mThreadsSegment = new ThreadsSegment(timeRange);

    List<Animatable> animatables = new ArrayList<>();
    animatables.add(AnimatedTimeRange);
    animatables.add(mThreadsSegment);
    mCPULevel2Segment.createComponentsList(animatables);
    mThreadsSegment.createComponentsList(animatables);

    return animatables;
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    panel.setLayout(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weighty = 1f/2;
    constraints.weightx = 1;
    constraints.gridy = 0;
    mCPULevel2Segment.initializeComponents();
    panel.add(mCPULevel2Segment, constraints);
    constraints.gridy = 1;
    mThreadsSegment.initializeComponents();
    panel.add(mThreadsSegment, constraints);
    simulateTestData();
  }

  private void simulateTestData() {
    Thread updateDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            //  Insert new data point at now.
            long now = System.currentTimeMillis() - mStartTimeMs;

            // Copy active threads into ACTIVE_THREADS array
            int numActiveThreads = getThreadGroup().enumerate(ACTIVE_THREADS);
            for (int i = 0; i < numActiveThreads; i++) {
              // We're only interested in threads that are alive
              Thread thread = ACTIVE_THREADS[i];
              if (thread.isAlive()) {
                // Add new thread to the segment in case it's not represented there yet.
                SwingUtilities.invokeAndWait(() -> mThreadsSegment.addThreadStateSeries(thread));
              }
            }

            for (Map.Entry<Thread, RangedDiscreteSeries<Thread.State>> series : mThreadsSegment.getThreadsStateSeries().entrySet()) {
              series.getValue().getSeries().add(now, series.getKey().getState());
            }

            Thread.sleep(UPDATE_THREAD_SLEEP_DELAY_MS);
          }
        }
        catch (InterruptedException ignored) {}
        catch (InvocationTargetException e) {
          e.printStackTrace();
        }
      }
    };
    updateDataThread.start();
  }
}
