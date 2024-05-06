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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.graph;

import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeStructure;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency;
import com.intellij.ui.treeStructure.SimpleNode;
import java.util.Arrays;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class DependenciesTreeStructure extends AbstractBaseTreeStructure {
  @NotNull private final DependenciesTreeRootNode myRootNode;

  public DependenciesTreeStructure(@NotNull DependenciesTreeRootNode rootNode) {
    myRootNode = rootNode;
  }

  @Override
  @NotNull
  public Object getRootElement() {
    return myRootNode;
  }

  public void reset() {
    myRootNode.reset();
  }

  @NotNull
  public Optional<SimpleNode> findDeclaredDependencyNode(@NotNull PsDeclaredDependency dependency) {
    return Arrays.stream(myRootNode.getChildren())
      .filter(simpleNode -> {
        if (simpleNode instanceof AbstractDependencyNode) {
          AbstractDependencyNode<?, PsBaseDependency> node = (AbstractDependencyNode)simpleNode;
          for (PsBaseDependency model : node.getModels()) {
            // Checking for reference equality since declared dependencies are always reloaded.
            if (dependency == model) {
              return true;
            }
          }
        }
        return false;
      })
      .findFirst();
  }
}
