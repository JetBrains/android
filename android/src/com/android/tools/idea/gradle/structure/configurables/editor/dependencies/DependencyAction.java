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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

abstract class DependencyAction implements ActionListener, Runnable {
  @NotNull final String text;
  @NotNull final Icon icon;
  @NotNull final DependenciesPanel dependenciesPanel;
  final int index;

  DependencyAction(@NotNull String text, @NotNull Icon icon, @NotNull DependenciesPanel dependenciesPanel, int index) {
    this.text = text;
    this.icon = icon;
    this.dependenciesPanel = dependenciesPanel;
    this.index = index;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    execute();
  }

  final void execute() {
    dependenciesPanel.runAction(this);
  }
}
