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
package com.android.tools.adtui.model;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class SelectionModelTest {
  private Range mySelection;
  private Range myRange;

  @Before
  public void setUp() throws Exception {
    mySelection = new Range();
    myRange = new Range(0, 100);
  }

  @Test
  public void testSetWithNoConstraints() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.set(10, 20);
    assertEquals(10, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(20, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testSetWithPartialConstraint() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));

    selection.set(10, 18);

    assertEquals(15, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(18, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testSetWithPartialConstraintEmpty() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));

    selection.set(10, 12);

    assertTrue(mySelection.isEmpty());
  }

  @Test
  public void testSelectionPrefersCurrentOne() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));
    selection.set(18, 20);
    assertEquals(18, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(20, mySelection.getMax(), Float.MIN_VALUE);

    // This overlaps with two constraints, it should choose the one it's using
    selection.set(3, 18);
    assertEquals(15, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(18, mySelection.getMax(), Float.MIN_VALUE);

    // In both directions
    selection.set(16, 39);
    assertEquals(16, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(20, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testFullSelection() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(10, 18);

    assertEquals(15, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(20, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testNestedFullConstraints() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(0, 1);
    assertEquals(0, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(5, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(2.5, 2.6);
    assertEquals(2, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(3, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(4, 5);
    assertEquals(0, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(5, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(35, 36);
    assertEquals(35, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(40, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(38.5, 38.6);
    assertEquals(38, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(39, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(39.5, 39.6);
    assertEquals(35, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(40, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testFullWithNestedPartialConstraints() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(false, true, 2, 3, 18, 19, 38, 39));
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(0, 1);
    assertEquals(0, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(5, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(2.5, 2.6);
    assertEquals(2.5, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(2.6, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(4, 5);
    assertEquals(0, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(5, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(35, 36);
    assertEquals(35, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(40, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(38.5, 38.6);
    assertEquals(38.5, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(38.6, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(39.5, 39.6);
    assertEquals(35, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(40, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testPartialWithNestedFullConstraints() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));
    selection.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));

    selection.set(0, 1);
    assertEquals(0, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(1, mySelection.getMax(), Float.MIN_VALUE);

    // SelectionModel selects the first constraint that intersects the previous selected range. If we don't clear the selection, the partial
    // constrain would have been used instead.
    selection.clear();
    selection.set(2.5, 2.6);
    assertEquals(2, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(3, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(4, 5);
    assertEquals(4, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(5, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(35, 36);
    assertEquals(35, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(36, mySelection.getMax(), Float.MIN_VALUE);

    selection.clear();
    selection.set(38.5, 38.6);
    assertEquals(38, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(39, mySelection.getMax(), Float.MIN_VALUE);

    selection.set(39.5, 39.6);
    assertEquals(39.5, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(39.6, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testListenersFiredAsExpected() throws Exception {
    SelectionModel model = new SelectionModel(mySelection, myRange);

    final int SELECTION_CREATED = 0;
    final int SELECTION_CLEARED = 1;
    final boolean[] event = {false, false};
    model.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        event[SELECTION_CREATED] = true;
      }

      @Override
      public void selectionCleared() {
        event[SELECTION_CLEARED] = true;
      }
    });

    // Basic selection modification
    Arrays.fill(event, false);
    model.set(1, 2);
    assertTrue(event[SELECTION_CREATED]);
    event[SELECTION_CREATED] = false;
    model.set(1, 3);
    assertFalse(event[SELECTION_CREATED]);
    assertFalse(event[SELECTION_CLEARED]);
    model.clear();
    assertTrue(event[SELECTION_CLEARED]);

    // Selection creation not fired if not changed
    model.set(1, 2);
    event[SELECTION_CREATED] = false;
    model.set(1, 2);
    assertFalse(event[SELECTION_CREATED]);

    // Selection clear not fired if not changed
    model.clear();
    event[SELECTION_CLEARED] = false;
    model.clear();
    assertFalse(event[SELECTION_CLEARED]);

    // Selection creation only fired after updating is finished
    model.clear();
    Arrays.fill(event, false);
    model.beginUpdate();
    model.set(3, 4);
    model.set(3, 5);
    assertFalse(event[SELECTION_CREATED]);
    model.endUpdate();
    assertTrue(event[SELECTION_CREATED]);

    // Selection clear only fired after updating is finished
    model.set(1, 2);
    event[SELECTION_CLEARED] = false;
    model.beginUpdate();
    model.clear();
    assertFalse(event[SELECTION_CLEARED]);
    model.endUpdate();
    assertTrue(event[SELECTION_CLEARED]);
  }

  @Test
  public void testSelectionClearOnRangeChange() {
    SelectionModel model = new SelectionModel(mySelection, myRange);
    model.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));

    final int CREATED = 0;
    final int CLEARED = 1;
    int[] counts = new int[]{0, 0};
    model.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        counts[CREATED]++;
      }

      @Override
      public void selectionCleared() {
        counts[CLEARED]++;
      }
    });

    model.set(2.5, 2.5);
    assertEquals(0, counts[CLEARED]);
    assertEquals(1, counts[CREATED]);
    Arrays.setAll(counts, operand -> 0);

    model.set(4, 5);
    assertEquals(1, counts[CLEARED]);
    assertEquals(0, counts[CREATED]);
    Arrays.setAll(counts, operand -> 0);

    model.set(7, 9);
    assertEquals(0, counts[CLEARED]);
    assertEquals(0, counts[CREATED]);
    Arrays.setAll(counts, operand -> 0);

    model.set(18, 19);
    assertEquals(0, counts[CLEARED]);
    assertEquals(1, counts[CREATED]);
    Arrays.setAll(counts, operand -> 0);

    model.set(38.5, 38.7);
    assertEquals(0, counts[CLEARED]);
    assertEquals(1, counts[CREATED]);
    Arrays.setAll(counts, operand -> 0);

    model.set(38.3, 38.4);
    assertEquals(0, counts[CLEARED]);
    assertEquals(0, counts[CREATED]);
    Arrays.setAll(counts, operand -> 0);
  }

  @Test
  public void testCanSelectUnfinishedDurationData() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(false, true, 0, Long.MAX_VALUE));
    selection.set(10, 12);
    assertTrue(mySelection.isEmpty());
  }

  @Test
  public void testCannotSelectUnfinishedDurationData() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(true, true, 0, Long.MAX_VALUE));
    selection.set(10, 12);
    assertEquals(10, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(12, mySelection.getMax(), Float.MIN_VALUE);
  }

  private DurationDataModel<DefaultConfigurableDurationData> createConstraint(boolean selectableWhenUnspecifiedDuration, boolean selectPartialRange, long... values) {
    DefaultDataSeries<DefaultConfigurableDurationData> series = new DefaultDataSeries<>();
    RangedSeries<DefaultConfigurableDurationData> ranged = new RangedSeries<>(myRange, series);
    DurationDataModel<DefaultConfigurableDurationData> constraint = new DurationDataModel<>(ranged);
    for (int i = 0; i < values.length / 2; i++) {
      long duration = values[i * 2 + 1] == Long.MAX_VALUE ? Long.MAX_VALUE : values[i * 2 + 1] - values[i * 2];
      series.add(values[i * 2], new DefaultConfigurableDurationData(duration, selectableWhenUnspecifiedDuration, selectPartialRange));
    }

    return constraint;
  }
}