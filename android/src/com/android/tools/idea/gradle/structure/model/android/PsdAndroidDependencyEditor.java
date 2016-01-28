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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.tools.idea.gradle.structure.model.PsdChildEditor;
import com.android.tools.idea.gradle.structure.model.PsdModelEditor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public abstract class PsdAndroidDependencyEditor extends PsdChildEditor {
  @NotNull private final Set<String> myVariants = Sets.newHashSet();

  PsdAndroidDependencyEditor(PsdModelEditor parent) {
    super(parent);
  }

  @Override
  @NotNull
  public PsdAndroidModuleEditor getParent() {
    return (PsdAndroidModuleEditor)super.getParent();
  }

  void addContainer(@NotNull PsdVariantEditor container) {
    myVariants.add(container.getName());
  }

  @NotNull
  public List<String> getVariants() {
    return ImmutableList.copyOf(myVariants);
  }
}
