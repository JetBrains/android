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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tools.adtui.common.AdtUiCursorType;
import com.android.tools.adtui.common.AdtUiCursorsProvider;
import com.android.tools.adtui.common.AdtUiCursorsTestUtil;
import com.android.tools.adtui.common.TestAdtUiCursorsProvider;
import com.android.tools.adtui.model.DefaultConfigurableDurationData;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionListener;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeUi;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.ServiceContainerUtil;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;

public class RangeSelectionComponentTest {

  private static final double DELTA = 1e-3;

  @ClassRule
  public static final ApplicationRule appRule = new ApplicationRule();

  @Before
  public void setup() {
    ServiceContainerUtil.registerServiceInstance(
      ApplicationManager.getApplication(),
      AdtUiCursorsProvider.class,
      new TestAdtUiCursorsProvider());
    AdtUiCursorsTestUtil.replaceAdtUiCursorWithPredefinedCursor(AdtUiCursorType.GRAB, Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    AdtUiCursorsTestUtil.replaceAdtUiCursorWithPredefinedCursor(AdtUiCursorType.GRABBING, Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
  }

  @Test
  public void clickingInViewRangeCreatesPointSelectionRange() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setSize(100, 100);
    assertThat(model.getSelectionRange().isEmpty()).isTrue();
    new FakeUi(component).mouse.click(20, 50);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(20);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(20);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
  }

  @Test
  public void clickingInViewRangeCreatesSmallSelectionRange() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model, true);
    component.setSize(100, 100);
    assertThat(model.getSelectionRange().isEmpty()).isTrue();
    new FakeUi(component).mouse.click(20, 50);
    double delta = 100.0 * RangeSelectionComponent.CLICK_RANGE_RATIO;
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(20 - delta);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(20 + delta);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
  }

  @Test
  public void clickingOutsideOfSelectionCreatesNewSelection() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(20, 40), new Range(20, 120));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
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
    RangeSelectionModel model = new RangeSelectionModel(new Range(40, 60), new Range(20, 120));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setSize(100, 100);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(40);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(60);
    FakeUi ui = new FakeUi(component);
    ui.keyboard.setFocus(component);
    ui.keyboard.press(FakeKeyboard.Key.ESC);
    assertThat(model.getSelectionRange().isEmpty()).isTrue();
  }

  @Test
  public void doubleClickingClearsSelection() throws Exception {
    RangeSelectionModel model = new RangeSelectionModel(new Range(40, 60), new Range(20, 120));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setSize(100, 100);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(40);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(60);
    FakeUi ui = new FakeUi(component);
    // Assert click does not clear selection.
    ui.mouse.click(50, 50);
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE);
    assertThat(model.getSelectionRange().isEmpty()).isFalse();
    ui.mouse.doubleClick(50, 50);
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE);
    assertThat(model.getSelectionRange().isEmpty()).isTrue();
  }

  @Test
  public void selectionModelReceivesMouseClick() {
    int[] event = new int[1];
    RangeSelectionModel model = new RangeSelectionModel(new Range(), new Range(20, 120));
    model.addListener(new RangeSelectionListener() {
      @Override
      public void selectionCreated() {
        event[0]++;
      }
    });
    RangeSelectionComponent component = new RangeSelectionComponent(model);
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

    RangeSelectionModel model = new RangeSelectionModel(new Range(), new Range(20, 120));
    model.addListener(new RangeSelectionListener() {
      @Override
      public void selectionCleared() {
        cleared[0]++;
      }

      @Override
      public void selectionCreated() {
        created[0]++;
      }
    });
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);

    ui.mouse.press(50, 0);
    ui.mouse.dragDelta(5, 0);
    ui.mouse.release();
    assertThat(cleared[0]).isEqualTo(0);
    assertThat(created[0]).isEqualTo(1);

    // Click outside the recently created selection, to create a new one.
    ui.mouse.press(70, 0);
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
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
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
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
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
    RangeSelectionModel model = new RangeSelectionModel(new Range(40, 50), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setDragBarHeight(10);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(45, 9);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(40);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(50);
    assertThat(component.getCursor()).isEqualTo(AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRABBING));
    ui.mouse.dragDelta(40, 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(80);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(90);
    ui.mouse.release();
    assertThat(component.getCursor()).isEqualTo(AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRAB));
  }

  @Test
  public void canMakeNewSelectionInSelection() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(40, 50), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setDragBarHeight(20);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.press(45, 21);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(45);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(45);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    ui.mouse.dragDelta(40, 0);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(45);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(85);
    ui.mouse.release();
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
  }

  @Test
  public void mouseChangesWithConstraints() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(40, 50), new Range(0, 100));
    DefaultDataSeries<DefaultConfigurableDurationData> series = new DefaultDataSeries<>();
    RangedSeries<DefaultConfigurableDurationData> ranged = new RangedSeries<>(new Range(0, 100), series);
    DurationDataModel<DefaultConfigurableDurationData> constraint = new DurationDataModel<>(ranged);
    series.add(41, new DefaultConfigurableDurationData(5, true, true));
    model.addConstraint(constraint);
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.mouse.moveTo(45, 50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    ui.mouse.moveTo(30,50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getDefaultCursor());
  }

  @Test
  public void draggingMinHandleAboveMaxHandleSwapsThem() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
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
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
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
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.keyboard.setFocus(component);
    // Test no modifier keys shifts the entire model left.
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.LEFT, 9, 19);

    // Test shift expands the selection by shifting the min range but not the max.
    ui.keyboard.press(FakeKeyboard.Key.SHIFT);
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.LEFT, 8, 19);
    ui.keyboard.release(FakeKeyboard.Key.SHIFT);

    // Test alt shrinks the selection by shifting the max range but not the min.
    ui.keyboard.press(FakeKeyboard.Key.ALT);
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.LEFT, 8,18);
  }

  @Test
  public void rightKeyUpdatesModel() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    ui.keyboard.setFocus(component);

    // Test no modifier keys shifts the entire model right.
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.RIGHT, 11, 21);

    // Test shift expands the selection by shifting the max range but not the min.
    ui.keyboard.press(FakeKeyboard.Key.SHIFT);
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.RIGHT, 11, 22);
    ui.keyboard.release(FakeKeyboard.Key.SHIFT);

    // Test alt shrinks the selection by shifting the min range but not the max.
    ui.keyboard.press(FakeKeyboard.Key.ALT);
    shiftAndValidateShift(ui, model, FakeKeyboard.Key.RIGHT, 12,22);
  }

  @Test
  public void movingMouseChangesCursor() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);

    // Moving to min handle should change cursor to east resize cursor.
    ui.mouse.moveTo(getMinHandleX(model), 0);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    assertThat(component.getMode()).isEqualTo(RangeSelectionComponent.Mode.ADJUST_MIN);

    // Moving inside the range should change cursor to default cursor.
    ui.mouse.moveTo(15, 0);
    assertThat(component.getCursor()).isEqualTo(AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRABBING));
    assertThat(component.getMode()).isEqualTo(RangeSelectionComponent.Mode.MOVE);

    // Moving inside the range should change cursor to default cursor.
    ui.mouse.moveTo(15, 50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    assertThat(component.getMode()).isEqualTo(RangeSelectionComponent.Mode.CREATE);

    // Moving to max handle should change cursor to west resize cursor.
    ui.mouse.moveTo(getMaxHandleX(model), 50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
    assertThat(component.getMode()).isEqualTo(RangeSelectionComponent.Mode.ADJUST_MAX);

    // Moving outside the range should change cursor to default.
    ui.mouse.moveTo(0, 0);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
    assertThat(component.getMode()).isEqualTo(RangeSelectionComponent.Mode.CREATE);
  }

  @Test
  public void creatingNewSelectionChangesCursor() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
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

  @Test
  public void componentIsNotDrawnIfInvisible() {
    Range selectionRange = new Range (60, 70);
    Range viewRange = new Range(30, 50);
    RangeSelectionComponent component = new RangeSelectionComponent(new RangeSelectionModel(selectionRange, viewRange));
    Dimension dimension = new Dimension(100, 100);
    component.setSize(dimension);
    Graphics2D graphics = mock(Graphics2D.class);
    component.draw(graphics, dimension);
    Mockito.verifyNoMoreInteractions(graphics);
    selectionRange.set(10, 20);
    component.draw(graphics, dimension);
    Mockito.verifyNoMoreInteractions(graphics);
    selectionRange.set(0, -1);
    component.draw(graphics, dimension);
    Mockito.verifyNoMoreInteractions(graphics);
  }

  @Test
  public void repaintIsCalledOnMouseMove() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    component.setOpaque(false);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);
    // Create an opaque parent for us to intercept the repaint call on.
    JComponent parent = spy(new JPanel());
    parent.add(component);
    // Verify no repaint has been called to this point.
    verify(parent, times(0)).repaint();
    // Move and verify we get a repaint.
    ui.mouse.moveTo(30, 0);
    verify(parent, times(1)).repaint();
  }

  @Test
  public void componentRemovalRestoresCursor() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(component, BorderLayout.CENTER);
    panel.setSize(100, 100);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(panel);

    ui.mouse.moveTo(30, 30);
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));

    panel.removeAll();
    assertThat(component.getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }

  @Test
  public void occludedRangeSetsDefaultCursor() {
    RangeSelectionModel model = new RangeSelectionModel(new Range(10, 20), new Range(0, 100));
    RangeSelectionComponent component = new RangeSelectionComponent(model);
    boolean[] isOccluded = {true};
    component.setRangeOcclusionTest(() -> isOccluded[0]);
    component.setSize(100, 100);
    FakeUi ui = new FakeUi(component);

    // State marked as occluded, cursor should be default
    ui.mouse.moveTo(getMinHandleX(model), 0);
    assertThat(component.getCursor()).isEqualTo(Cursor.getDefaultCursor());

    // State marked as clear, cursor should be up to the selection component
    isOccluded[0] = false;
    ui.mouse.moveTo(15, 0);
    assertThat(component.getCursor()).isEqualTo(AdtUiCursorsProvider.getInstance().getCursor(AdtUiCursorType.GRABBING));

    // State marked as occluded again
    isOccluded[0] = true;
    ui.mouse.moveTo(15, 50);
    assertThat(component.getCursor()).isEqualTo(Cursor.getDefaultCursor());
  }

  private static void shiftAndValidateShift(FakeUi ui, RangeSelectionModel model, FakeKeyboard.Key key, int min, int max) {
    ui.keyboard.press(key);
    assertThat(model.getSelectionRange().getMin()).isWithin(DELTA).of(min);
    assertThat(model.getSelectionRange().getMax()).isWithin(DELTA).of(max);
    ui.keyboard.release(key);
  }

  private static int getMinHandleX(RangeSelectionModel model) {
    return (int)(model.getSelectionRange().getMin() - (RangeSelectionComponent.HANDLE_HITBOX_WIDTH / 2));
  }

  private static int getMaxHandleX(RangeSelectionModel model) {
    return (int)(model.getSelectionRange().getMax() + (RangeSelectionComponent.HANDLE_HITBOX_WIDTH / 2));
  }
}
