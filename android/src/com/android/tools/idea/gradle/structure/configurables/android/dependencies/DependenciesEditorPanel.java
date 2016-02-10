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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class DependenciesEditorPanel extends JPanel implements Disposable {
  @NotNull private final JBSplitter myVerticalSplitter;
  @NotNull private final EditableDependenciesPanel myDependenciesPanel;
  @NotNull private final VariantsToolWindowPanel myVariantsToolWindowPanel;
  @NotNull private final JPanel myAltPanel;

  DependenciesEditorPanel(@NotNull PsdAndroidModuleModel moduleModel) {
    super(new BorderLayout());

    myDependenciesPanel = new EditableDependenciesPanel(moduleModel);
    myVariantsToolWindowPanel = new VariantsToolWindowPanel(moduleModel, myDependenciesPanel);

    myVerticalSplitter = new OnePixelSplitter(false, "psi.dependencies.main.vertical.splitter.proportion", .75f);
    myVerticalSplitter.setFirstComponent(myDependenciesPanel);
    myVerticalSplitter.setSecondComponent(myVariantsToolWindowPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);

    myDependenciesPanel.updateTableColumnSizes();
    myDependenciesPanel.add(new EditableDependenciesPanel.SelectionListener() {
      @Override
      public void dependencyModelSelected(@NotNull PsdAndroidDependencyModel model) {
        myVariantsToolWindowPanel.setSelection(model);
      }
    });

    myVariantsToolWindowPanel.add(new VariantsToolWindowPanel.SelectionListener() {
      @Override
      public void dependencyModelSelected(@NotNull PsdAndroidDependencyModel model) {
        myDependenciesPanel.setSelection(model);
      }
    });

    myAltPanel = new JPanel(new BorderLayout());

    ToolWindowHeader header = myVariantsToolWindowPanel.getHeader();

    JPanel minimizedContainerPanel = myVariantsToolWindowPanel.getMinimizedPanel();
    assert minimizedContainerPanel != null;
    myAltPanel.add(minimizedContainerPanel, BorderLayout.EAST);

    header.addMinimizeListener(new ToolWindowHeader.MinimizeListener() {
      @Override
      public void minimized() {
        minimize();
      }
    }, this);

    myVariantsToolWindowPanel.addRestoreListener(new ToolWindowPanel.RestoreListener() {
      @Override
      public void restored() {
        restore();
      }
    }, this);
  }

  private void restore() {
    remove(myAltPanel);
    myAltPanel.remove(myDependenciesPanel);
    myVerticalSplitter.setFirstComponent(myDependenciesPanel);
    add(myVerticalSplitter, BorderLayout.CENTER);
    revalidate();
    repaint();
    saveMinimizedState(false);
  }

  private void minimize() {
    remove(myVerticalSplitter);
    myVerticalSplitter.setFirstComponent(null);
    myAltPanel.add(myDependenciesPanel, BorderLayout.CENTER);
    add(myAltPanel, BorderLayout.CENTER);
    revalidate();
    repaint();
    saveMinimizedState(true);
  }

  private static void saveMinimizedState(boolean minimize) {
    PsdUISettings.getInstance().VARIANTS_DEPENDENCIES_MINIMIZE = minimize;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    loadMinimizedState();
  }

  private void loadMinimizedState() {
    boolean minimize = PsdUISettings.getInstance().VARIANTS_DEPENDENCIES_MINIMIZE;
    if (minimize) {
      minimize();
    }
    else {
      restore();
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDependenciesPanel);
    Disposer.dispose(myVariantsToolWindowPanel);
  }
}
