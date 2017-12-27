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

import com.android.tools.adtui.model.event.ActivityAction;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.StackedEventType;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.ProfilersTestData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ActivityEventDataSeriesTest {

  private static final long TEST_START_TIME_NS = System.nanoTime();
  private static final long TEST_END_TIME_NS = TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1);
  private static final String ACTIVITY_NAME = "TestActivity";
  private static final String FRAGMENT_NAME = "TestFragment";
  private static final String ACTIVITY_NAME_2 = "TestActivity2";

  FakeEventService myEventService = new FakeEventService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel(getClass().getName(), myEventService);
  private ActivityEventDataSeries myActivitySeries;
  private ActivityEventDataSeries myFragmentSeries;

  @Before
  public void setUp() {
    myActivitySeries =
      new ActivityEventDataSeries(myGrpcChannel.getClient(), FakeEventService.FAKE_APP_ID, ProfilersTestData.SESSION_DATA, false);
    myFragmentSeries =
      new ActivityEventDataSeries(myGrpcChannel.getClient(), FakeEventService.FAKE_APP_ID, ProfilersTestData.SESSION_DATA, true);
  }

  @Test
  public void testActivityStarted() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                         },
                         0
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = myActivitySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    SeriesData<EventAction<StackedEventType>> event = dataList.get(0);
    verifyActivity(event, 0);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_STARTED);
    assertEquals(((ActivityAction)event.value).getData(), ACTIVITY_NAME);
  }

  @Test
  public void testActivityCompleted() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                                                 TEST_END_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.DESTROYED,
                                                 TEST_END_TIME_NS),
                         },
                         0
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = myActivitySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    SeriesData<EventAction<StackedEventType>> event = dataList.get(0);
    verifyActivity(event, TEST_END_TIME_NS);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_COMPLETED);
    assertEquals(((ActivityAction)event.value).getData(), String.format("%s - %s", ACTIVITY_NAME,
                                                                        EventProfiler.ActivityStateData.ActivityState.DESTROYED.toString()
                                                                          .toLowerCase()));
  }

  @Test
  public void testActivityDied() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                                                 TEST_END_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.STOPPED,
                                                 TEST_END_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.DESTROYED,
                                                 TEST_END_TIME_NS),
                         },
                         0
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = myActivitySeries.getDataForXRange(range);
    assertEquals(1, dataList.size());
    SeriesData<EventAction<StackedEventType>> event = dataList.get(0);
    verifyActivity(event, TEST_END_TIME_NS);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_COMPLETED);
    assertEquals(((ActivityAction)event.value).getData(), String.format("%s - %s - %s", ACTIVITY_NAME,
                                                                        EventProfiler.ActivityStateData.ActivityState.STOPPED.toString()
                                                                          .toLowerCase(),
                                                                        EventProfiler.ActivityStateData.ActivityState.DESTROYED.toString()
                                                                          .toLowerCase()));
  }

  @Test
  public void testActivityDiedThenResumed() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.DESTROYED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.REMOVED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                                                 TEST_END_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.DESTROYED,
                                                 TEST_END_TIME_NS),
                         },
                         0
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = myActivitySeries.getDataForXRange(range);
    assertEquals(2, dataList.size());
    SeriesData<EventAction<StackedEventType>> event = dataList.get(0);
    verifyActivity(event, TEST_START_TIME_NS);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_COMPLETED);
    assertEquals(((ActivityAction)event.value).getData(), String.format("%s - %s - %s", ACTIVITY_NAME,
                                                                        EventProfiler.ActivityStateData.ActivityState.DESTROYED.toString()
                                                                          .toLowerCase(),
                                                                        EventProfiler.ActivityStateData.ActivityState.REMOVED.toString()
                                                                          .toLowerCase()));
    event = dataList.get(1);
    verifyActivity(event, TEST_END_TIME_NS);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_COMPLETED);
    assertEquals(((ActivityAction)event.value).getData(), String.format("%s - %s", ACTIVITY_NAME,
                                                                        EventProfiler.ActivityStateData.ActivityState.DESTROYED.toString()
                                                                          .toLowerCase()));
  }


  @Test
  public void testMultipleActivity() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                         },
                         0));
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME_2,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                                                 TEST_END_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.DESTROYED,
                                                 TEST_END_TIME_NS),
                         },
                         0
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = myActivitySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 2);
    SeriesData<EventAction<StackedEventType>> event = dataList.get(0);
    verifyActivity(event, 0);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_STARTED);
    assertEquals(((ActivityAction)event.value).getData(), ACTIVITY_NAME);
    event = dataList.get(1);
    verifyActivity(event, TEST_END_TIME_NS);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_COMPLETED);
    assertEquals(((ActivityAction)event.value).getData(), String.format("%s - %s", ACTIVITY_NAME_2,
                                                                        EventProfiler.ActivityStateData.ActivityState.DESTROYED.toString()
                                                                          .toLowerCase()));
  }

  @Test
  public void testOnlyFragmentReceived() {
    myEventService.addActivityEvent(
      buildActivityEvent(FRAGMENT_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                                                 TEST_END_TIME_NS),
                         },
                         1234
      ));
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_NS),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                                                 TEST_END_TIME_NS),
                         },
                         0
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = myFragmentSeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    SeriesData<EventAction<StackedEventType>> event = dataList.get(0);
    verifyActivity(event, TEST_END_TIME_NS);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_COMPLETED);
    assertEquals(FRAGMENT_NAME, ((ActivityAction)event.value).getData());
  }

  private void verifyActivity(SeriesData<EventAction<StackedEventType>> event, long endTime) {
    assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(endTime));
  }

  private EventProfiler.ActivityData buildActivityEvent(String name, ActivityStateData[] states, long contextHash) {
    EventProfiler.ActivityData.Builder builder = EventProfiler.ActivityData.newBuilder();
    builder.setProcessId(FakeEventService.FAKE_APP_ID)
      .setName(name)
      .setHash(name.hashCode())
      .setFragmentData(EventProfiler.FragmentData.newBuilder().setActivityContextHash(contextHash));
    for (ActivityStateData state : states) {
      builder.addStateChanges(EventProfiler.ActivityStateData.newBuilder()
                                .setState(state.activityState)
                                .setTimestamp(state.activityStateTime)
                                .build());
    }
    return builder.build();
  }

  private static final class ActivityStateData {
    public EventProfiler.ActivityStateData.ActivityState activityState;
    public long activityStateTime;

    private ActivityStateData(EventProfiler.ActivityStateData.ActivityState state, long time) {
      activityState = state;
      activityStateTime = time;
    }
  }
}