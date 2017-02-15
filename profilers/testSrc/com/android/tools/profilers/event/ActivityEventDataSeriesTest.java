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
  private static final String ACTIVITY_NAME_2 = "TestActivity2";

  FakeEventService myEventService = new FakeEventService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel(getClass().getName(), myEventService);
  private ActivityEventDataSeries mySeries;

  @Before
  public void setUp() {
    mySeries = new ActivityEventDataSeries(myGrpcChannel.getClient(), FakeEventService.FAKE_APP_ID, ProfilersTestData.SESSION_DATA);
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
                         }
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = mySeries.getDataForXRange(range);
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
                         }
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    SeriesData<EventAction<StackedEventType>> event = dataList.get(0);
    verifyActivity(event, TEST_END_TIME_NS);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_COMPLETED);
    assertEquals(((ActivityAction)event.value).getData(), ACTIVITY_NAME);
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
                         }));
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
                         }
      ));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<StackedEventType>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 2);
    SeriesData<EventAction<StackedEventType>> event = dataList.get(0);
    verifyActivity(event, 0);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_STARTED);
    assertEquals(((ActivityAction)event.value).getData(), ACTIVITY_NAME);
    event = dataList.get(1);
    verifyActivity(event, TEST_END_TIME_NS);
    assertEquals(event.value.getType(), StackedEventType.ACTIVITY_COMPLETED);
    assertEquals(((ActivityAction)event.value).getData(), ACTIVITY_NAME_2);
  }

  private void verifyActivity(SeriesData<EventAction<StackedEventType>> event, long endTime) {
    assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(endTime));
  }

  private EventProfiler.ActivityData buildActivityEvent(String name, ActivityStateData[] states) {
    EventProfiler.ActivityData.Builder builder = EventProfiler.ActivityData.newBuilder();
    builder.setProcessId(FakeEventService.FAKE_APP_ID)
      .setName(name)
      .setHash(name.hashCode());
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