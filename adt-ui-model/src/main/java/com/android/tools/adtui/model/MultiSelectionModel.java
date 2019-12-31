/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * A multi-selection model that supports creating, adding and removing selection of multiple items.
 * <p>
 * All selected items should be of the same subtype of T.
 */
public class MultiSelectionModel<T> extends AspectModel<MultiSelectionModel.Aspect> {
  public enum Aspect {
    CHANGE_SELECTION,
  }

  private final Set<T> mySelection = new HashSet<>();

  /**
   * @return an immutable collection of the selected objects to prevent caller from modifying the selection.
   */
  public ImmutableList<T> getSelection() {
    return ImmutableList.<T>builder().addAll(mySelection).build();
  }

  /**
   * Replaces the current selection with a new one. Only fires {@link Aspect#CHANGE_SELECTION} if the new selection is not identical to the
   * current one.
   *
   * @param newSelection new selection to replace with.
   */
  public void setSelection(@NotNull Set<T> newSelection) {
    if (!mySelection.containsAll(newSelection) || !newSelection.containsAll(mySelection)) {
      mySelection.clear();
      mySelection.addAll(newSelection);
      changed(Aspect.CHANGE_SELECTION);
    }
  }

  /**
   * Deselect everything selected.
   */
  public void clearSelection() {
    setSelection(Collections.emptySet());
  }

  /**
   * Test if an item is already selected.
   *
   * @param selection the item to test.
   * @return true if the item is present in the current selection. False otherwise.
   */
  public boolean isSelected(T selection) {
    return mySelection.contains(selection);
  }

  /**
   * @return true if no item is selected.
   */
  public boolean isEmpty() {
    return mySelection.isEmpty();
  }
}
