/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.memory;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

class MemorySnapshotAction extends AnAction {

  @NotNull
  private final MemoryProfilingView myView;

  public MemorySnapshotAction(@NotNull MemoryProfilingView view) {
    super("Take a memory snapshot", "Takes a memory snapshot", AndroidIcons.Ddms.ScreenCapture);
    myView = view;
  }

  @Override
  public void update(AnActionEvent e) {
    MemorySampler sampler = myView.getMemorySampler();
    e.getPresentation().setEnabled(sampler != null && sampler.canRequestHeapDump());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    MemorySampler sampler = myView.getMemorySampler();
    if (sampler != null) {
      sampler.requestHeapDump();
    }
  }
}
