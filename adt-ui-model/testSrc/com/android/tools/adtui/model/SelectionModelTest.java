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
    selection.addConstraint(createConstraint(0, 5, 15, 20, 35, 40));

    selection.set(10, 18);

    assertEquals(15, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(18, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testSetWithPartialConstraintEmpty() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(0, 5, 15, 20, 35, 40));

    selection.set(10, 12);

    assertTrue(mySelection.isEmpty());
  }

  @Test
  public void testSelectionPrefersCurrentOne() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(0, 5, 15, 20, 35, 40));
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
    selection.addConstraint(createConstraint(0, 5, 15, 20, 35, 40));
    selection.setSelectFullConstraint(true);

    selection.set(10, 18);

    assertEquals(15, mySelection.getMin(), Float.MIN_VALUE);
    assertEquals(20, mySelection.getMax(), Float.MIN_VALUE);
  }

  @Test
  public void testNestedConstraints() throws Exception {
    SelectionModel selection = new SelectionModel(mySelection, myRange);
    selection.addConstraint(createConstraint(2, 3, 18, 19, 38, 39));
    selection.addConstraint(createConstraint(0, 5, 15, 20, 35, 40));
    selection.setSelectFullConstraint(true);

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
  public void testSelectionFiredAsExpected() throws Exception {
    SelectionModel model = new SelectionModel(mySelection, myRange);

    final boolean[] selectionChanged = {false};
    model.addChangeListener(e -> selectionChanged[0] = true);

    // Basic selection modification
    selectionChanged[0] = false;
    model.set(1, 2);
    assertTrue(selectionChanged[0]);

    // Selection change not fired if not changed
    selectionChanged[0] = false;
    model.set(1, 2);
    assertFalse(selectionChanged[0]);

    // Selection change only fired after updating is finished
    selectionChanged[0] = false;
    model.beginUpdate();
    model.set(3, 4);
    assertFalse(selectionChanged[0]);
    model.set(3, 5);
    assertFalse(selectionChanged[0]);
    model.endUpdate();
    assertTrue(selectionChanged[0]);

    // Selection change not fired if not changed during update
    selectionChanged[0] = false;
    model.beginUpdate();
    model.endUpdate();
    assertFalse(selectionChanged[0]);

    // Fired on clear
    selectionChanged[0] = false;
    model.getSelectionRange().clear();
    assertTrue(selectionChanged[0]);
  }

  private DurationDataModel<DefaultDurationData> createConstraint(long... values) {
    DefaultDataSeries<DefaultDurationData> series = new DefaultDataSeries<>();
    RangedSeries<DefaultDurationData> ranged = new RangedSeries<>(myRange, series);
    DurationDataModel<DefaultDurationData> constraint = new DurationDataModel<>(ranged);
    for (int i = 0; i < values.length / 2; i++) {
      series.add(values[i * 2], new DefaultDurationData(values[i * 2 + 1] - values[i * 2]));
    }

    return constraint;
  }
}