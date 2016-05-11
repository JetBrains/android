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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.resolved;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractMainDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.DependencyGraphPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.TargetArtifactsPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

class MainPanel extends AbstractMainDependenciesPanel {
  @NotNull private final DependencyGraphPanel myDependencyGraphPanel;
  @NotNull private final TargetArtifactsPanel myTargetArtifactsPanel;

  MainPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super(context);

    myDependencyGraphPanel = new DependencyGraphPanel(module, context);
    myDependencyGraphPanel.setHistory(getHistory());

    myTargetArtifactsPanel = new TargetArtifactsPanel(module, context);

    myDependencyGraphPanel.add(newSelection -> {
      AbstractDependencyNode<? extends PsAndroidDependency> node = newSelection;
      if (node != null) {
        node = findTopDependencyNode(node);
      }
      myTargetArtifactsPanel.displayTargetArtifacts(node != null ? node.getFirstModel() : null);
    });

    JBSplitter verticalSplitter = createMainVerticalSplitter();
    verticalSplitter.setFirstComponent(myDependencyGraphPanel);
    verticalSplitter.setSecondComponent(myTargetArtifactsPanel);

    add(verticalSplitter, BorderLayout.CENTER);
  }

  @NotNull
  private static AbstractDependencyNode<? extends PsAndroidDependency> findTopDependencyNode(@NotNull AbstractDependencyNode<? extends PsAndroidDependency> node) {
    AbstractDependencyNode<? extends PsAndroidDependency> current = node;
    while (true) {
      SimpleNode parent = current.getParent();
      if (!(parent instanceof AbstractDependencyNode)) {
        return current;
      }
      current = (AbstractDependencyNode<? extends PsAndroidDependency>)parent;
    }
  }

  @Override
  public void setHistory(History history) {
    super.setHistory(history);
    myDependencyGraphPanel.setHistory(history);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return myDependencyGraphPanel.navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    myDependencyGraphPanel.queryPlace(place);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDependencyGraphPanel);
    Disposer.dispose(myTargetArtifactsPanel);
  }
}
