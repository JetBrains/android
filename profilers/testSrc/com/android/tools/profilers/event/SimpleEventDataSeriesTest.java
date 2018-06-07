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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.KeyboardAction;
import com.android.tools.adtui.model.event.SimpleEventType;
import com.android.tools.profiler.proto.EventProfiler.SystemData;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.ProfilersTestData;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class SimpleEventDataSeriesTest {

  private static final long TEST_START_TIME_NS = System.nanoTime();
  private static final long TEST_END_TIME_NS = TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1);

  FakeEventService myEventService = new FakeEventService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel(getClass().getName(), myEventService);
  private SimpleEventDataSeries mySeries;

  @Before
  public void setUp() {
    mySeries = new SimpleEventDataSeries(myGrpcChannel.getClient(), ProfilersTestData.SESSION_DATA);
  }

  @Test
  public void testRotationEvent() {
    myEventService.addSystemEvent(buildRotationEvent(1));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<SimpleEventType>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    for (SeriesData<EventAction<SimpleEventType>> data : dataList) {
      assertEquals(data.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(data.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(data.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(data.value.getType(), SimpleEventType.ROTATION);
    }
  }

  @Test
  public void testTouchEvent() {
    myEventService.addSystemEvent(buildTouchEvent(1));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<SimpleEventType>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    for (SeriesData<EventAction<SimpleEventType>> event : dataList) {
      assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
      assertEquals(event.value.getType(), SimpleEventType.TOUCH);
    }
  }

  @Test
  public void testKeyEvent() {
    myEventService.addSystemEvent(buildKeyEvent(1));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<SimpleEventType>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    for (SeriesData<EventAction<SimpleEventType>> event : dataList) {
      assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getType(), SimpleEventType.KEYBOARD);
      assertEquals(((KeyboardAction)event.value).getData().toString(), "Hello");
    }
  }

  @Test
  public void testMixedEvent() {
    myEventService.addSystemEvent(buildTouchEvent(1));
    myEventService.addSystemEvent(buildRotationEvent(2));
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<SimpleEventType>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 2);
    SeriesData<EventAction<SimpleEventType>> event = dataList.get(0);
    assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    assertEquals(event.value.getType(), SimpleEventType.TOUCH);
    event = dataList.get(1);
    assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getType(), SimpleEventType.ROTATION);
  }

  private SystemData buildTouchEvent(int eventId) {
    return SystemData.newBuilder()
      .setEventId(eventId)
      .setPid(ProfilersTestData.SESSION_DATA.getPid())
      .setStartTimestamp(TEST_START_TIME_NS)
      .setEndTimestamp(TEST_END_TIME_NS)
      .setType(SystemData.SystemEventType.TOUCH)
      .build();
  }

  private SystemData buildRotationEvent(int eventId) {
    return SystemData.newBuilder()
      .setEventId(eventId)
      .setPid(ProfilersTestData.SESSION_DATA.getPid())
      .setStartTimestamp(TEST_START_TIME_NS)
      .setEndTimestamp(TEST_START_TIME_NS)
      .setType(SystemData.SystemEventType.ROTATION)
      .build();
  }

  private SystemData buildKeyEvent(int eventId) {
    return SystemData.newBuilder()
      .setEventId(eventId)
      .setPid(ProfilersTestData.SESSION_DATA.getPid())
      .setStartTimestamp(TEST_START_TIME_NS)
      .setEndTimestamp(TEST_START_TIME_NS)
      .setType(SystemData.SystemEventType.KEY)
      .setEventData("Hello")
      .build();
  }
}