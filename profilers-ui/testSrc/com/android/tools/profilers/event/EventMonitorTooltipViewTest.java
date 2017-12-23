/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.EventProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.StudioMonitorStageView;
import com.android.tools.profilers.StudioProfilers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class EventMonitorTooltipViewTest {

  FakeEventMonitorTooltipView myTooltipView;
  FakeTimer myTimer;
  EventMonitor myMonitor;
  FakeEventService myEventService = new FakeEventService();
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("EventMonitorTooltipViewTest", myEventService);
  private static final long TEST_START_TIME_US = TimeUnit.SECONDS.toNanos(1);
  private static final String ACTIVITY_NAME = "TestActivity";

  @Before
  public void setup() {
    myTimer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    myMonitor = new EventMonitor(profilers);
    myTooltipView = new FakeEventMonitorTooltipView(null, new EventMonitorTooltip(myMonitor));
    long tooltipTime = TimeUnit.SECONDS.toMicros(1) + TimeUnit.MILLISECONDS.toMicros(1);
    long timelineRange = TimeUnit.SECONDS.toMicros(5);
    myMonitor.getTimeline().getDataRange().set(0, timelineRange);
    myMonitor.getTimeline().getTooltipRange().set(tooltipTime, tooltipTime);
  }

  @Test
  public void testGetTitleText() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_US),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_US),
                         },
                         0
      ));
    myTimer.tick(1);
    String text = myTooltipView.getTitleText();
    assertEquals(String.format("%s 0s", ACTIVITY_NAME), text);
  }

  @Test
  public void testGetTitleTextCompleted() {
    myEventService.addActivityEvent(
      buildActivityEvent(ACTIVITY_NAME,
                         new ActivityStateData[]{
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.CREATED,
                                                 TEST_START_TIME_US),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.RESUMED,
                                                 TEST_START_TIME_US),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.PAUSED,
                                                 TEST_START_TIME_US + TimeUnit.SECONDS.toNanos(1)),
                           new ActivityStateData(EventProfiler.ActivityStateData.ActivityState.DESTROYED,
                                                 TEST_START_TIME_US + TimeUnit.SECONDS.toNanos(1)),
                         },
                         0
      ));
    myTimer.tick(1);
    String text = myTooltipView.getTitleText();
    assertEquals(String.format("%s - destroyed 1s", ACTIVITY_NAME), text);
  }

  @Test
  public void testGetTitleTextNone() {
    myTimer.tick(1);
    String text = myTooltipView.getTitleText();
    assertEquals("EVENTS at 1s1.00ms", text);
  }

  private com.android.tools.profiler.proto.EventProfiler.ActivityData buildActivityEvent(String name,
                                                                                         ActivityStateData[] states,
                                                                                         long contextHash) {
    com.android.tools.profiler.proto.EventProfiler.ActivityData.Builder builder =
      com.android.tools.profiler.proto.EventProfiler.ActivityData.newBuilder();
    builder.setProcessId(FakeEventService.FAKE_APP_ID)
      .setName(name)
      .setHash(name.hashCode())
      .setFragmentData(com.android.tools.profiler.proto.EventProfiler.FragmentData.newBuilder().setActivityContextHash(contextHash));
    for (ActivityStateData state : states) {
      builder.addStateChanges(EventProfiler.ActivityStateData.newBuilder()
                                .setState(state.activityState)
                                .setTimestamp(state.activityStateTime)
                                .build());
    }
    return builder.build();
  }

  private static final class FakeEventMonitorTooltipView extends EventMonitorTooltipView {

    public FakeEventMonitorTooltipView(StudioMonitorStageView parent, EventMonitorTooltip tooltip) {
      super(parent, tooltip);
      createComponent();
    }

    public String getTitleText() {
      return myHeadingLabel.getText();
    }
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
