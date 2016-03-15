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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractBaseTreeStructure;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import org.jetbrains.annotations.NotNull;

public class ResolvedDependenciesTreeStructure extends AbstractBaseTreeStructure {
  @NotNull private final RootNode myRootNode;

  ResolvedDependenciesTreeStructure(@NotNull PsAndroidModule module) {
    myRootNode = new RootNode(module);
  }

  @Override
  public Object getRootElement() {
    return myRootNode;
  }

  boolean settingsChanged() {
    return myRootNode.settingsChanged();
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }
}
