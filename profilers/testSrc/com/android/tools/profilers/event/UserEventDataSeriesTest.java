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
import static org.junit.Assert.assertEquals;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.KeyboardAction;
import com.android.tools.adtui.model.event.UserEvent;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.EventProfiler.SystemData;
import com.android.tools.profiler.proto.Interaction;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UserEventDataSeriesTest {

  private static final long TEST_START_TIME_NS = System.nanoTime();
  private static final long TEST_END_TIME_NS = TEST_START_TIME_NS + TimeUnit.SECONDS.toNanos(1);

  private FakeTimer myTimer = new FakeTimer();
  private FakeTransportService myTransportService = new FakeTransportService(myTimer);
  private FakeEventService myEventService = new FakeEventService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel(getClass().getName(), myTransportService, myEventService);

  private FakeIdeProfilerServices myIdeProfilerServices;
  private UserEventDataSeries mySeries;

  @Parameterized.Parameters
  public static Collection<Boolean> useNewEventPipelineParameter() {
    return Arrays.asList(false, true);
  }

  public UserEventDataSeriesTest(boolean useNewEventPipeline) {
    myIdeProfilerServices = new FakeIdeProfilerServices();
    myIdeProfilerServices.enableEventsPipeline(useNewEventPipeline);
  }

  @Before
  public void setUp() {
    mySeries = new UserEventDataSeries(new StudioProfilers(new ProfilerClient(myGrpcChannel.getName()), myIdeProfilerServices, myTimer));
  }

  @Test
  public void testRotationEvent() {
    buildRotationEvent(1);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<UserEvent>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    for (SeriesData<EventAction<UserEvent>> data : dataList) {
      assertEquals(data.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(data.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(data.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(data.value.getType(), UserEvent.ROTATION);
    }
  }

  @Test
  public void testTouchEvent() {
    buildTouchEvent(1);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<UserEvent>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    for (SeriesData<EventAction<UserEvent>> event : dataList) {
      assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
      assertEquals(event.value.getType(), UserEvent.TOUCH);
    }
  }

  @Test
  public void testKeyEvent() {
    buildKeyEvent(1);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<UserEvent>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 1);
    for (SeriesData<EventAction<UserEvent>> event : dataList) {
      assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
      assertEquals(event.value.getType(), UserEvent.KEYBOARD);
      assertEquals(((KeyboardAction)event.value).getData().toString(), "Hello");
    }
  }

  @Test
  public void testMixedEvent() {
    buildTouchEvent(1);
    buildRotationEvent(2);
    Range range = new Range(TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    List<SeriesData<EventAction<UserEvent>>> dataList = mySeries.getDataForXRange(range);
    assertEquals(dataList.size(), 2);
    SeriesData<EventAction<UserEvent>> event = dataList.get(0);
    assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_END_TIME_NS));
    assertEquals(event.value.getType(), UserEvent.TOUCH);
    event = dataList.get(1);
    assertEquals(event.x, TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getStartUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getEndUs(), TimeUnit.NANOSECONDS.toMicros(TEST_START_TIME_NS));
    assertEquals(event.value.getType(), UserEvent.ROTATION);
  }

  private void buildTouchEvent(int eventId) {
    if (myIdeProfilerServices.getFeatureConfig().isUnifiedPipelineEnabled()) {
      myTransportService.addEventToEventGroup(FAKE_DEVICE_ID,
                                              Common.Event.newBuilder()
                                                .setKind(Common.Event.Kind.INTERACTION)
                                                .setTimestamp(TEST_START_TIME_NS)
                                                .setGroupId(eventId)
                                                .setInteraction(
                                                  Interaction.InteractionData.newBuilder()
                                                    .setType(Interaction.InteractionData.Type.TOUCH))
                                                .build());
      myTransportService.addEventToEventGroup(FAKE_DEVICE_ID,
                                              Common.Event.newBuilder()
                                                .setKind(Common.Event.Kind.INTERACTION)
                                                .setTimestamp(TEST_END_TIME_NS)
                                                .setGroupId(eventId)
                                                .setIsEnded(true)
                                                .setInteraction(
                                                  Interaction.InteractionData.newBuilder()
                                                    .setType(Interaction.InteractionData.Type.TOUCH))
                                                .build());
    }
    else {
      myEventService.addSystemEvent(SystemData.newBuilder()
                                      .setEventId(eventId)
                                      .setStartTimestamp(TEST_START_TIME_NS)
                                      .setEndTimestamp(TEST_END_TIME_NS)
                                      .setType(Interaction.InteractionData.Type.TOUCH)
                                      .build());
    }
  }

  private void buildRotationEvent(int eventId) {
    if (myIdeProfilerServices.getFeatureConfig().isUnifiedPipelineEnabled()) {
      myTransportService.addEventToEventGroup(FAKE_DEVICE_ID,
                                              Common.Event.newBuilder()
                                                .setKind(Common.Event.Kind.INTERACTION)
                                                .setTimestamp(TEST_START_TIME_NS)
                                                .setGroupId(eventId)
                                                .setIsEnded(true)
                                                .setInteraction(
                                                  Interaction.InteractionData.newBuilder()
                                                    .setType(Interaction.InteractionData.Type.ROTATION))
                                                .build());
    }
    else {
      myEventService.addSystemEvent(SystemData.newBuilder()
                                      .setEventId(eventId)
                                      .setStartTimestamp(TEST_START_TIME_NS)
                                      .setEndTimestamp(TEST_START_TIME_NS)
                                      .setType(Interaction.InteractionData.Type.ROTATION)
                                      .build());
    }
  }

  private void buildKeyEvent(int eventId) {
    if (myIdeProfilerServices.getFeatureConfig().isUnifiedPipelineEnabled()) {
      myTransportService.addEventToEventGroup(FAKE_DEVICE_ID,
                                              Common.Event.newBuilder()
                                                .setKind(Common.Event.Kind.INTERACTION)
                                                .setTimestamp(TEST_START_TIME_NS)
                                                .setGroupId(eventId)
                                                .setIsEnded(true)
                                                .setInteraction(
                                                  Interaction.InteractionData.newBuilder()
                                                    .setType(Interaction.InteractionData.Type.KEY)
                                                    .setEventData("Hello"))
                                                .build());
    }
    else {
      myEventService.addSystemEvent(SystemData.newBuilder()
                                      .setEventId(eventId)
                                      .setStartTimestamp(TEST_START_TIME_NS)
                                      .setEndTimestamp(TEST_START_TIME_NS)
                                      .setType(Interaction.InteractionData.Type.KEY)
                                      .setEventData("Hello")
                                      .build());
    }
  }
}