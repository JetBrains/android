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
package com.android.tools.adtui.model;

import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import java.util.ArrayList;
import java.util.List;

public class SelectionModel extends AspectModel<SelectionModel.Aspect> {

  public enum Aspect {
    SELECTION,
  }

  /**
   * The range being selected.
   */
  @NotNull
  private final Range mySelectionRange;

  /**
   * The reference range.
   */
  @NotNull
  private final Range myRange;

  @NotNull
  private final List<SelectionListener> myListeners = new ArrayList<>();

  @Nullable
  private final DurationDataModel<? extends DurationData> myConstraints;

  public SelectionModel(@NotNull Range selection, @NotNull Range range) {
    this(selection, range, null);
  }

  public SelectionModel(@NotNull Range selection, @NotNull Range range, @Nullable DurationDataModel<? extends DurationData> constraints) {
    myRange = range;
    mySelectionRange = selection;

    myRange.addDependency(this).onChange(Range.Aspect.RANGE, this::rangesChanged);
    mySelectionRange.addDependency(this).onChange(Range.Aspect.RANGE, this::rangesChanged);
    myConstraints = constraints;
  }

  private void rangesChanged() {
    changed(Aspect.SELECTION);
  }

  public void addChangeListener(final SelectionListener listener) {
    myListeners.add(listener);
  }

  public void fireSelectionEvent() {
    ChangeEvent e = new ChangeEvent(this);
    myListeners.forEach(l -> l.selectionStateChanged(e));
  }

  public void set(double min, double max) {

    if (myConstraints == null) {
      mySelectionRange.set(min, max);
      return;
    }

    DataSeries<? extends DurationData> series = myConstraints.getSeries().getDataSeries();
    ImmutableList<? extends SeriesData<? extends DurationData>> constraints = series.getDataForXRange(new Range(min, max));

    Range candidate = new Range(min, max);
    Range constraint = null;
    for (SeriesData<? extends DurationData> data : constraints) {
      Range r = new Range(data.x, data.x + data.value.getDuration());
      // Check if this constraint intersects the candidate range.
      if (!r.getIntersection(candidate).isEmpty()) {
        constraint = r;
        // If this constraint already intersects the current range, use it.
        if (!r.getIntersection(mySelectionRange).isEmpty()) {
          break;
        }
      }
    }
    if (constraint == null) {
      mySelectionRange.clear();
    }
    else {
      mySelectionRange.set(constraint.getIntersection(candidate));
    }
  }

  @NotNull
  public Range getSelectionRange() {
    return mySelectionRange;
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }

  @Nullable
  public DurationDataModel<? extends DurationData> getConstraints() {
    return myConstraints;
  }
}
