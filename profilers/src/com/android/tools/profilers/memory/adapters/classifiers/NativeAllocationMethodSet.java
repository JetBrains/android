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
import org.jetbrains.annotations.NotNull;

/**
 * Native method {@link ClassifierSet} that represents a leaf node in a heapprofd trace.
 */
public class NativeAllocationMethodSet extends ClassifierSet {
  @NotNull
  public static Classifier createDefaultClassifier() {
    return NativeAllocationMethodClassifier.newInstance();
  }

  public NativeAllocationMethodSet(@NotNull String allocationFunction) {
    super(allocationFunction);
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    // Do nothing, as this is a leaf node.
    return Classifier.Id.INSTANCE;
  }

  @Override
  protected void applyFilter(@NotNull Filter filter, boolean hasMatchedAncestor, boolean filterChanged) {
    if (!filterChanged && !myNeedsRefiltering) {
      return;
    }
    myIsMatched = matches(filter);
    myFilterMatchCount = myIsMatched ? 1 : 0;
    myIsFiltered = !myIsMatched && !hasMatchedAncestor;
    myNeedsRefiltering = false;
  }

  @Override
  protected boolean matches(@NotNull Filter filter) {
    return filter.matches(getName());
  }


}
