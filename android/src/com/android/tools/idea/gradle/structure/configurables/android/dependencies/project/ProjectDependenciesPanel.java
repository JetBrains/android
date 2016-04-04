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
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractMainPanel;
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

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.createMainVerticalSplitter;

class ProjectDependenciesPanel extends AbstractMainPanel {
  @NotNull private final JBSplitter myVerticalSplitter;
  @NotNull private final DeclaredDependenciesPanel myDeclaredDependenciesPanel;
  @NotNull private final TargetModulesPanel myTargetModulesPanel;

  ProjectDependenciesPanel(@NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(context, extraTopModules);

    myDeclaredDependenciesPanel = new DeclaredDependenciesPanel(context);
    myDeclaredDependenciesPanel.setHistory(getHistory());

    myTargetModulesPanel = new TargetModulesPanel(context);

    myDeclaredDependenciesPanel.add(myTargetModulesPanel::displayTargetModules);

    myVerticalSplitter = createMainVerticalSplitter();
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    myVerticalSplitter.setSecondComponent(myTargetModulesPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);
  }

  @Override
  public void setHistory(History history) {
    super.setHistory(history);
    myDeclaredDependenciesPanel.setHistory(history);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return myDeclaredDependenciesPanel.navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    myDeclaredDependenciesPanel.queryPlace(place);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDeclaredDependenciesPanel);
    Disposer.dispose(myTargetModulesPanel);
  }
}
