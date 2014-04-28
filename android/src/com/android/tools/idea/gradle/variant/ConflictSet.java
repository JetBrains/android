/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Set of all variant-selection-related conflicts. We classify these conflicts in 2 groups:
 * <ol>
 * <li>
 * <b>Selection conflicts.</b> These conflicts occur when module A depends on module B/variant X but module B has variant Y selected
 * instead. These conflicts can be easily fixed by selecting the right variant in the "Build Variants" tool window.
 * </li>
 * <b>Structure conflicts.</b> These conflicts occur when there are multiple modules depending on different variants of a single module.
 * For example, module A depends on module E/variant X, module B depends on module E/variant Y and module C depends on module E/variant Z.
 * These conflicts cannot be resolved through the "Build Variants" tool window because regardless of the variant is selected on module E,
 * we will always have a selection conflict. These conflicts can be resolved by importing a subset of modules into the IDE (i.e. project
 * profiles.)
 * </ol>
 */
public class ConflictSet {
  @NotNull private final ImmutableList<Conflict> mySelectionConflicts;
  @NotNull private final ImmutableList<Conflict> myStructureConflicts;

  ConflictSet(@NotNull Collection<Conflict> selectionConflicts, @NotNull Collection<Conflict> structureConflicts) {
    mySelectionConflicts = ImmutableList.copyOf(selectionConflicts);
    myStructureConflicts = ImmutableList.copyOf(structureConflicts);
  }

  @NotNull
  public List<Conflict> getSelectionConflicts() {
    return mySelectionConflicts;
  }

  @NotNull
  public List<Conflict> getStructureConflicts() {
    return myStructureConflicts;
  }
}
