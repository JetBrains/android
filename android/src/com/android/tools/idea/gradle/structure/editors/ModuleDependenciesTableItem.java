/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.editors;

import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a cell in the dependency table that shows the dependency data itself: for example, the module name for module dependencies,
 * or the Maven coordinates for Maven dependencies.
 */
public class ModuleDependenciesTableItem {
  @Nullable protected final BuildFileStatement myEntry;

  protected ModuleDependenciesTableItem(@Nullable BuildFileStatement entry) {
    myEntry = entry;
  }

  @Nullable
  public final Dependency.Scope getScope() {
    return myEntry != null && myEntry instanceof Dependency ? ((Dependency)myEntry).scope : null;
  }

  public final void setScope(@NotNull Dependency.Scope scope) {
    if (myEntry != null && myEntry instanceof Dependency) ((Dependency)myEntry).scope = scope;
  }

  @Nullable
  public final BuildFileStatement getEntry() {
    return myEntry;
  }

  public boolean isRemovable() {
    return true;
  }

  public boolean isEditable() {
    return false;
  }

  @Nullable
  public String getTooltipText() {
    return null;
  }
}
