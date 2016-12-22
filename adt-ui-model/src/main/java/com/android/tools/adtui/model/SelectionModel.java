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

import org.jetbrains.annotations.NotNull;

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

  public SelectionModel(@NotNull Range selectionRange, @NotNull Range globalRange) {
    myRange = globalRange;
    mySelectionRange = selectionRange;

    myRange.addDependency(this).onChange(Range.Aspect.RANGE, this::rangesChanged);
    mySelectionRange.addDependency(this).onChange(Range.Aspect.RANGE, this::rangesChanged);
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
    mySelectionRange.set(min, max);
  }

  @NotNull
  public Range getSelectionRange() {
    return mySelectionRange;
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }
}
