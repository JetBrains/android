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
package com.android.tools.adtui.workbench;

import com.android.tools.adtui.workbench.AttachedToolWindow.AttachedToolWindowPanel;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ui.FocusUtil;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.util.Collection;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * All the settings of an {@link AttachedToolWindow} are shared globally for all instances
 * of the same {@link WorkBench} and tool window combination.<br/>
 * This class is responsible for notifying other {@link WorkBench}es that something has
 * changed.
 */
public class WorkBenchManager implements Disposable {
  private final Multimap<String, WorkBench<?>> myWorkBenches;
  private WorkBench<?> myActiveWorkBench = null;
  private AttachedToolWindowPanel myActiveToolWindow = null;

  public static WorkBenchManager getInstance() {
    return ApplicationManager.getApplication().getService(WorkBenchManager.class);
  }

  public WorkBenchManager() {
    myWorkBenches = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    if (ExperimentalUI.isNewUI()) {
      FocusUtil.addFocusOwnerListener(this, evt -> findActiveToolWindow());
    }
  }

  @Override
  public void dispose() {
    myWorkBenches.clear();
    myActiveWorkBench = null;
    myActiveToolWindow = null;
  }

  public void register(@NotNull WorkBench<?> workBench) {
    myWorkBenches.put(workBench.getName(), workBench);
  }

  public void unregister(@NotNull WorkBench<?> workBench) {
    myWorkBenches.remove(workBench.getName(),workBench);
    if (myActiveWorkBench == workBench) {
      resetActiveToolWindow();
    }
  }

  public void storeDefaultLayout() {
    for (String name : myWorkBenches.keySet()) {
      Optional<WorkBench<?>> workbench = myWorkBenches.get(name).stream().findFirst();
      if (workbench.isPresent()) {
        workbench.get().storeDefaultLayout();
        updateOtherWorkBenches(workbench.get());
      }
    }
  }

  public void restoreDefaultLayout() {
    for (String name : myWorkBenches.keySet()) {
      Optional<WorkBench<?>> workbench = myWorkBenches.get(name).stream().findFirst();
      if (workbench.isPresent()) {
        workbench.get().restoreDefaultLayout();
        updateOtherWorkBenches(workbench.get());
      }
    }
  }

  public void updateOtherWorkBenches(@NotNull WorkBench<?> workBench) {
    Collection<WorkBench<?>> workBenches = myWorkBenches.get(workBench.getName());
    workBenches.stream().filter(bench -> bench != workBench).forEach(WorkBench::updateModel);
  }

  @TestOnly
  public AttachedToolWindow<?> getActiveToolWindow() {
    return myActiveToolWindow != null ? myActiveToolWindow.getToolWindow() : null;
  }

  // Visible such that the document reference in AttachToolWindow is valid.
  void findActiveToolWindow() {
    if (myWorkBenches.isEmpty()) {
      resetActiveToolWindow();
      return;
    }
    Component component = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (component == null) {
      resetActiveToolWindow();
      return;
    }
    AttachedToolWindowPanel panel = (AttachedToolWindowPanel)SwingUtilities.getAncestorOfClass(AttachedToolWindowPanel.class, component);
    if (panel == null) {
      resetActiveToolWindow();
      return;
    }
    WorkBench<?> workBench = (WorkBench<?>)SwingUtilities.getAncestorOfClass(WorkBench.class, component);
    if (workBench == null || !myWorkBenches.containsValue(workBench)) {
      resetActiveToolWindow();
      return;
    }
    if (myActiveToolWindow != panel) {
      resetActiveToolWindow();
    }
    myActiveWorkBench = workBench;
    myActiveToolWindow = panel;
    panel.setActive(true);
  }

  private void resetActiveToolWindow() {
    if (myActiveToolWindow != null) {
      myActiveToolWindow.setActive(false);
    }
    myActiveWorkBench = null;
    myActiveToolWindow = null;
  }
}
