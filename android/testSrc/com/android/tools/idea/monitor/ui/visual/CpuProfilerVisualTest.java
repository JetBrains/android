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
import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.visual.VisualTest;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.cpu.model.ThreadStatesDataModel;
import com.android.tools.idea.monitor.ui.cpu.view.CpuUsageSegment;
import com.android.tools.idea.monitor.ui.cpu.view.ThreadsSegment;
import com.android.tools.idea.monitor.ui.visual.data.TestDataGenerator;
import com.android.tools.profiler.proto.Cpu;
import com.intellij.util.EventDispatcher;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CpuProfilerVisualTest extends VisualTest {

  private static final String CPU_PROFILER_NAME = "CPU Profiler";

  private SeriesDataStore mDataStore;

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
  public String getName() {
    return CPU_PROFILER_NAME;
  }

  @Override
  protected List<Animatable> createComponentsList() {
    mStartTimeMs = System.currentTimeMillis();
    Range timeRange = new Range();
    AnimatedTimeRange AnimatedTimeRange = new AnimatedTimeRange(timeRange, mStartTimeMs);

    //TODO Update test data for CpuUsageSegment to be exactly what it was.
    EventDispatcher<ProfilerEventListener> dummyDispatcher = EventDispatcher.create(ProfilerEventListener.class);
    mCPULevel2Segment = new CpuUsageSegment(timeRange, mDataStore, dummyDispatcher);
    mThreadsSegment = new ThreadsSegment(timeRange, mDataStore, dummyDispatcher, (threads) -> {
      // TODO: show L3 segment with the charts corresponding to threads selected.
      // Hide any charts corresponding to unselected threads and hide L3 segment in case no threads are selected
    });

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
    ThreadStatesDataModel threadStatesDataModel = new ThreadStatesDataModel(name);
    mDataStore.registerAdapter(
      SeriesDataType.CPU_THREAD_STATE,
      new ThreadStateTestDataGenerator(threadStatesDataModel.getThreadStates(), threadStatesDataModel.getTimestamps()),
      threadStatesDataModel);
    mThreadsSegment.getThreadAddedNotifier().threadAdded(threadStatesDataModel);
  }

  private static final class ThreadStateTestDataGenerator extends TestDataGenerator<Cpu.ThreadActivity.State> {

    private List<Cpu.ThreadActivity.State> mStates;

    // Set the initial state to be, arbitrary RUNNING. The other alive state is SLEEPING.
    private Cpu.ThreadActivity.State mCurrentState = Cpu.ThreadActivity.State.RUNNING;

    /**
     * Flag to indicate whether the thread is that. In this case, we should interrupt the data generation thread.
     */
    private boolean mIsDead = false;

    private ThreadStateTestDataGenerator(List<Cpu.ThreadActivity.State> states, TLongArrayList timestamps) {
      mStates = states;
      mTime = timestamps;
    }

    @Override
    public SeriesData<Cpu.ThreadActivity.State> get(int index) {
      return new SeriesData<>(mTime.get(index) - mStartTimeMs, mStates.get(index));
    }

    @Override
    protected void generateData() {
      addState();
    }

    private void addState() {
      double prob = Math.random();
      mTime.add(System.currentTimeMillis());
      if (mIsDead || prob < 0.01) { // Terminate the thread with 1% of chance. If it's already dead, repeat the state.
        mCurrentState = Cpu.ThreadActivity.State.DEAD;
        // there' no need for adding more states after that.
        mIsDead = true;
      } else if (prob < 0.31) { // Change state with 30% of chance
        mCurrentState = mCurrentState == Cpu.ThreadActivity.State.RUNNING ?
                        Cpu.ThreadActivity.State.SLEEPING : Cpu.ThreadActivity.State.RUNNING;
      }
      // Otherwise, repeat last state.
      mStates.add(mCurrentState);
    }
  }
}
