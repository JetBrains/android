/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof;

import com.android.tools.idea.editors.hprof.tables.heaptable.HeapTableManager;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

public class ComputeDominatorAction extends AnAction {
  private Snapshot mySnapshot;
  private HeapTableManager myHeapTableManager;
  @NotNull Project myProject;

  public ComputeDominatorAction(@NotNull Snapshot snapshot, @NotNull HeapTableManager heapTableManager, @NotNull Project project) {
    super(null, "Compute Dominators", AndroidIcons.Ddms.AllocationTracker);
    mySnapshot = snapshot;
    myHeapTableManager = heapTableManager;
    myProject = project;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ProgressManager.getInstance().run(new ComputeDominatorIndicator(myProject));
  }

  private class ComputeDominatorIndicator extends Task.Modal {
    public ComputeDominatorIndicator(@NotNull Project project) {
      super(project, "Computing dominators...", true);
    }

    @Override
    public void onSuccess() {
      super.onSuccess();
      myHeapTableManager.notifyDominatorsComputed();
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      // TODO do this in a separate thread.
      mySnapshot.computeDominators();
    }
  }
}
