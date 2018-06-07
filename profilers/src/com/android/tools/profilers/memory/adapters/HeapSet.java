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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Classifies {@link InstanceObject}s based on their allocation's heap ID.
 */
public class HeapSet extends ClassifierSet {
  @NotNull private final CaptureObject myCaptureObject;
  @NotNull private ClassGrouping myClassGrouping = ClassGrouping.ARRANGE_BY_CLASS;
  private final int myId;
  @Nullable private Pattern myFilter;

  public HeapSet(@NotNull CaptureObject captureObject, @NotNull String heapName, int id) {
    super(heapName);
    myCaptureObject = captureObject;
    myId = id;
    setClassGrouping(ClassGrouping.ARRANGE_BY_CLASS);
  }

  public void setClassGrouping(@NotNull ClassGrouping classGrouping) {
    if (myClassGrouping == classGrouping) {
      return;
    }
    myClassGrouping = classGrouping;

    // Gather all the instances from the descendants and add them to the heap node.
    // Subsequent calls to getChildrenClassifierSets will re-partition them to the correct child ClassifierSet.
    List<InstanceObject> snapshotStream = getSnapshotInstanceStream().collect(Collectors.toList());
    List<InstanceObject> deltaStream = getDeltaInstanceStream().collect(Collectors.toList());
    myDeltaInstances.clear();
    mySnapshotInstances.clear();
    myClassifier = null;
    myDeltaInstances.addAll(deltaStream);
    mySnapshotInstances.addAll(snapshotStream);
    myNeedsRefiltering = true;
  }

  public int getId() {
    return myId;
  }

  // Select and apply the filter if it is different from previous one.
  public void selectFilter(@Nullable Pattern filter) {
    // We do not apply filter when both old and new filters are null
    if (myFilter == null && filter == null) {
      return;
    }

    boolean filterChanged =
      filter == null || myFilter == null || filter.flags() != myFilter.flags() || !filter.pattern().equals(myFilter.pattern());
    myFilter = filter;
    applyFilter(filterChanged);
  }

  @Nullable
  public Pattern getFilter() {
    return myFilter;
  }

  // Filter child ClassSets based on current selected filter string
  // If filterChanged is false, we only update modified classifierSets
  private void applyFilter(boolean filterChanged) {
    applyFilter(myFilter, false, filterChanged);
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
