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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.LightToolWindowContent;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface PaletteToolWindow extends LightToolWindowContent {
  /**
   * Return a {@link JComponent} for this designer tool.
   */
  JComponent getDesignerComponent();

  /**
   * Return a {@link JComponent} in the designer tool that should receive focus initially.
   */
  JComponent getFocusedComponent();

  /**
   * Return the actions that should be added to the tool window bar.
   */
  AnAction[] getActions();

  /**
   * Update the {@link DesignSurface} that this designer tool window is affecting.
   */
  void setDesignSurface(@Nullable DesignSurface designSurface);

  /**
   * Set focus on the palette.
   */
  void requestFocusInPalette();

  /**
   * Set focus on the component tree.
   */
  void requestFocusInComponentTree();
}
