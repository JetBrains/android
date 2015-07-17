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
package com.android.tools.idea.monitor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

public abstract class BaseMonitorView implements HierarchyListener, TimelineEventListener {
  @NotNull protected Project myProject;
  @NotNull protected JPanel myContentPane;

  protected BaseMonitorView(@NotNull Project project) {
    myProject = project;
    myContentPane = new JPanel(new BorderLayout());
    myContentPane.addHierarchyListener(this);
  }

  @Override
  public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
    if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
      if (!getSampler().isRunning()) {
        getSampler().start();
      }
    }
  }

  @Override
  public void onStart() {

  }

  @Override
  public void onStop() {

  }

  protected abstract DeviceSampler getSampler();

  protected boolean isShowing() {
    return myContentPane.isShowing();
  }

  protected void setComponent(@NotNull JComponent component) {
    myContentPane.removeAll();
    myContentPane.add(component, BorderLayout.CENTER);
  }
}
