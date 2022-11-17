/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.android;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This provider is a workaround for the ResourceBundleGrouper and the Kotlin plugin overriding
 * nodes in the Android project view.
 */
public class BuildScriptTreeStructureProvider implements TreeStructureProvider {
  @NotNull private final TreeStructureProvider myRealTreeStructureProvider;

  public BuildScriptTreeStructureProvider(@NotNull TreeStructureProvider provider) {
    myRealTreeStructureProvider = provider;
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode<?>> modify(
    @NotNull AbstractTreeNode<?> parent, @NotNull Collection<AbstractTreeNode<?>> children, @Nullable ViewSettings settings) {
    if (parent instanceof AndroidBuildScriptsGroupNode) {
      return children;
    }

    return myRealTreeStructureProvider.modify(parent, children, settings);
  }

  @Nullable
  @Override
  public Object getData(@NotNull Collection<? extends AbstractTreeNode<?>> selected, @NotNull String dataName) {
    return myRealTreeStructureProvider.getData(selected, dataName);
  }

  @Override
  public String toString() {
    return String.format("BuildScriptTreeStructureProvider(%s)", myRealTreeStructureProvider);
  }
}
