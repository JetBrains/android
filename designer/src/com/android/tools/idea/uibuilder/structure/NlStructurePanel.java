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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.LightToolWindowContent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

// TODO: this can be inlined if we know that it doesn't have to include the component tree.
// Otherwise, look at DesignerToolWindow to see how to add a splitter and include the component tree & properties panel
public class NlStructurePanel implements LightToolWindowContent {
  private final NlPropertiesPanel myPropertiesPanel;

  public NlStructurePanel(@NotNull DesignSurface designSurface) {
    myPropertiesPanel = new NlPropertiesPanel(designSurface.getCurrentScreenView());
  }

  public JComponent getPanel() {
    return myPropertiesPanel;
  }

  @Override
  public void dispose() {
  }
}
