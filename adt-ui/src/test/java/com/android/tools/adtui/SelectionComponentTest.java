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
package com.android.tools.adtui;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SelectionModel;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.event.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.when;

public class SelectionComponentTest {

  private static final double DELTA = 1e-3;

  @Test
  public void testMousePressToSelectRange() {
    SelectionModel model = new SelectionModel(new Range(0, 0), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    assertEquals(0, model.getSelectionRange().getMin(), DELTA);
    assertEquals(0, model.getSelectionRange().getMax(), DELTA);
    mousePress(component, getEventAtX(20));
    assertEquals(20, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void testMousePressWithNonZeroStartX() {
    SelectionModel model = new SelectionModel(new Range(20, 40), new Range(20, 120));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    assertEquals(20, model.getSelectionRange().getMin(), DELTA);
    assertEquals(40, model.getSelectionRange().getMax(), DELTA);
    mousePress(component, getEventAtX(60));
    assertEquals(80, model.getSelectionRange().getMin(), DELTA);
    assertEquals(80, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void testKeyPressClearSelection() {
    SelectionModel model = new SelectionModel(new Range(40, 60), new Range(20, 120));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    assertEquals(40, model.getSelectionRange().getMin(), DELTA);
    assertEquals(60, model.getSelectionRange().getMax(), DELTA);
    keyPress(component, getEventForExtendedKeyCode(KeyEvent.VK_ESCAPE));
    assertNotEquals(40, model.getSelectionRange().getMin(), DELTA);
    assertNotEquals(60, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void testMouseReleaseToFireEventInModel() {
    int[] event = new int[1];
    SelectionModel model = new SelectionModel(new Range(40, 60), new Range(20, 120));
    model.addChangeListener(e -> event[0] = 1);
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);

    mousePress(component, getEventAtX(50));
    assertEquals(0, event[0]);
    mouseRelease(component, getEventAtX(50));
    assertEquals(1, event[0]);
  }

  @Test
  public void testMouseDragToAdjustMin() {
    SelectionModel model = new SelectionModel(new Range(10, 20), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    mousePress(component, getEventAtX(getAdjustMinX(10)));
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    mouseDrag(component, getEventAtX(5));
    assertEquals(5, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void testMouseDragToAdjustMax() {
    SelectionModel model = new SelectionModel(new Range(10, 20), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    mousePress(component, getEventAtX(getAdjustMaxX(20)));
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    mouseDrag(component, getEventAtX(40));
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(40, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void testMove() {
    SelectionModel model = new SelectionModel(new Range(40, 50), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    mousePress(component, getEventAtX(45));
    assertEquals(40, model.getSelectionRange().getMin(), DELTA);
    assertEquals(50, model.getSelectionRange().getMax(), DELTA);
    mouseDrag(component, getEventAtX(85));
    assertEquals(80, model.getSelectionRange().getMin(), DELTA);
    assertEquals(90, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void testDragToMaximumAfterAdjustMin() {
    SelectionModel model = new SelectionModel(new Range(10, 20), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    mousePress(component, getEventAtX(getAdjustMinX(10)));
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    mouseDrag(component, getEventAtX(100));
    assertEquals(100, model.getSelectionRange().getMin(), DELTA);
    assertEquals(100, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void testDragToMinimumAfterAdjustMax() {
    SelectionModel model = new SelectionModel(new Range(10, 20), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    mousePress(component, getEventAtX(getAdjustMaxX(20)));
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    mouseDrag(component, getEventAtX(0));
    assertEquals(0, model.getSelectionRange().getMin(), DELTA);
    assertEquals(0, model.getSelectionRange().getMax(), DELTA);
  }

  private static MouseEvent getEventAtX(int x) {
    return new MouseEvent(new JPanel(), 0, 0, 0, x, 0, 1, false, 0);
  }

  // No public API to set extended key, so use mock.
  private static KeyEvent getEventForExtendedKeyCode(int code) {
    KeyEvent e = Mockito.mock(KeyEvent.class);
    when(e.getExtendedKeyCode()).thenReturn(code);
    return e;
  }

  private static void mousePress(SelectionComponent component, MouseEvent e) {
    for (MouseListener listener : component.getMouseListeners()) {
      listener.mousePressed(e);
    }
  }

  private static void mouseRelease(SelectionComponent component, MouseEvent e) {
    for (MouseListener listener : component.getMouseListeners()) {
      listener.mouseReleased(e);
    }
  }

  private static void mouseDrag(SelectionComponent component, MouseEvent e) {
    for (MouseMotionListener listener : component.getMouseMotionListeners()) {
      listener.mouseDragged(e);
    }
  }

  private static void keyPress(SelectionComponent component, KeyEvent e) {
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyPressed(e);
    }
  }

  private static int getAdjustMinX(int selectionMin) {
    return selectionMin - SelectionComponent.HANDLE_WIDTH + 1;
  }

  private static int getAdjustMaxX(int selectionMax) {
    return selectionMax + SelectionComponent.HANDLE_WIDTH - 1;
  }
}
