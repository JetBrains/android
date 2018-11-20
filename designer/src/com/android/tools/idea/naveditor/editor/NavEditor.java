/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.editor;

import com.android.tools.adtui.workbench.AutoHide;
import com.android.tools.adtui.workbench.Side;
import com.android.tools.adtui.workbench.Split;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.common.editor.DesignerEditor;
import com.android.tools.idea.common.editor.DesignerEditorPanel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.naveditor.property.NavPropertyPanelDefinition;
import com.android.tools.idea.naveditor.structure.StructurePanel;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class NavEditor extends DesignerEditor {

  public static final String NAV_EDITOR_ID = "nav-designer";

  private static final String WORKBENCH_NAME = "NAV_EDITOR";

  public NavEditor(@NotNull VirtualFile file, @NotNull Project project) {
    super(file, project);
  }

  @NotNull
  @Override
  public String getEditorId() {
    return NAV_EDITOR_ID;
  }

  @NotNull
  @Override
  protected DesignerEditorPanel createEditorPanel() {
    WorkBench<DesignSurface> workBench = new WorkBench<>(myProject, WORKBENCH_NAME, this);
    NavDesignSurface surface = new NavDesignSurface(myProject, this);
    DesignerEditorPanel panel = new DesignerEditorPanel(this, myProject, myFile, workBench, surface, null, (androidFacet) -> ImmutableList
      .of(new NavPropertyPanelDefinition(androidFacet, Side.RIGHT, Split.TOP, AutoHide.DOCKED),
          new StructurePanel.StructurePanelDefinition()));
    surface.setEditorPanel(panel);
    return panel;
  }

  @NotNull
  @Override
  public String getName() {
    return "Design";
  }
}
