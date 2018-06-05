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

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.ui.AdtUiCursors;
import org.junit.Test;

import java.awt.*;

import static com.google.common.truth.Truth.assertThat;

public class SelectionComponentTest {

  private static final double DELTA = 1e-3;

  @Test
  public void clickingInViewRangeCreatesPointSelectionRange() {
    SelectionModel model = new SelectionModel(new Range());
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    assertThat(model.getSelectionRange().isEmpty()).isTrue();
    new FakeUi(component).mouse.click(20, 50);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(20);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(20);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
  }

  @Test
  public void clickingOutsideOfSelectionCreatesNewSelection() {
    SelectionModel model = new SelectionModel(new Range(20, 40));
    SelectionComponent component = new SelectionComponent(model, new Range(20, 120));
    component.setSize(100, 100);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(20);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(40);
    new FakeUi(component).mouse.click(60, 50);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(80);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(80);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
  }

  @Test
  public void pressingEscapeClearsSelection() {
    SelectionModel model = new SelectionModel(new Range(40, 60));
    SelectionComponent component = new SelectionComponent(model, new Range(20, 120));
    component.setSize(100, 100);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(40);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(60);
    FakeUi ui = new FakeUi(component);
    ui.keyboard.setFocus(component);
    ui.keyboard.press(FakeKeyboard.Key.ESC);
    assertThat(model.getSelectionRange().isEmpty()).isTrue();
  }

  @Test
  public void selectionModelReceivesMouseClick() {
    int[] event = new int[1];
    SelectionModel model = new SelectionModel(new Range());
    model.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        event[0]++;
      }
    });
    SelectionComponent component = new SelectionComponent(model, new Range(20, 120));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(50, 0);
    assertThat(event[0]).isEqualTo(0);
    ui.mouse.release();
    assertThat(event[0]).isEqualTo(1);
  }

  @Test
  public void createSelectionEventIsFiredForEachTimeANewSelectionRangeIsCreated() {
    int[] cleared = new int[1];
    int[] created = new int[1];

    SelectionModel model = new SelectionModel(new Range());
    model.addListener(new SelectionListener() {
      @Override
      public void selectionCleared() {
        cleared[0]++;
      }

      @Override
      public void selectionCreated() {
        created[0]++;
      }
    });
    SelectionComponent component = new SelectionComponent(model, new Range(20, 120));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);

    ui.mouse.press(50, 0);
    ui.mouse.dragDelta(5, 0);
    ui.mouse.release();
    assertThat(cleared[0]).isEqualTo(0);
    assertThat(created[0]).isEqualTo(1);

    // Click outside the recently created selection, to create a new one.
    ui.mouse.press(60, 0);
    ui.mouse.dragDelta(10, 0);
    ui.mouse.release();
    assertThat(cleared[0]).isEqualTo(0);
    assertThat(created[0]).isEqualTo(2);

    // Drag inside the previous selection, to move it.
    // Sanity check this does not fire a creation event.
    ui.mouse.press(65, 0);
    ui.mouse.dragDelta(-15, 0);
    ui.mouse.release();
    assertThat(cleared[0]).isEqualTo(0);
    assertThat(created[0]).isEqualTo(2);
  }

  @Test
  public void canDragMinHandleToLowerValue() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMinHandleX(model), 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(10);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(20);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    ui.mouse.dragDelta(-5, 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(5);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(20);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
  }

  @Test
  public void canDragMaxHandleToHigherValue() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMaxHandleX(model), 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(10);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(20);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    ui.mouse.dragDelta(20, 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(10);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(40);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
  }

  @Test
  public void canDragSelectionToPan() {
    SelectionModel model = new SelectionModel(new Range(40, 50));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(45, 15);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(40);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(50);
    assertThat(component.getCursor()).isEqualTo(AdtUiCursors.GRABBING);
    ui.mouse.dragDelta(40, 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(80);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(90);
    ui.mouse.release();
    assertThat(component.getCursor()).isEqualTo(AdtUiCursors.GRAB);
  }

  @Test
  public void canMakeNewSelectionInSelection() {
    SelectionModel model = new SelectionModel(new Range(40, 50));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(45, 50);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(45);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(45);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    ui.mouse.dragDelta(40, 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(45);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(85);
    ui.mouse.release();
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
  }

  @Test
  public void mouseChangesWithConstraints() {
    SelectionModel model = new SelectionModel(new Range(40, 50));
    DefaultDataSeries<DefaultConfigurableDurationData> series = new DefaultDataSeries<>();
    RangedSeries<DefaultConfigurableDurationData> ranged = new RangedSeries<>(new Range(0, 100), series);
    DurationDataModel<DefaultConfigurableDurationData> constraint = new DurationDataModel<>(ranged);
    series.add(41, new DefaultConfigurableDurationData(5, true, true));
    model.addConstraint(constraint);
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.moveTo(45, 50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    ui.mouse.moveTo(30,50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getDefaultCursor());
  }

  @Test
  public void draggingMinHandleAboveMaxHandleSwapsThem() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMinHandleX(model), 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(10);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(20);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    ui.mouse.dragDelta(90, 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(20);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(100);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
  }

  @Test
  public void draggingMaxHandleBelowMinHandleSwapsThem() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(getMaxHandleX(model), 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(10);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(20);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    ui.mouse.dragDelta(-20, 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(0);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(10);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
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
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    assertThat(component.getMode()).isEqualTo(SelectionComponent.Mode.ADJUST_MIN);

    // Moving inside the range should change cursor to default cursor.
    ui.mouse.moveTo(15, 0);
    assertThat(component.getCursor()).isEqualTo(AdtUiCursors.GRABBING);
    assertThat(component.getMode()).isEqualTo(SelectionComponent.Mode.MOVE);

    // Moving inside the range should change cursor to default cursor.
    ui.mouse.moveTo(15, 50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    assertThat(component.getMode()).isEqualTo(SelectionComponent.Mode.CREATE);

    // Moving to max handle should change cursor to west resize cursor.
    ui.mouse.moveTo(getMaxHandleX(model), 50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    assertThat(component.getMode()).isEqualTo(SelectionComponent.Mode.ADJUST_MAX);

    // Moving outside the range should change cursor to default.
    ui.mouse.moveTo(0, 0);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    assertThat(component.getMode()).isEqualTo(SelectionComponent.Mode.CREATE);
  }

  @Test
  public void creatingNewSelectionChangesCursor() {
    SelectionModel model = new SelectionModel(new Range(10, 20));
    SelectionComponent component = new SelectionComponent(model, new Range(0, 100));
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);

    ui.mouse.press(30, 0);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    ui.mouse.dragTo(40, 0);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    ui.mouse.dragTo(20, 0);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    ui.mouse.release();
  }

  private void shiftAndValidateShift(FakeUi ui, SelectionModel model, FakeKeyboard.Key key, int min, int max) {
    ui.keyboard.press(key);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(min);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(max);
    ui.keyboard.release(key);
  }

  private static int getMinHandleX(SelectionModel model) {
    return (int)model.getSelectionRange().getMin() - (SelectionComponent.HANDLE_WIDTH / 2);
  }

  private static int getMaxHandleX(SelectionModel model) {
    return (int)model.getSelectionRange().getMax() + (SelectionComponent.HANDLE_WIDTH / 2);
  }
}
