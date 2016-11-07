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
package com.android.tools.idea.gradle.project.sync.common;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.Collections.sort;

public class VariantSelector {
  @Nullable
  public Variant findVariantToSelect(@NotNull AndroidProject androidProject) {
    Collection<Variant> variants = androidProject.getVariants();
    if (variants.size() == 1) {
      Variant variant = getFirstItem(variants);
      assert variant != null;
      return variant;
    }
    // look for "debug" variant. This is just a little convenience for the user that has not created any additional flavors/build types.
    // trying to match something else may add more complexity for little gain.
    for (Variant variant : variants) {
      if ("debug".equals(variant.getName())) {
        return variant;
      }
    }
    List<Variant> sortedVariants = Lists.newArrayList(variants);
    sort(sortedVariants, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    return sortedVariants.isEmpty() ? null : sortedVariants.get(0);
  }
}
