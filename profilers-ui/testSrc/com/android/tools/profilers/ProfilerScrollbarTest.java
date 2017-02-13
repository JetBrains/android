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
package com.android.tools.profilers;

import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.swing.FakeKeyboard;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class ProfilerScrollbarTest {

  private ProfilerTimeline myTimeline;
  private ProfilerScrollbar myScrollbar;
  private JPanel myPanel;
  private FakeUi myUi;

  @Before
  public void setUp() throws Exception {
    myTimeline = new ProfilerTimeline(new RelativeTimeConverter(0));
    myPanel = new JPanel();
    myPanel.setSize(100, 100);
    myScrollbar = new ProfilerScrollbar(myTimeline, myPanel);
    myScrollbar.setSize(100, 10);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(myPanel, BorderLayout.CENTER);
    panel.add(myScrollbar, BorderLayout.SOUTH);
    panel.setSize(100, 110);
    myUi = new FakeUi(panel);
    myTimeline.getDataRange().set(0, 10000);
    myTimeline.getViewRange().set(0, 5000);
  }

  @Test
  public void testModelChanged() {

    // Model units are kept in 1000th of a range unit
    assertEquals(0, myScrollbar.getModel().getMinimum());
    assertEquals(10, myScrollbar.getModel().getMaximum());
    assertEquals(0, myScrollbar.getModel().getValue());
    assertEquals(5, myScrollbar.getModel().getExtent());

    myTimeline.getViewRange().set(1000, 4000);

    // Model units are kept in 1000th of a range unit
    assertEquals(0, myScrollbar.getModel().getMinimum());
    assertEquals(10, myScrollbar.getModel().getMaximum());
    assertEquals(1, myScrollbar.getModel().getValue());
    assertEquals(3, myScrollbar.getModel().getExtent());
  }

  @Test
  public void testZoom() {
    // Zoom in
    double delta = myScrollbar.getWheelDelta();
    myUi.keyboard.press(FakeKeyboard.Key.ALT); // Alt+wheel == zoom

    myUi.mouse.wheel(50, 50, -1);
    assertEquals(0 + delta * 0.5, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 - delta * 0.5, myTimeline.getViewRange().getMax(), 0.001);

    // Zoom in twice
    double delta2 = myScrollbar.getWheelDelta() * 2;
    myUi.mouse.wheel(50, 50, -2);
    assertEquals(0 + (delta + delta2) * 0.5, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 - (delta + delta2) * 0.5, myTimeline.getViewRange().getMax(), 0.001);

    // Zoom out
    double delta3 = myScrollbar.getWheelDelta();
    myUi.mouse.wheel(50, 50, 1);
    assertEquals(0 + (delta + delta2 - delta3) * 0.5, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 - (delta + delta2 - delta3) * 0.5, myTimeline.getViewRange().getMax(), 0.001);
  }

  @Test
  public void testPan() {
    double delta = myScrollbar.getWheelDelta();
    myUi.mouse.wheel(50, 50, 1);
    assertEquals(0 + delta, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 + delta, myTimeline.getViewRange().getMax(), 0.001);

    myUi.mouse.wheel(50, 50, 2);
    assertEquals(0 + delta * 3, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 + delta * 3, myTimeline.getViewRange().getMax(), 0.001);

    myUi.mouse.wheel(50, 50, -3);
    assertEquals(0, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000, myTimeline.getViewRange().getMax(), 0.001);
  }

  @Test
  public void testAdjust() {

    assertEquals(0, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000, myTimeline.getViewRange().getMax(), 0.001);

    myUi.mouse.press(5, 100);

    assertEquals(0, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000, myTimeline.getViewRange().getMax(), 0.001);

    myUi.mouse.dragTo(10, 100);

    assertEquals(1000, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(6000, myTimeline.getViewRange().getMax(), 0.001);

    myUi.mouse.release();

    assertEquals(1000, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(6000, myTimeline.getViewRange().getMax(), 0.001);

    // Scroll to the end, and check streaming
    assertFalse(myTimeline.isStreaming());
    myUi.mouse.press(10, 100);
    assertFalse(myTimeline.isStreaming());
    myUi.mouse.dragTo(40, 100);
    assertFalse(myTimeline.isStreaming());
    myUi.mouse.release();

    assertEquals(4000, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(9000, myTimeline.getViewRange().getMax(), 0.001);

    BufferedImage image = new BufferedImage(100, 10, BufferedImage.TYPE_INT_ARGB);
    Graphics graphics = image.getGraphics();
    graphics.setClip(0, 0, 100, 10);
    myScrollbar.paintComponent(graphics);
    assertTrue(myTimeline.isStreaming());
  }
}
