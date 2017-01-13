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
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.swing.FakeKeyboard;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SelectionComponentTest {

  private static final double DELTA = 1e-3;

  @Test
  public void clickingInViewRangeCreatesPointSelectionRange() {
    SelectionModel model = new SelectionModel(new Range(), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    assertTrue(model.getSelectionRange().isEmpty());
    new FakeUi(component).mouse.click(20, 0);
    assertEquals(20, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void clickingOutsideOfSelectionCreatesNewSelection() {
    SelectionModel model = new SelectionModel(new Range(20, 40), new Range(20, 120));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    assertEquals(20, model.getSelectionRange().getMin(), DELTA);
    assertEquals(40, model.getSelectionRange().getMax(), DELTA);
    new FakeUi(component).mouse.click(60, 0);
    assertEquals(80, model.getSelectionRange().getMin(), DELTA);
    assertEquals(80, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void pressingEscapeClearsSelection() {
    SelectionModel model = new SelectionModel(new Range(40, 60), new Range(20, 120));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    assertEquals(40, model.getSelectionRange().getMin(), DELTA);
    assertEquals(60, model.getSelectionRange().getMax(), DELTA);
    FakeUi ui = new FakeUi(component);
    ui.keyboard.setFocus(component);
    ui.keyboard.press(FakeKeyboard.Key.ESC);
    assertTrue(model.getSelectionRange().isEmpty());
  }

  @Test
  public void selectionModelReceivesMouseClick() {
    int[] event = new int[1];
    SelectionModel model = new SelectionModel(new Range(40, 60), new Range(20, 120));
    model.addChangeListener(e -> event[0] = 1);
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(50, 0);
    assertEquals(0, event[0]);
    ui.mouse.release();
    assertEquals(1, event[0]);
  }

  @Test
  public void canDragMinHandleToLowerValue() {
    SelectionModel model = new SelectionModel(new Range(10, 20), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMinHandleX(model), 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    ui.mouse.dragDelta(-5, 0);
    assertEquals(5, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void canDragMaxHandleToHigherValue() {
    SelectionModel model = new SelectionModel(new Range(10, 20), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMaxHandleX(model), 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    ui.mouse.dragDelta(20, 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(40, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void canDragSelectionToPan() {
    SelectionModel model = new SelectionModel(new Range(40, 50), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(45, 0);
    assertEquals(40, model.getSelectionRange().getMin(), DELTA);
    assertEquals(50, model.getSelectionRange().getMax(), DELTA);
    ui.mouse.dragDelta(40, 0);
    assertEquals(80, model.getSelectionRange().getMin(), DELTA);
    assertEquals(90, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void draggingMinHandleAboveMaxHandleSwapsThem() {
    SelectionModel model = new SelectionModel(new Range(10, 20), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMinHandleX(model), 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    ui.mouse.dragDelta(90, 0);
    assertEquals(20, model.getSelectionRange().getMin(), DELTA);
    assertEquals(100, model.getSelectionRange().getMax(), DELTA);
  }

  @Test
  public void draggingMaxHandleBelowMinHandleSwapsThem() {
    SelectionModel model = new SelectionModel(new Range(10, 20), new Range(0, 100));
    SelectionComponent component = new SelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMaxHandleX(model), 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    ui.mouse.dragDelta(-20, 0);
    assertEquals(0, model.getSelectionRange().getMin(), DELTA);
    assertEquals(10, model.getSelectionRange().getMax(), DELTA);
  }

  private static int getMinHandleX(SelectionModel model) {
    return (int)model.getSelectionRange().getMin() - (SelectionComponent.HANDLE_WIDTH / 2);
  }

  private static int getMaxHandleX(SelectionModel model) {
    return (int)model.getSelectionRange().getMax() + (SelectionComponent.HANDLE_WIDTH / 2);
  }
}
