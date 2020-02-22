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

import com.android.tools.profilers.memory.adapters.ClassDb;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.intellij.util.containers.ContainerUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NativeAllocationMethodClassifier extends Classifier {
  @NotNull private final Map<String, NativeAllocationMethodSet> myAllocationMap = new LinkedHashMap<>();

  @Nullable
  @Override
  public ClassifierSet getClassifierSet(@NotNull InstanceObject instance, boolean createIfAbsent) {
    ClassDb.ClassEntry classEntry = instance.getClassEntry();
    // The classname is the leaf function name that issued the allocation.
    NativeAllocationMethodSet set = myAllocationMap.get(classEntry.getClassName());
    if (set == null && createIfAbsent) {
      set = new NativeAllocationMethodSet(classEntry.getClassName());
      myAllocationMap.put(classEntry.getClassName(), set);
    }
    return set;
  }

  @NotNull
  @Override
  public List<ClassifierSet> getFilteredClassifierSets() {
    return ContainerUtil.filter(myAllocationMap.values(), child -> !child.getIsFiltered());
  }

  @NotNull
  @Override
  protected List<ClassifierSet> getAllClassifierSets() {
    return new ArrayList<>(myAllocationMap.values());
  }
}