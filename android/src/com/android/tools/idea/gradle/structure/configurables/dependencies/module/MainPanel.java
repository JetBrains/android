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
package com.android.tools.idea.gradle.structure.configurables.dependencies.module;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.dependencies.AbstractMainDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsResolvedDependency;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MainPanel extends AbstractMainDependenciesPanel {
  @NotNull private final JBSplitter myVerticalSplitter;

  @NotNull private final DeclaredDependenciesPanel myDeclaredDependenciesPanel;
  @NotNull private final ResolvedDependenciesPanel myResolvedDependenciesPanel;
  @NotNull private final JPanel myAltPanel;

  private int myQueuedSelectionCounter = 0;

  public MainPanel(@NotNull PsModule module, @NotNull PsContext context) {
    super(context);

    myDeclaredDependenciesPanel = new DeclaredDependenciesPanel(module, context);
    myDeclaredDependenciesPanel.setHistory(getHistory());

    myResolvedDependenciesPanel = new ResolvedDependenciesPanel(module, context, myDeclaredDependenciesPanel);

    myVerticalSplitter = createMainVerticalSplitter();
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    myVerticalSplitter.setSecondComponent(myResolvedDependenciesPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);

    myDeclaredDependenciesPanel.updateTableColumnSizes();
    myDeclaredDependenciesPanel.add(new SelectionChangeListener<PsBaseDependency>() {
      @Override
      public void selectionChanged(@Nullable PsBaseDependency newSelection) {
        if (myQueuedSelectionCounter == 0) {
          myQueuedSelectionCounter++;
          myResolvedDependenciesPanel
            .setSelection(null)
            .doWhenProcessed(() -> myQueuedSelectionCounter--);
        }
      }
    });

    myResolvedDependenciesPanel.add(new SelectionChangeListener<PsBaseDependency>() {
      @Override
      public void selectionChanged(@Nullable PsBaseDependency newSelection) {
        if (newSelection instanceof PsResolvedDependency) {
          PsResolvedDependency resolvedDependency = (PsResolvedDependency)newSelection;
          final List<PsDeclaredDependency> declaredDependencies = resolvedDependency.getDeclaredDependencies();
          if (myQueuedSelectionCounter == 0) {
            myQueuedSelectionCounter++;
            if (!declaredDependencies.isEmpty()) {
              myDeclaredDependenciesPanel.setSelection(declaredDependencies).doWhenProcessed(() -> myQueuedSelectionCounter--);
            }
            else {
              myDeclaredDependenciesPanel.setSelection(null).doWhenProcessed(() -> myQueuedSelectionCounter--);
            }
          }
        }
      }
    });

    JPanel minimizedContainerPanel = myResolvedDependenciesPanel.getMinimizedPanel();
    assert minimizedContainerPanel != null;
    myAltPanel = new JPanel(new BorderLayout());
    myAltPanel.add(minimizedContainerPanel, BorderLayout.EAST);

    ToolWindowHeader header = myResolvedDependenciesPanel.getHeader();
    header.addMinimizeListener(this::minimizeResolvedDependenciesPanel);

    myResolvedDependenciesPanel.addRestoreListener(this::restoreResolvedDependenciesPanel);
  }

  private void restoreResolvedDependenciesPanel() {
    remove(myAltPanel);
    myAltPanel.remove(myDeclaredDependenciesPanel);
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    add(myVerticalSplitter, BorderLayout.CENTER);
    revalidateAndRepaint(this);
    saveMinimizedState(false);
  }

  private void minimizeResolvedDependenciesPanel() {
    remove(myVerticalSplitter);
    myVerticalSplitter.setFirstComponent(null);
    myAltPanel.add(myDeclaredDependenciesPanel, BorderLayout.CENTER);
    add(myAltPanel, BorderLayout.CENTER);
    revalidateAndRepaint(this);
    saveMinimizedState(true);
  }

  private void saveMinimizedState(boolean minimize) {
    getContext().getUiSettings().RESOLVED_DEPENDENCIES_MINIMIZE = minimize;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    boolean minimize = getContext().getUiSettings().RESOLVED_DEPENDENCIES_MINIMIZE;
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
    Disposer.dispose(myResolvedDependenciesPanel);
  }
}
