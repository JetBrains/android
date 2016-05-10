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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractMainDependenciesPanel;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

class MainPanel extends AbstractMainDependenciesPanel {
  @NotNull private final DependenciesPanel myDependenciesPanel;
  @NotNull private final TargetModulesPanel myTargetModulesPanel;

  MainPanel(@NotNull PsModule module, @NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(context, extraTopModules);
    myTargetModulesPanel = new TargetModulesPanel(context);

    myDependenciesPanel = new DependenciesPanel(module, context);
    myDependenciesPanel.setHistory(getHistory());
    myDependenciesPanel.add(myTargetModulesPanel::displayTargetModules);

    JBSplitter verticalSplitter = createMainVerticalSplitter();
    verticalSplitter.setFirstComponent(myDependenciesPanel);
    verticalSplitter.setSecondComponent(myTargetModulesPanel);

    add(verticalSplitter, BorderLayout.CENTER);
  }

  @Override
  public void setHistory(History history) {
    super.setHistory(history);
    myDependenciesPanel.setHistory(history);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return myDependenciesPanel.navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    myDependenciesPanel.queryPlace(place);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDependenciesPanel);
    Disposer.dispose(myTargetModulesPanel);
  }
}
