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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.editor.NlAbstractWindowManager;
import com.android.tools.idea.uibuilder.editor.NlPaletteManager;
import com.android.tools.idea.uibuilder.palette.NlPalettePanel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ShowComponentTreeAction extends LightToolWindowAction<NlPalettePanel> {

  public ShowComponentTreeAction() {
    super(NlPalettePanel.class);
  }

  @NotNull
  @Override
  protected NlAbstractWindowManager getWindowManager(@NotNull Project project) {
    return NlPaletteManager.get(project);
  }

  @Override
  protected void actionPerformed(@NotNull NlPalettePanel palette) {
    palette.activateComponentTree();
  }
}
