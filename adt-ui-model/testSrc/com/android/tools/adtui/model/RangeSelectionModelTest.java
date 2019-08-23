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

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class RangeSelectionModelTest {
  private Range mySelection;

  @Before
  public void setUp() throws Exception {
    mySelection = new Range();
  }

  @Test
  public void testSetWithNoConstraints() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.set(10, 20);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(10);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(20);
  }

  @Test
  public void testSetWithPartialConstraint() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));

    selection.set(10, 18);

    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(15);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(18);
  }

  @Test
  public void testSetWithPartialConstraintEmpty() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));

    selection.set(10, 12);

    assertThat(mySelection.isEmpty()).isTrue();
  }

  @Test
  public void testSelectionPrefersCurrentOne() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));
    selection.set(18, 20);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(18);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(20);

    // This overlaps with two constraints, it should choose the one it's using
    selection.set(3, 18);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(15);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(18);

    // In both directions
    selection.set(16, 39);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(16);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(20);
  }

  @Test
  public void testFullSelection() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(10, 18);

    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(15);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(20);
  }

  @Test
  public void canSelectWithConstraints() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20));
    assertThat(selection.canSelectRange(new Range(15, 15))).isTrue();
    assertThat(selection.canSelectRange(new Range(14, 14))).isFalse();
  }

  @Test
  public void canSelectWithOutConstraints() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    assertThat(selection.canSelectRange(new Range(15, 15))).isTrue();
    assertThat(selection.canSelectRange(new Range(14, 14))).isTrue();
  }

  @Test
  public void testNestedFullConstraints() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(0, 1);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(2.5, 2.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(2);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(3);

    selection.set(4, 5);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(35, 36);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(40);

    selection.set(38.5, 38.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(38);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(39);

    selection.set(39.5, 39.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(40);
  }

  @Test
  public void testFullWithNestedPartialConstraints() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 2, 3, 18, 19, 38, 39));
    selection.addConstraint(createConstraint(false, false, 0, 5, 15, 20, 35, 40));

    selection.set(0, 1);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(2.5, 2.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(2.5);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(2.6);

    selection.set(4, 5);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(35, 36);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(40);

    selection.set(38.5, 38.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(38.5);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(38.6);

    selection.set(39.5, 39.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(40);
  }

  @Test
  public void testPartialWithNestedFullConstraints() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, 5, 15, 20, 35, 40));
    selection.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));

    selection.set(0, 1);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(0);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(1);

    // RangeSelectionModel selects the first constraint that intersects the previous selected range. If we don't clear the selection, the
    // partial constrain would have been used instead.
    selection.clear();
    selection.set(2.5, 2.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(2);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(3);

    selection.set(4, 5);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(4);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(5);

    selection.set(35, 36);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(35);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(36);

    selection.clear();
    selection.set(38.5, 38.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(38);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(39);

    selection.set(39.5, 39.6);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(39.5);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(39.6);
  }

  @Test
  public void testAspectFiresWhenSelectionChanges() {
    RangeSelectionModel model = new RangeSelectionModel(mySelection);
    AspectObserver observer = new AspectObserver();

    int[] aspectFiredCount = new int[]{0};

    model.addDependency(observer).onChange(RangeSelectionModel.Aspect.SELECTION, () -> aspectFiredCount[0]++);

    assertThat(aspectFiredCount[0]).isEqualTo(0);

    model.set(1, 2);
    assertThat(aspectFiredCount[0]).isEqualTo(1);

    model.set(1, 2);
    assertThat(aspectFiredCount[0]).isEqualTo(1);

    model.set(3, 4);
    assertThat(aspectFiredCount[0]).isEqualTo(2);

    model.clear();
    assertThat(aspectFiredCount[0]).isEqualTo(3);

    // Aspect still fired even between begin/endUpdate calls
    model.beginUpdate();

    model.set(1, 2);
    assertThat(aspectFiredCount[0]).isEqualTo(4);

    model.set(3, 4);
    assertThat(aspectFiredCount[0]).isEqualTo(5);

    model.endUpdate();
    assertThat(aspectFiredCount[0]).isEqualTo(5);
  }

  @Test
  public void testListenersFiredAsExpected() {
    RangeSelectionModel model = new RangeSelectionModel(mySelection);

    final int SELECTION_CREATED = 0;
    final int SELECTION_CLEARED = 1;
    final int SELECTION_FAILED = 2;
    final int[] event = {0, 0, 0};
    model.addListener(new RangeSelectionListener() {
      @Override
      public void selectionCreated() {
        event[SELECTION_CREATED]++;
      }

      @Override
      public void selectionCleared() {
        event[SELECTION_CLEARED]++;
      }

      @Override
      public void selectionCreationFailure() {
        event[SELECTION_FAILED]++;
      }
    });

    // Basic selection modification
    Arrays.fill(event, 0);
    model.set(1, 2);
    assertThat(event[SELECTION_CREATED]).isEqualTo(1);
    event[SELECTION_CREATED] = 0;
    model.set(1, 3);
    assertThat(event[SELECTION_CREATED]).isEqualTo(0);
    assertThat(event[SELECTION_CLEARED]).isEqualTo(0);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);
    model.clear();
    assertThat(event[SELECTION_CLEARED]).isEqualTo(1);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);

    // Selection creation not fired if not changed
    model.set(1, 2);
    event[SELECTION_CREATED] = 0;
    model.set(1, 2);
    assertThat(event[SELECTION_CREATED]).isEqualTo(0);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);

    // Selection clear not fired if not changed
    model.clear();
    event[SELECTION_CLEARED] = 0;
    model.clear();
    assertThat(event[SELECTION_CLEARED]).isEqualTo(0);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);

    // Selection creation only fired after updating is finished
    model.clear();
    Arrays.fill(event, 0);
    model.beginUpdate();
    model.set(3, 4);
    model.set(3, 5);
    assertThat(event[SELECTION_CREATED]).isEqualTo(0);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);
    model.endUpdate();
    assertThat(event[SELECTION_CREATED]).isEqualTo(1);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);

    // Selection clear only fired after updating is finished
    model.set(1, 2);
    event[SELECTION_CLEARED] = 0;
    model.beginUpdate();
    model.clear();
    assertThat(event[SELECTION_CLEARED]).isEqualTo(0);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);
    model.endUpdate();
    assertThat(event[SELECTION_CLEARED]).isEqualTo(1);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);

    // Selection failed is fired when attempting to select constrained ranges
    model.clear();
    Arrays.fill(event, 0);
    model.addConstraint(createConstraint(false, true, 0, 1));
    model.addConstraint(createConstraint(false, false, 2, 3));
    model.addConstraint(createConstraint(true, true, 5, Long.MAX_VALUE));
    model.set(0.25, 0.75);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);
    model.set(1.25, 1.75);
    assertThat(event[SELECTION_FAILED]).isEqualTo(1);
    event[SELECTION_FAILED] = 0;
    model.set(2.25, 2.75);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);
    model.set(7.5, 10);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);

    // Only most recent event fired after updating is finished
    Arrays.fill(event, 0);
    model.beginUpdate();
    model.clear(); // Normally fires selectionCleared but is swallowed within begin/endUpdate
    model.set(0.25, 0.75); // Normally fires selectionCreated but is swallowed within begin/endUpdate
    assertThat(event[SELECTION_CLEARED]).isEqualTo(0);
    assertThat(event[SELECTION_CREATED]).isEqualTo(0);
    model.endUpdate(); // Only fire most recent event (selectionCreated)
    assertThat(event[SELECTION_CLEARED]).isEqualTo(0);
    assertThat(event[SELECTION_CREATED]).isEqualTo(1);

    Arrays.fill(event, 0);
    model.beginUpdate();
    model.clear(); // Normally fires selectionCleared but is swallowed within begin/endUpdate
    model.set(0.25, 0.75); // Normally fire selectionCreated but is swallowed within begin/endUpdate
    model.set(1.25, 1.75); // Normally fires selectionCleared + selectionCreationFailed but is swallowed within begin/endUpdate
    assertThat(event[SELECTION_CLEARED]).isEqualTo(0);
    assertThat(event[SELECTION_CREATED]).isEqualTo(0);
    assertThat(event[SELECTION_FAILED]).isEqualTo(0);
    model.endUpdate(); // Only fire most recent event (selectionCreationFailed)
    assertThat(event[SELECTION_CLEARED]).isEqualTo(0);
    assertThat(event[SELECTION_CREATED]).isEqualTo(0);
    assertThat(event[SELECTION_FAILED]).isEqualTo(1);
  }

  @Test
  public void testSelectionClearOnRangeChange() {
    RangeSelectionModel model = new RangeSelectionModel(mySelection);
    model.addConstraint(createConstraint(false, false, 2, 3, 18, 19, 38, 39));

    final int CREATED = 0;
    final int CLEARED = 1;
    int[] counts = new int[]{0, 0};
    model.addListener(new RangeSelectionListener() {
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
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(1);
    Arrays.fill(counts, 0);

    model.set(4, 5);
    assertThat(counts[CLEARED]).isEqualTo(1);
    assertThat(counts[CREATED]).isEqualTo(0);
    Arrays.fill(counts, 0);

    model.set(7, 9);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(0);
    Arrays.fill(counts, 0);

    model.set(18, 19);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(1);
    Arrays.fill(counts, 0);

    model.set(38.5, 38.7);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(1);
    Arrays.fill(counts, 0);

    model.set(38.3, 38.4);
    assertThat(counts[CLEARED]).isEqualTo(0);
    assertThat(counts[CREATED]).isEqualTo(0);
    Arrays.fill(counts, 0);
  }

  @Test
  public void testListenersFireEvenWhenModifyingUnderlyingRangeDirectly() {
    RangeSelectionModel model = new RangeSelectionModel(mySelection);

    final int SELECTION_CREATED = 0;
    final int SELECTION_CLEARED = 1;
    final boolean[] event = {false, false};
    model.addListener(new RangeSelectionListener() {
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
    mySelection.set(1, 2);
    assertThat(event[SELECTION_CREATED]).isTrue();
    event[SELECTION_CREATED] = false;
    mySelection.set(1, 3);
    assertThat(event[SELECTION_CREATED]).isFalse();
    mySelection.clear();
    assertThat(event[SELECTION_CLEARED]).isTrue();

    // Only fire one even after begin/endUpdate
    Arrays.fill(event, false);
    model.beginUpdate();
    mySelection.set(1, 2);
    mySelection.clear();
    model.endUpdate();
    assertThat(event[SELECTION_CREATED]).isFalse();
    assertThat(event[SELECTION_CLEARED]).isTrue();
  }

  @Test
  public void testCanSelectUnfinishedDurationData() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(false, true, 0, Long.MAX_VALUE));
    selection.set(10, 12);
    assertThat(mySelection.isEmpty()).isTrue();
  }

  @Test
  public void testCannotSelectUnfinishedDurationData() {
    RangeSelectionModel selection = new RangeSelectionModel(mySelection);
    selection.addConstraint(createConstraint(true, true, 0, Long.MAX_VALUE));
    selection.set(10, 12);
    assertThat(mySelection.getMin()).isWithin(Float.MIN_VALUE).of(10);
    assertThat(mySelection.getMax()).isWithin(Float.MIN_VALUE).of(12);
  }

  private static DurationDataModel<DefaultConfigurableDurationData> createConstraint(boolean selectableWhenUnspecifiedDuration,
                                                                                     boolean selectPartialRange,
                                                                                     long... values) {
    DefaultDataSeries<DefaultConfigurableDurationData> series = new DefaultDataSeries<>();
    RangedSeries<DefaultConfigurableDurationData> ranged = new RangedSeries<>(new Range(0, 100), series);
    DurationDataModel<DefaultConfigurableDurationData> constraint = new DurationDataModel<>(ranged);
    for (int i = 0; i < values.length / 2; i++) {
      long duration = values[i * 2 + 1] == Long.MAX_VALUE ? Long.MAX_VALUE : values[i * 2 + 1] - values[i * 2];
      series.add(values[i * 2], new DefaultConfigurableDurationData(duration, selectableWhenUnspecifiedDuration, selectPartialRange));
    }

    return constraint;
  }
}