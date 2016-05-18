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

package com.android.tools.adtui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.ContinuousSeries;
import com.android.tools.adtui.segment.CPUUsageSegment;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.*;

import javax.swing.JPanel;

public class CPUProfilerVisualTest extends VisualTest {

  private static final String CPU_PROFILER_NAME = "CPU Profiler";

  private static final int UPDATE_THREAD_SLEEP_DELAY_MS = 100;

  private static final int PROCESSES_LINE_CHART_VARIANCE = 10;

  private static final int MY_PROCESS_MAX_VALUE = 60;

  private static final int OTHER_PROCESSES_MAX_VALUE = 20;

  /**
   * The active threads should be copied into this array when getThreadGroup().enumerate() is called.
   * It is initialized with a safe size.
   */
  private static final Thread[] ACTIVE_THREADS = new Thread[1000];

  private CPUUsageSegment mCPULevel1Segment;

  private CPUUsageSegment mCPULevel2Segment;

  private long mStartTimeMs;

  /**
   * Max y value of each process series.
   */
  private Map<ContinuousSeries, Integer> mSeriesMaxValues = new HashMap<>();

  /**
   * Series that represent processes share some properties, so they can grouped in a list.
   */
  private List<ContinuousSeries> mProcessesSeries;

  private ContinuousSeries mNumberOfThreadsSeries;


  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
    mCPULevel1Segment.registerComponents(components);
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

    ContinuousSeries myProcessSeries = new ContinuousSeries();
    mSeriesMaxValues.put(myProcessSeries, MY_PROCESS_MAX_VALUE);
    ContinuousSeries otherProcessesSeries = new ContinuousSeries();
    mSeriesMaxValues.put(otherProcessesSeries, OTHER_PROCESSES_MAX_VALUE);
    mNumberOfThreadsSeries = new ContinuousSeries();

    mCPULevel1Segment = new CPUUsageSegment(timeRange, myProcessSeries);
    mCPULevel2Segment = new CPUUsageSegment(timeRange, myProcessSeries, otherProcessesSeries, mNumberOfThreadsSeries);
    mProcessesSeries = Arrays.asList(myProcessSeries, otherProcessesSeries);

    List<Animatable> animatables = new ArrayList<Animatable>();
    animatables.add(AnimatedTimeRange);
    mCPULevel1Segment.createComponentsList(animatables);
    mCPULevel2Segment.createComponentsList(animatables);

    return animatables;
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weighty = .5;
    constraints.weightx = 1;
    constraints.gridy = 0;
    mCPULevel1Segment.initializeComponents();
    panel.add(mCPULevel1Segment, constraints);
    constraints.gridy = 1;
    mCPULevel2Segment.initializeComponents();
    panel.add(mCPULevel2Segment, constraints);
    simulateTestData();
  }

  private void simulateTestData() {
    mUpdateDataThread = new Thread() {
      @Override
      public void run() {
        try {
          while (true) {
            //  Insert new data point at now.
            long now = System.currentTimeMillis() - mStartTimeMs;
            for (ContinuousSeries series : mProcessesSeries) {
              int size = series.size();
              long last = size > 0 ? series.getY(size - 1) : 0;
              // Difference between current and new values is going to be variance times a number in the interval [-0.5, 0.5)
              float delta = PROCESSES_LINE_CHART_VARIANCE * ((float) Math.random() - 0.5f);
              long current = last + (long) delta;
              assert mSeriesMaxValues.containsKey(series);
              current = Math.min(mSeriesMaxValues.get(series), Math.max(current, 0));
              series.add(now, current);
            }

            // Copy active threads into ACTIVE_THREADS array
            int numActiveThreads = getThreadGroup().enumerate(ACTIVE_THREADS);
            int targetThreads = 0;
            for (int i = 0; i < numActiveThreads; i++) {
              // We're only interested in threads that are alive
              if (ACTIVE_THREADS[i].isAlive()) {
                targetThreads++;
              }
            }
            mNumberOfThreadsSeries.add(now, targetThreads);

            Thread.sleep(UPDATE_THREAD_SLEEP_DELAY_MS);
          }
        } catch (InterruptedException ignored) {}
      }
    };
    mUpdateDataThread.start();
  }
}
