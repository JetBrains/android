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
package com.android.tools.idea.uibuilder.structure;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class ToggleBoundsVisibility extends AnAction {
  static final String BOUNDS_VISIBLE_PROPERTY = "NlBoundsVisible";

  private final PropertiesComponent myProperties;
  private final Component myComponentTree;

  public ToggleBoundsVisibility(@NotNull PropertiesComponent properties, @NotNull Component componentTree) {
    myProperties = properties;
    myComponentTree = componentTree;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean visible = myProperties.getBoolean(BOUNDS_VISIBLE_PROPERTY);
    event.getPresentation().setText(visible ? "Hide Bounds in Component Tree" : "Show Bounds in Component Tree");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myProperties.setValue(BOUNDS_VISIBLE_PROPERTY, !myProperties.getBoolean(BOUNDS_VISIBLE_PROPERTY));
    myComponentTree.repaint();
  }
}
