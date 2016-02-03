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
  @NotNull private final PsdAndroidModuleModel myModuleModel;
  @NotNull private final JBSplitter myVerticalSplitter;
  @NotNull private final EditableDependenciesPanel myDependenciesPanel;
  @NotNull private final VariantTreeViewPanel myVariantTreeViewPanel;

  DependenciesEditorPanel(@NotNull PsdAndroidModuleModel moduleModel) {
    super(new BorderLayout());
    myModuleModel = moduleModel;

    myDependenciesPanel = new EditableDependenciesPanel(moduleModel);
    myVariantTreeViewPanel = new VariantTreeViewPanel(moduleModel);

    myVerticalSplitter = new OnePixelSplitter(false, "psi.dependencies.main.vertical.splitter.proportion", .75f);
    myVerticalSplitter.setFirstComponent(myDependenciesPanel);
    myVerticalSplitter.setSecondComponent(myVariantTreeViewPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);

    myDependenciesPanel.updateTableColumnSizes();
    myDependenciesPanel.add(new EditableDependenciesPanel.SelectionListener() {
      @Override
      public void dependencyModelSelected(@NotNull PsdAndroidDependencyModel model) {
        myVariantTreeViewPanel.select(model);
      }
    });
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDependenciesPanel);
    Disposer.dispose(myVariantTreeViewPanel);
  }
}
