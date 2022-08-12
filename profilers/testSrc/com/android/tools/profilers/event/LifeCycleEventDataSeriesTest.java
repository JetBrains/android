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
package com.android.tools.profilers.event;

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.LifecycleAction;
import com.android.tools.adtui.model.event.LifecycleEvent;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Interaction;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LifeCycleEventDataSeriesTest {
  private static final long TEST_START_TIME_NS = TimeUnit.SECONDS.toNanos(10);
  private static final long TEST_END_TIME_NS = TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1);
  private static final String ACTIVITY_NAME = "TestActivity";
  private static final String FRAGMENT_NAME = "TestFragment";
  private static final String ACTIVITY_NAME_2 = "TestActivity2";

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel(getClass().getName(), myTransportService);
  private LifecycleEventDataSeries myActivitySeries;
  private LifecycleEventDataSeries myFragmentSeries;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer);
    myActivitySeries = new LifecycleEventDataSeries(profilers, false);
    myFragmentSeries = new LifecycleEventDataSeries(profilers, true);
  }

  @Test
  public void testActivityStarted() {
    buildActivityEvent(ACTIVITY_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.CREATED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                       },
                       0);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<LifecycleEvent>>> dataList = myActivitySeries.getDataForRange(range);
    assertThat(dataList).hasSize(1);
    SeriesData<EventAction<LifecycleEvent>> event = dataList.get(0);
    verifyActivity(event, 0);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.STARTED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(ACTIVITY_NAME);
  }

  @Test
  public void testActivityCompleted() {
    buildActivityEvent(ACTIVITY_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.CREATED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.PAUSED,
                                               TEST_END_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.DESTROYED,
                                               TEST_END_TIME_NS),
                       },
                       0);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<LifecycleEvent>>> dataList = myActivitySeries.getDataForRange(range);
    assertThat(dataList).hasSize(1);
    SeriesData<EventAction<LifecycleEvent>> event = dataList.get(0);
    verifyActivity(event, TEST_END_TIME_NS);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.COMPLETED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(
      String.format("%s - %s", ACTIVITY_NAME, Interaction.ViewData.State.DESTROYED.toString().toLowerCase()));
  }

  @Test
  public void testActivityDied() {
    buildActivityEvent(ACTIVITY_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.CREATED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.PAUSED,
                                               TEST_END_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.STOPPED,
                                               TEST_END_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.DESTROYED,
                                               TEST_END_TIME_NS),
                       },
                       0);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<LifecycleEvent>>> dataList = myActivitySeries.getDataForRange(range);
    assertThat(dataList).hasSize(1);
    SeriesData<EventAction<LifecycleEvent>> event = dataList.get(0);
    verifyActivity(event, TEST_END_TIME_NS);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.COMPLETED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(
      String.format("%s - %s - %s", ACTIVITY_NAME, Interaction.ViewData.State.STOPPED.toString().toLowerCase(),
                    Interaction.ViewData.State.DESTROYED.toString().toLowerCase()));
  }

  @Test
  public void testActivityDiedThenResumed() {
    buildActivityEvent(ACTIVITY_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.CREATED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.PAUSED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.DESTROYED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.REMOVED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.CREATED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.PAUSED,
                                               TEST_END_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.DESTROYED,
                                               TEST_END_TIME_NS),
                       },
                       0);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<LifecycleEvent>>> dataList = myActivitySeries.getDataForRange(range);
    assertThat(dataList).hasSize(2);
    SeriesData<EventAction<LifecycleEvent>> event = dataList.get(0);
    verifyActivity(event, TEST_START_TIME_NS);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.COMPLETED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(
      String.format("%s - %s - %s", ACTIVITY_NAME, Interaction.ViewData.State.DESTROYED.toString().toLowerCase(),
                    Interaction.ViewData.State.REMOVED.toString().toLowerCase()));
    event = dataList.get(1);
    verifyActivity(event, TEST_END_TIME_NS);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.COMPLETED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(
      String.format("%s - %s", ACTIVITY_NAME, Interaction.ViewData.State.DESTROYED.toString().toLowerCase()));
  }

  @Test
  public void testActivityDestroyedDisplayString() {
    buildActivityEvent(ACTIVITY_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.CREATED, TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.DESTROYED, TEST_START_TIME_NS),
                       },
                       0);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<LifecycleEvent>>> dataList = myActivitySeries.getDataForRange(range);
    assertThat(dataList).hasSize(1);
    SeriesData<EventAction<LifecycleEvent>> event = dataList.get(0);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.COMPLETED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(
      String.format("%s - %s", ACTIVITY_NAME, Interaction.ViewData.State.DESTROYED.toString().toLowerCase()));
  }

  @Test
  public void testDestroyedEventOutOfOrder() {
    buildActivityEvent(ACTIVITY_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.CREATED, TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.DESTROYED, TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.PAUSED, TEST_START_TIME_NS),
                       },
                       0);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<LifecycleEvent>>> dataList = myActivitySeries.getDataForRange(range);
    assertThat(dataList).hasSize(1);
    SeriesData<EventAction<LifecycleEvent>> event = dataList.get(0);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.COMPLETED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(ACTIVITY_NAME);
  }

  @Test
  public void testMultipleActivity() {
    buildActivityEvent(ACTIVITY_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.CREATED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                       },
                       0);
    buildActivityEvent(ACTIVITY_NAME_2,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.CREATED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.PAUSED,
                                               TEST_END_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.DESTROYED,
                                               TEST_END_TIME_NS),
                       },
                       0);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<LifecycleEvent>>> dataList = myActivitySeries.getDataForRange(range);
    assertThat(dataList).hasSize(2);
    SeriesData<EventAction<LifecycleEvent>> event = dataList.get(0);
    verifyActivity(event, 0);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.STARTED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(ACTIVITY_NAME);
    event = dataList.get(1);
    verifyActivity(event, TEST_END_TIME_NS);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.COMPLETED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(
      String.format("%s - %s", ACTIVITY_NAME_2, Interaction.ViewData.State.DESTROYED.toString().toLowerCase()));
  }

  @Test
  public void testOnlyFragmentReceived() {
    buildActivityEvent(FRAGMENT_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.PAUSED,
                                               TEST_END_TIME_NS),
                       },
                       1234
    );
    buildActivityEvent(ACTIVITY_NAME,
                       new ActivityStateData[]{
                         new ActivityStateData(Interaction.ViewData.State.RESUMED,
                                               TEST_START_TIME_NS),
                         new ActivityStateData(Interaction.ViewData.State.PAUSED,
                                               TEST_END_TIME_NS),
                       },
                       0
    );
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<LifecycleEvent>>> dataList = myFragmentSeries.getDataForRange(range);
    assertThat(dataList).hasSize(1);
    SeriesData<EventAction<LifecycleEvent>> event = dataList.get(0);
    verifyActivity(event, TEST_END_TIME_NS);
    assertThat(event.value.getType()).isEqualTo(LifecycleEvent.COMPLETED);
    assertThat(((LifecycleAction)event.value).getName()).isEqualTo(FRAGMENT_NAME);
  }

  private static void verifyActivity(SeriesData<EventAction<LifecycleEvent>> event, long endTime) {
    assertThat(event.x).isEqualTo(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertThat(event.value.getStartUs()).isEqualTo(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertThat(event.value.getEndUs()).isEqualTo(TimeUnit.NANOSECONDS.toMicros(endTime));
  }

  private void buildActivityEvent(String name, ActivityStateData[] states, long contextHash) {
    for (ActivityStateData state : states) {
      myTransportService.addEventToStream(FAKE_DEVICE_ID,
                                          Common.Event.newBuilder()
                                            .setKind(Common.Event.Kind.VIEW)
                                            .setTimestamp(state.activityStateTime)
                                            .setGroupId(name.hashCode())
                                            .setIsEnded(state.isEndState())
                                            .setView(
                                              Interaction.ViewData.newBuilder()
                                                .setName(name)
                                                .setState(state.activityState)
                                                .setParentActivityId(contextHash)
                                            )
                                            .build());
    }
  }

  private static final class ActivityStateData {
    public Interaction.ViewData.State activityState;
    public long activityStateTime;

    private ActivityStateData(Interaction.ViewData.State state, long time) {
      activityState = state;
      activityStateTime = time;
    }

    private boolean isEndState() {
      switch (activityState) {
        case PAUSED:
        case STOPPED:
        case DESTROYED:
        case SAVED:
        case REMOVED:
          return true;
        default:
          return false;
      }
    }
  }
}