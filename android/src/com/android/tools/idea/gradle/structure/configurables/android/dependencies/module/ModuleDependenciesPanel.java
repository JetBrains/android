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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.*;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.createMainVerticalSplitter;

class ModuleDependenciesPanel extends AbstractMainPanel {
  @NotNull private final JBSplitter myVerticalSplitter;
  @NotNull private final DeclaredDependenciesPanel myDeclaredDependenciesPanel;
  @NotNull private final ResolvedDependenciesPanel myResolvedDependenciesPanel;
  @NotNull private final JPanel myAltPanel;

  ModuleDependenciesPanel(@NotNull PsAndroidModule module, @NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(module.getParent(), context, extraTopModules);

    myDeclaredDependenciesPanel = new DeclaredDependenciesPanel(module, context);
    myResolvedDependenciesPanel = new ResolvedDependenciesPanel(module, context, myDeclaredDependenciesPanel);

    myVerticalSplitter = createMainVerticalSplitter();
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    myVerticalSplitter.setSecondComponent(myResolvedDependenciesPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);

    myDeclaredDependenciesPanel.updateTableColumnSizes();
    myDeclaredDependenciesPanel.add(new DeclaredDependenciesPanel.SelectionListener() {
      @Override
      public void dependencyModelSelected(@NotNull PsAndroidDependency dependency) {
        myResolvedDependenciesPanel.setSelection(dependency);
      }
    });

    myResolvedDependenciesPanel.add(new ResolvedDependenciesPanel.SelectionListener() {
      @Override
      public void dependencySelected(@Nullable PsAndroidDependency dependency) {
        myDeclaredDependenciesPanel.setSelection(dependency);
      }
    });

    myAltPanel = new JPanel(new BorderLayout());

    ToolWindowHeader header = myResolvedDependenciesPanel.getHeader();

    JPanel minimizedContainerPanel = myResolvedDependenciesPanel.getMinimizedPanel();
    assert minimizedContainerPanel != null;
    myAltPanel.add(minimizedContainerPanel, BorderLayout.EAST);

    header.addMinimizeListener(new ToolWindowHeader.MinimizeListener() {
      @Override
      public void minimized() {
        minimizeResolvedDependenciesPanel();
      }
    });

    myResolvedDependenciesPanel.addRestoreListener(new ToolWindowPanel.RestoreListener() {
      @Override
      public void restored() {
        restoreResolvedDependenciesPanel();
      }
    });
  }

  private void restoreResolvedDependenciesPanel() {
    remove(myAltPanel);
    myAltPanel.remove(myDeclaredDependenciesPanel);
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    add(myVerticalSplitter, BorderLayout.CENTER);
    revalidate();
    repaint();
    saveMinimizedState(false);
  }

  private void minimizeResolvedDependenciesPanel() {
    remove(myVerticalSplitter);
    myVerticalSplitter.setFirstComponent(null);
    myAltPanel.add(myDeclaredDependenciesPanel, BorderLayout.CENTER);
    add(myAltPanel, BorderLayout.CENTER);
    revalidate();
    repaint();
    saveMinimizedState(true);
  }

  private static void saveMinimizedState(boolean minimize) {
    PsUISettings.getInstance().VARIANTS_DEPENDENCIES_MINIMIZE = minimize;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    boolean minimize = PsUISettings.getInstance().VARIANTS_DEPENDENCIES_MINIMIZE;
    if (minimize) {
      minimizeResolvedDependenciesPanel();
    }
    else {
      restoreResolvedDependenciesPanel();
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDeclaredDependenciesPanel);
    Disposer.dispose(myResolvedDependenciesPanel);
  }
}
