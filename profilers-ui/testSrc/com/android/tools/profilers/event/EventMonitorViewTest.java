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

import com.android.tools.adtui.SimpleEventComponent;
import com.android.tools.adtui.StackedEventComponent;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import com.intellij.ui.HyperlinkLabel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventMonitorViewTest {

  private final FakeProfilerService myProfilerService = new FakeProfilerService(true);

  @Rule
  public FakeGrpcServer myGrpcChannel = new FakeGrpcServer("EventMonitorViewTestChannel", myProfilerService);

  private FakeTimer myTimer;

  private EventMonitorView myMonitorView;

  @Before
  public void setUp() {
    myTimer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));

    // StudioProfilersView initialization needs to happen after the tick, as during setDevice/setProcess the StudioMonitorStage is
    // constructed. If the StudioMonitorStageView is constructed as well, grpc exceptions will be thrown due to lack of various services
    // in the channel, and the tick loop would not complete properly to set the process and agent status.
    StudioProfilersView profilerView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    myMonitorView = new EventMonitorView(profilerView, new EventMonitor(profilers));
  }

  @Test
  public void testDisabledMonitorMessage() throws Exception {
    // First verify the enabled hierarchy is correct.
    Component[] children = myMonitorView.getComponent().getComponents();
    assertEquals(2, children.length);
    assertTrue(children[0] instanceof SimpleEventComponent);
    assertTrue(children[1] instanceof StackedEventComponent);

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.DETACHED);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));

    children = myMonitorView.getComponent().getComponents();
    assertEquals(2, children.length);
    assertTrue(children[0] instanceof JLabel);
    JLabel label = (JLabel)children[0];
    assertEquals(myMonitorView.getDisabledMessage(), label.getText());

    // TODO: verify content on HyperLinkLabel? There is no public API to get that at the moment.
    assertTrue(children[1] instanceof HyperlinkLabel);
  }
}