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
import com.android.tools.profilers.memory.adapters.classifiers.Classifier;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Native method {@link ClassifierSet} that represents a leaf node in a heapprofd trace.
 */
public class NativeAllocationMethodSet extends ClassifierSet {
  @NotNull
  public static Classifier createDefaultClassifier() {
    return new NativeAllocationMethodClassifier();
  }

  public NativeAllocationMethodSet(@NotNull String allocationFunction) {
    super(allocationFunction);
  }

  @NotNull
  @Override
  public Classifier createSubClassifier() {
    // Do nothing, as this is a leaf node.
    return Classifier.IDENTITY_CLASSIFIER;
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
