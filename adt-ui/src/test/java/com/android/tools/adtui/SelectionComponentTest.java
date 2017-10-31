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
import com.android.tools.adtui.model.SelectionListener;
import com.android.tools.adtui.model.SelectionModel;
import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeUi;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SelectionComponentTest {

  private static final double DELTA = 1e-3;

  @Test
  public void clickingInViewRangeCreatesPointSelectionRange() {
    SelectionModel model = new SelectionModel(new Range());
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    assertTrue(model.getSelectionRange().isEmpty());
    new FakeUi(component).mouse.click(20, 0);
    assertEquals(20, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());
  }

  @Test
  public void clickingOutsideOfSelectionCreatesNewSelection() {
    SelectionModel model = new SelectionModel(new Range(20, 40));
    SelectionComponent component = new SelectionComponent(model, new Range(20, 120));
    component.setSize(100, 100);
    assertEquals(20, model.getSelectionRange().getMin(), DELTA);
    assertEquals(40, model.getSelectionRange().getMax(), DELTA);
    new FakeUi(component).mouse.click(60, 0);
    assertEquals(80, model.getSelectionRange().getMin(), DELTA);
    assertEquals(80, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());
  }

  @Test
  public void pressingEscapeClearsSelection() {
    SelectionModel model = new SelectionModel(new Range(40, 60));
    SelectionComponent component = new SelectionComponent(model, new Range(20, 120));
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
    SelectionModel model = new SelectionModel(new Range());
    model.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        event[0] = 1;
      }
    });
    SelectionComponent component = new SelectionComponent(model, new Range(20, 120));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(50, 0);
    assertEquals(0, event[0]);
    ui.mouse.release();
    assertEquals(1, event[0]);
  }

  @Test
  public void canDragMinHandleToLowerValue() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMinHandleX(model), 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());
    ui.mouse.dragDelta(-5, 0);
    assertEquals(5, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());
  }

  @Test
  public void canDragMaxHandleToHigherValue() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMaxHandleX(model), 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR), component.getCursor());
    ui.mouse.dragDelta(20, 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(40, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR), component.getCursor());
  }

  @Test
  public void canDragSelectionToPan() {
    SelectionModel model = new SelectionModel(new Range(40, 50));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(45, 0);
    assertEquals(40, model.getSelectionRange().getMin(), DELTA);
    assertEquals(50, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR), component.getCursor());
    ui.mouse.dragDelta(40, 0);
    assertEquals(80, model.getSelectionRange().getMin(), DELTA);
    assertEquals(90, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR), component.getCursor());
  }

  @Test
  public void draggingMinHandleAboveMaxHandleSwapsThem() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMinHandleX(model), 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());
    ui.mouse.dragDelta(90, 0);
    assertEquals(20, model.getSelectionRange().getMin(), DELTA);
    assertEquals(100, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR), component.getCursor());
  }

  @Test
  public void draggingMaxHandleBelowMinHandleSwapsThem() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMaxHandleX(model), 0);
    assertEquals(10, model.getSelectionRange().getMin(), DELTA);
    assertEquals(20, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR), component.getCursor());
    ui.mouse.dragDelta(-20, 0);
    assertEquals(0, model.getSelectionRange().getMin(), DELTA);
    assertEquals(10, model.getSelectionRange().getMax(), DELTA);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());
  }

  @Test
  public void leftKeyUpdatesModel() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.keyboard.setFocus(component);
    // Test no modifier keys shifts the entire model left.
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.LEFT, 9,19);

    // Test shift expands the selection by shifting the min range but not the max.
    ui.keyboard.press(FakeKeyboard.Key.SHIFT);
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.LEFT, 8,19);
    ui.keyboard.release(FakeKeyboard.Key.SHIFT);

    // Test alt shrinks the selection by shifting the max range but not the min.
    ui.keyboard.press(FakeKeyboard.Key.ALT);
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.LEFT, 8,18);
  }

  @Test
  public void rightKeyUpdatesModel() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.keyboard.setFocus(component);

    // Test no modifier keys shifts the entire model right.
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.RIGHT, 11,21);

    // Test shift expands the selection by shifting the max range but not the min.
    ui.keyboard.press(FakeKeyboard.Key.SHIFT);
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.RIGHT, 11,22);
    ui.keyboard.release(FakeKeyboard.Key.SHIFT);

    // Test alt shrinks the selection by shifting the min range but not the max.
    ui.keyboard.press(FakeKeyboard.Key.ALT);
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.RIGHT, 12,22);
  }

  @Test
  public void movingMouseChangesCursor() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);

    // Moving to min handle should change cursor to east resize cursor.
    ui.mouse.moveTo(getMinHandleX(model), 0);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());

    // Moving inside the range should change cursor to move cursor.
    ui.mouse.moveTo(15, 0);
    assertEquals(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR), component.getCursor());

    // Moving to max handle should change cursor to west resize cursor.
    ui.mouse.moveTo(getMaxHandleX(model), 50);
    assertEquals(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR), component.getCursor());

    // Moving outside the range should change cursor to default.
    ui.mouse.moveTo(0, 0);
    assertEquals(Cursor.getDefaultCursor(), component.getCursor());
  }

  @Test
  public void creatingNewSelectionChangesCursor() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);

    ui.mouse.press(30, 0);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());
    ui.mouse.dragTo(40, 0);
    assertEquals(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR), component.getCursor());
    ui.mouse.dragTo(20, 0);
    assertEquals(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR), component.getCursor());
    ui.mouse.release();
  }

  private void shiftAndValidateShift(FakeUi ui, SelectionModel model, FakeKeyboard.Key key, int min, int max) {
    ui.keyboard.press(key);
    assertEquals(min, model.getSelectionRange().getMin(), DELTA);
    assertEquals(max, model.getSelectionRange().getMax(), DELTA);
    ui.keyboard.release(key);
  }

  private static int getMinHandleX(SelectionModel model) {
    return (int)model.getSelectionRange().getMin() - (SelectionComponent.HANDLE_WIDTH / 2);
  }

  private static int getMaxHandleX(SelectionModel model) {
    return (int)model.getSelectionRange().getMax() + (SelectionComponent.HANDLE_WIDTH / 2);
  }
}
