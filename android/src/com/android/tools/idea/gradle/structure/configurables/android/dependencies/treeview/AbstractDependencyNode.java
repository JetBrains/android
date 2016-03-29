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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractDependencyNode<T extends PsAndroidDependency> extends AbstractPsModelNode<T> {
  @Nullable
  public static AbstractDependencyNode<?> createNode(@NotNull AbstractPsModelNode<?> parent, @NotNull PsAndroidDependency dependency) {
    if (dependency instanceof PsLibraryDependency) {
      PsLibraryDependency libraryDependency = (PsLibraryDependency)dependency;
      return new LibraryDependencyNode(parent, libraryDependency);
    }
    else if (dependency instanceof PsModuleDependency) {
      PsModuleDependency moduleDependency = (PsModuleDependency)dependency;
      return new ModuleDependencyNode(parent, moduleDependency);
    }
    return null;
  }

  protected AbstractDependencyNode(@NotNull AbstractPsModelNode<?> parent, @NotNull T dependency) {
    super(parent, dependency);
  }

  protected AbstractDependencyNode(@NotNull AbstractPsModelNode<?> parent, @NotNull List<T> dependencies) {
    super(parent, dependencies);
  }

  public boolean isDeclared() {
    List<? extends PsAndroidDependency> models = getModels();
    for (PsAndroidDependency dependency : models) {
      if (dependency.isDeclared()) {
        return true;
      }
    }
    return false;
  }
}
