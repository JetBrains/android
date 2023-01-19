/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.classifiers;

import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.profilers.memory.ClassGrouping;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import org.jetbrains.annotations.NotNull;

/**
 * Classifies {@link InstanceObject}s based on their allocation's heap ID.
 */
public class HeapSet extends ClassifierSet {
  @NotNull private final CaptureObject myCaptureObject;
  @NotNull protected ClassGrouping myClassGrouping = ClassGrouping.ARRANGE_BY_CLASS;
  private final int myId;
  @NotNull private Filter myFilter;

  public HeapSet(@NotNull CaptureObject captureObject, @NotNull String heapName, int id) {
    super(heapName);
    myCaptureObject = captureObject;
    myId = id;
    myFilter = Filter.EMPTY_FILTER;
    setClassGrouping(ClassGrouping.ARRANGE_BY_CLASS);
  }

  public ClassGrouping getClassGrouping() {
    return myClassGrouping;
  }

  public void setClassGrouping(@NotNull ClassGrouping classGrouping) {
    if (myClassGrouping == classGrouping) {
      return;
    }
    myClassGrouping = classGrouping;
    coalesce();
    needsRefiltering = true;
  }

  public int getId() {
    return myId;
  }

  // Select and apply a filter.
  // When there are content changes in HeapSet, we need to re-select the same filter.
  public void selectFilter(@NotNull Filter filter) {
    // If both the old and new filters are empty, no alloc/dealloc events will be filtered out and we do not need to do anything
    // even when HeapSet has content changes.
    if (myFilter.isEmpty() && filter.isEmpty()) {
      return;
    }

    boolean filterChanged = !myFilter.equals(filter);
    myFilter = filter;
    applyFilter(filterChanged);
  }

  @NotNull
  public Filter getFilter() {
    return myFilter;
  }

  // Filter child ClassSets based on current selected filter string
  // If filterChanged is false, we only update modified classifierSets
  private void applyFilter(boolean filterChanged) {
    applyFilter(myFilter, filterChanged);
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    switch (myClassGrouping) {
      case ARRANGE_BY_CLASS:
        return ClassSet.createDefaultClassifier();
      case ARRANGE_BY_PACKAGE:
        return PackageSet.createDefaultClassifier(myCaptureObject);
      case ARRANGE_BY_CALLSTACK:
        return ThreadSet.createDefaultClassifier(myCaptureObject);
      default:
        throw new RuntimeException("Classifier type not implemented: " + myClassGrouping);
    }
  }
}
