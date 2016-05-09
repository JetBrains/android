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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractMainDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
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

class MainPanel extends AbstractMainDependenciesPanel {
  @NotNull private final JBSplitter myVerticalSplitter;
  @NotNull private final DeclaredDependenciesPanel myDeclaredDependenciesPanel;
  @NotNull private final TargetArtifactsPanel myTargetArtifactsPanel;
  @NotNull private final JPanel myAltPanel;

  MainPanel(@NotNull PsAndroidModule module, @NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(context, extraTopModules);

    myDeclaredDependenciesPanel = new DeclaredDependenciesPanel(module, context);
    myDeclaredDependenciesPanel.setHistory(getHistory());

    myTargetArtifactsPanel = new TargetArtifactsPanel(module, context);

    myVerticalSplitter = createMainVerticalSplitter();
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    myVerticalSplitter.setSecondComponent(myTargetArtifactsPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);

    myDeclaredDependenciesPanel.updateTableColumnSizes();
    myDeclaredDependenciesPanel.add(myTargetArtifactsPanel::displayTargetArtifacts);

    myAltPanel = new JPanel(new BorderLayout());

    ToolWindowHeader header = myTargetArtifactsPanel.getHeader();

    JPanel minimizedContainerPanel = myTargetArtifactsPanel.getMinimizedPanel();
    assert minimizedContainerPanel != null;
    myAltPanel.add(minimizedContainerPanel, BorderLayout.EAST);

    header.addMinimizeListener(this::minimizeTargetArtifactsPanel);

    myTargetArtifactsPanel.addRestoreListener(this::restoreTargetArtifactsPanel);
  }

  private void restoreTargetArtifactsPanel() {
    remove(myAltPanel);
    myAltPanel.remove(myDeclaredDependenciesPanel);
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    add(myVerticalSplitter, BorderLayout.CENTER);
    revalidate();
    repaint();
    saveMinimizedState(false);
  }

  private void minimizeTargetArtifactsPanel() {
    remove(myVerticalSplitter);
    myVerticalSplitter.setFirstComponent(null);
    myAltPanel.add(myDeclaredDependenciesPanel, BorderLayout.CENTER);
    add(myAltPanel, BorderLayout.CENTER);
    revalidate();
    repaint();
    saveMinimizedState(true);
  }

  private static void saveMinimizedState(boolean minimize) {
    PsUISettings.getInstance().TARGET_ARTIFACTS_MINIMIZE = minimize;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    boolean minimize = PsUISettings.getInstance().TARGET_ARTIFACTS_MINIMIZE;
    if (minimize) {
      minimizeTargetArtifactsPanel();
    }
    else {
      restoreTargetArtifactsPanel();
    }
  }

  @Override
  public void setHistory(History history) {
    super.setHistory(history);
    myDeclaredDependenciesPanel.setHistory(history);
  }

  public void putPath(@NotNull Place place, @NotNull String dependency) {
    myDeclaredDependenciesPanel.putPath(place, dependency);
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
    Disposer.dispose(myTargetArtifactsPanel);
  }
}
