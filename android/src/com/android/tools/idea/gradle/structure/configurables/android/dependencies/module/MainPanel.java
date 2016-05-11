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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.AbstractMainDependenciesPanel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ui.UIUtil.FontSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.DependencyGraphPanel.findTopDependencyNode;
import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;
import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.FOR_NAVIGATION;
import static com.intellij.util.ui.UIUtil.getLabelFont;

class MainPanel extends AbstractMainDependenciesPanel {
  @NotNull private final JBSplitter myVerticalSplitter;

  @NotNull private final DeclaredDependenciesPanel myDeclaredDependenciesPanel;
  @NotNull private final DependencyGraphPanel myDependencyGraphPanel;

  @NotNull private final TargetArtifactsPanel myTargetArtifactsPanel;
  @NotNull private final JPanel myAltPanel;

  @NotNull private AbstractDependenciesPanel mySelectedView;
  private String mySelectedDependency;

  MainPanel(@NotNull PsAndroidModule module, @NotNull PsContext context, @NotNull List<PsModule> extraTopModules) {
    super(context, extraTopModules);

    myDeclaredDependenciesPanel = new DeclaredDependenciesPanel(module, context);
    myDeclaredDependenciesPanel.setHistory(getHistory());
    mySelectedView = myDeclaredDependenciesPanel;

    myDependencyGraphPanel = new DependencyGraphPanel(module, context);
    myDependencyGraphPanel.setHistory(getHistory());

    myTargetArtifactsPanel = new TargetArtifactsPanel(module, context);

    myVerticalSplitter = createMainVerticalSplitter();
    myVerticalSplitter.setFirstComponent(myDeclaredDependenciesPanel);
    myVerticalSplitter.setSecondComponent(myTargetArtifactsPanel);

    add(myVerticalSplitter, BorderLayout.CENTER);

    addLabelToHeader(myDeclaredDependenciesPanel, createSwitchViewLabel("Show Dependency Graph", myDependencyGraphPanel));
    addLabelToHeader(myDependencyGraphPanel, createSwitchViewLabel("Show Declared Dependencies Only", myDeclaredDependenciesPanel));

    myDeclaredDependenciesPanel.updateTableColumnSizes();
    myDeclaredDependenciesPanel.add(newSelection -> {
      setSelectedDependency(newSelection);
      myTargetArtifactsPanel.displayTargetArtifacts(newSelection);
    });

    myDependencyGraphPanel.add(newSelection -> {
      AbstractDependencyNode<? extends PsAndroidDependency> node = newSelection;
      if (node != null) {
        node = findTopDependencyNode(node);
      }
      PsAndroidDependency selection = node != null ? node.getFirstModel() : null;
      setSelectedDependency(selection);
      myTargetArtifactsPanel.displayTargetArtifacts(selection);
    });

    myAltPanel = new JPanel(new BorderLayout());

    ToolWindowHeader header = myTargetArtifactsPanel.getHeader();

    JPanel minimizedContainerPanel = myTargetArtifactsPanel.getMinimizedPanel();
    assert minimizedContainerPanel != null;
    myAltPanel.add(minimizedContainerPanel, BorderLayout.EAST);

    header.addMinimizeListener(this::minimizeTargetArtifactsPanel);

    myTargetArtifactsPanel.addRestoreListener(this::restoreTargetArtifactsPanel);
  }

  private void setSelectedDependency(@Nullable PsAndroidDependency selection) {
    mySelectedDependency = selection != null ? selection.toText(FOR_NAVIGATION) : null;
  }

  @NotNull
  private HyperlinkLabel createSwitchViewLabel(@NotNull String text, @NotNull AbstractDependenciesPanel view) {
    HyperlinkLabel hyperlinkLabel = new HyperlinkLabel(text);
    hyperlinkLabel.setFont(getLabelFont(FontSize.SMALL));
    hyperlinkLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        if (PsUISettings.getInstance().TARGET_ARTIFACTS_MINIMIZE) {
          myAltPanel.remove(mySelectedView);
          myAltPanel.add(view, BorderLayout.CENTER);
        }
        else {
          myVerticalSplitter.setFirstComponent(view);
        }
        mySelectedView = view;
        revalidateAndRepaint(MainPanel.this);

        mySelectedView.selectDependency(mySelectedDependency);
        mySelectedView.getPreferredFocusedComponent().requestFocusInWindow();
      }
    });
    return hyperlinkLabel;
  }

  private static void addLabelToHeader(@NotNull AbstractDependenciesPanel dependenciesPanel, @NotNull HyperlinkLabel label) {
    dependenciesPanel.getHeader().add(label, BorderLayout.EAST);
  }

  private void restoreTargetArtifactsPanel() {
    remove(myAltPanel);
    myAltPanel.remove(mySelectedView);
    myVerticalSplitter.setFirstComponent(mySelectedView);
    add(myVerticalSplitter, BorderLayout.CENTER);
    revalidateAndRepaint(this);
    saveMinimizedState(false);
  }

  private void minimizeTargetArtifactsPanel() {
    remove(myVerticalSplitter);
    myVerticalSplitter.setFirstComponent(null);
    myAltPanel.add(mySelectedView, BorderLayout.CENTER);
    add(myAltPanel, BorderLayout.CENTER);
    revalidateAndRepaint(this);
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
    myDependencyGraphPanel.setHistory(history);
  }

  public void putPath(@NotNull Place place, @NotNull String dependency) {
    mySelectedView.putPath(place, dependency);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return mySelectedView.navigateTo(place, requestFocus);
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    myDeclaredDependenciesPanel.queryPlace(place);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDeclaredDependenciesPanel);
    Disposer.dispose(myDependencyGraphPanel);
    Disposer.dispose(myTargetArtifactsPanel);
  }
}
