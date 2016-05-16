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
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;

class MainPanel extends AbstractMainDependenciesPanel {
  @NotNull private final JBSplitter myVerticalSplitter;

  @NotNull private final DependencyGraphPanel myDependencyGraphPanel;
  @NotNull private final TargetModulesPanel myTargetModulesPanel;
  @NotNull private final JPanel myAltPanel;

  MainPanel(@NotNull PsModule module, @NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(context, extraTopModules);
    myTargetModulesPanel = new TargetModulesPanel(context);

    myDependencyGraphPanel = new DependencyGraphPanel(module, context);
    myDependencyGraphPanel.setHistory(getHistory());
    myDependencyGraphPanel.add(myTargetModulesPanel::displayTargetModules);

    myVerticalSplitter = createMainVerticalSplitter();
    myVerticalSplitter.setFirstComponent(myDependencyGraphPanel);
    myVerticalSplitter.setSecondComponent(myTargetModulesPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);

    JPanel minimizedContainerPanel = myTargetModulesPanel.getMinimizedPanel();
    assert minimizedContainerPanel != null;
    myAltPanel = new JPanel(new BorderLayout());
    myAltPanel.add(minimizedContainerPanel, BorderLayout.EAST);

    ToolWindowHeader header = myTargetModulesPanel.getHeader();
    header.addMinimizeListener(this::minimizeResolvedDependenciesPanel);

    myTargetModulesPanel.addRestoreListener(this::restoreResolvedDependenciesPanel);
  }

  private void restoreResolvedDependenciesPanel() {
    remove(myAltPanel);
    myAltPanel.remove(myDependencyGraphPanel);
    myVerticalSplitter.setFirstComponent(myDependencyGraphPanel);
    add(myVerticalSplitter, BorderLayout.CENTER);
    revalidateAndRepaint(this);
    saveMinimizedState(false);
  }

  private void minimizeResolvedDependenciesPanel() {
    remove(myVerticalSplitter);
    myVerticalSplitter.setFirstComponent(null);
    myAltPanel.add(myDependencyGraphPanel, BorderLayout.CENTER);
    add(myAltPanel, BorderLayout.CENTER);
    revalidateAndRepaint(this);
    saveMinimizedState(true);
  }

  private static void saveMinimizedState(boolean minimize) {
    PsUISettings.getInstance().TARGET_MODULES_MINIMIZE = minimize;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    boolean minimize = PsUISettings.getInstance().TARGET_MODULES_MINIMIZE;
    if (minimize) {
      minimizeResolvedDependenciesPanel();
    }
    else {
      restoreResolvedDependenciesPanel();
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
    Disposer.dispose(myTargetModulesPanel);
  }
}
