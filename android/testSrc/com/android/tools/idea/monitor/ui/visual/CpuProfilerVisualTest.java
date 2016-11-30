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
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.RangeScrollbar;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.visualtests.VisualTest;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.cpu.view.CpuUsageSegment;
import com.android.tools.idea.monitor.ui.cpu.view.ThreadsSegment;
import com.android.tools.idea.monitor.ui.visual.data.TestDataGenerator;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.ThreadStateDataSeries;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CpuProfilerVisualTest extends VisualTest {

  private static final String CPU_PROFILER_NAME = "CPU Profiler";

  private SeriesDataStore mDataStore;

  private CpuUsageSegment mCPULevel2Segment;

  private ThreadsSegment mThreadsSegment;

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
  public String getName() {
    return CPU_PROFILER_NAME;
  }

  @Override
  protected List<Animatable> createComponentsList() {
    long startTimeUs = mDataStore.getLatestTimeUs();
    Range timeCurrentRangeUs = new Range(startTimeUs - RangeScrollbar.DEFAULT_VIEW_LENGTH_US, startTimeUs);
    AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(timeCurrentRangeUs, 0);

    //TODO Update test data for CpuUsageSegment to be exactly what it was.
    EventDispatcher<ProfilerEventListener> dummyDispatcher = EventDispatcher.create(ProfilerEventListener.class);
    mCPULevel2Segment = new CpuUsageSegment(timeCurrentRangeUs, mDataStore, dummyDispatcher);
    mThreadsSegment = new ThreadsSegment(timeCurrentRangeUs, mDataStore, dummyDispatcher, (threads) -> {
      // TODO: show L3 segment with the charts corresponding to threads selected.
      // Hide any charts corresponding to unselected threads and hide L3 segment in case no threads are selected
    });

    List<Animatable> animatables = new ArrayList<>();
    animatables.add(animatedTimeRange);
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
    mCPULevel2Segment.toggleView(true);
    panel.add(mCPULevel2Segment, constraints);
    constraints.gridy = 1;
    mThreadsSegment.initializeComponents();
    panel.add(mThreadsSegment, constraints);
    addThreadsTestData();
  }

  private void addThreadsTestData() {
    addThread("main");
    addThread("Thread-2");
  }

  private void addThread(String name) {
    // TODO: Convert to list model of threads
    //ThreadStateDataSeries threadStateDataSeries = new ThreadStateDataSeries(name, 0);
    //mDataStore.registerAdapter(
    //  SeriesDataType.CPU_THREAD_STATE,
    //  new ThreadStateTestDataGenerator(),
    //  threadStateDataSeries);
  }

  private static final class ThreadStateTestDataGenerator extends TestDataGenerator<CpuProfiler.ThreadActivity.State> {

    private List<SeriesData<CpuProfiler.ThreadActivity.State>> mStates = new ArrayList();

    // Set the initial state to be, arbitrary RUNNING. The other alive state is SLEEPING.
    private CpuProfiler.ThreadActivity.State mCurrentState = CpuProfiler.ThreadActivity.State.RUNNING;

    /**
     * Flag to indicate whether the thread is that. In this case, we should interrupt the data generation thread.
     */
    private boolean mIsDead = false;

    private ThreadStateTestDataGenerator() {
    }

    @Override
    public SeriesData<CpuProfiler.ThreadActivity.State> get(int index) {
      return mStates.get(index);
    }

    @Override
    protected void generateData() {
      addState();
    }

    private void addState() {
      double prob = Math.random();
      mTime.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime()));
      if (mIsDead || prob < 0.01) { // Terminate the thread with 1% of chance. If it's already dead, repeat the state.
        mCurrentState = CpuProfiler.ThreadActivity.State.DEAD;
        // there' no need for adding more states after that.
        mIsDead = true;
      } else if (prob < 0.31) { // Change state with 30% of chance
        mCurrentState = mCurrentState == CpuProfiler.ThreadActivity.State.RUNNING ?
                        CpuProfiler.ThreadActivity.State.SLEEPING : CpuProfiler.ThreadActivity.State.RUNNING;
      }
      // Otherwise, repeat last state.
      mStates.add(new SeriesData<>(mTime.get(mTime.size()-1), mCurrentState));
    }
  }
}
