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

import org.junit.Before;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class ProfilerScrollbarTests {

  private ProfilerTimeline myTimeline;
  private ProfilerScrollbar myScrollbar;
  private JPanel myPanel;

  @Before
  public void setUp() throws Exception {
    myTimeline = new ProfilerTimeline();
    myPanel = new JPanel();
    myPanel.setSize(100, 100);
    myScrollbar = new ProfilerScrollbar(myTimeline, myPanel);
    myScrollbar.setSize(100, 10);

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
    mouseWheel(50, 50, Event.ALT_MASK, -1);
    assertEquals(0 + delta * 0.5, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 - delta * 0.5, myTimeline.getViewRange().getMax(), 0.001);

    // Zoom in twice
    double delta2 = myScrollbar.getWheelDelta() * 2;
    mouseWheel(50, 50, Event.ALT_MASK, -2);
    assertEquals(0 + (delta + delta2) * 0.5, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 - (delta + delta2) * 0.5, myTimeline.getViewRange().getMax(), 0.001);

    // Zoom out
    double delta3 = myScrollbar.getWheelDelta();
    mouseWheel(50, 50, Event.ALT_MASK, 1);
    assertEquals(0 + (delta + delta2 - delta3) * 0.5, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 - (delta + delta2 - delta3) * 0.5, myTimeline.getViewRange().getMax(), 0.001);
  }

  @Test
  public void testPan() {
    double delta = myScrollbar.getWheelDelta();

    mouseWheel(50, 50, 0, 1);
    assertEquals(0 + delta, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 + delta, myTimeline.getViewRange().getMax(), 0.001);

    mouseWheel(50, 50, 0, 2);
    assertEquals(0 + delta * 3, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000 + delta * 3, myTimeline.getViewRange().getMax(), 0.001);

    mouseWheel(50, 50, 0, -3);
    assertEquals(0, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000, myTimeline.getViewRange().getMax(), 0.001);
  }

  @Test
  public void testAdjust() {

    assertEquals(0, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000, myTimeline.getViewRange().getMax(), 0.001);

    mouse(MouseEvent.MOUSE_PRESSED, 5, 5);

    assertEquals(0, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(5000, myTimeline.getViewRange().getMax(), 0.001);

    mouse(MouseEvent.MOUSE_DRAGGED, 10, 5);

    assertEquals(1000, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(6000, myTimeline.getViewRange().getMax(), 0.001);

    mouse(MouseEvent.MOUSE_RELEASED, 10, 5);

    assertEquals(1000, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(6000, myTimeline.getViewRange().getMax(), 0.001);

    // Scroll to the end, and check streaming
    assertFalse(myTimeline.isStreaming());
    mouse(MouseEvent.MOUSE_PRESSED, 10, 5);
    assertFalse(myTimeline.isStreaming());
    mouse(MouseEvent.MOUSE_DRAGGED, 40, 5);
    assertFalse(myTimeline.isStreaming());
    mouse(MouseEvent.MOUSE_RELEASED, 40, 5);

    assertEquals(4000, myTimeline.getViewRange().getMin(), 0.001);
    assertEquals(9000, myTimeline.getViewRange().getMax(), 0.001);

    BufferedImage image = new BufferedImage(100, 10, BufferedImage.TYPE_INT_ARGB);
    Graphics graphics = image.getGraphics();
    graphics.setClip(0, 0, 100, 10);
    myScrollbar.paintComponent(graphics);
    assertTrue(myTimeline.isStreaming());
  }

  private void mouse(int id, int x, int y) {
    MouseEvent event = new MouseEvent(myScrollbar, id, System.nanoTime(), 0, x, y, 1, false, MouseEvent.BUTTON1);
    myScrollbar.dispatchEvent(event);
  }

  private void mouseWheel(int x, int y, int modifiers, int rotation) {
    MouseWheelEvent event = new MouseWheelEvent(myPanel, MouseEvent.MOUSE_WHEEL, System.nanoTime(), modifiers, x, y, 0, false,
                                                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation);
    myPanel.dispatchEvent(event);
  }
}
