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
package com.android.tools.idea.uibuilder.editor;

import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.structure.NlStructurePanel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NlStructureManager extends NlAbstractWindowManager {
  private NlStructurePanel myStructurePanel;

  public NlStructureManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @NotNull
  public static NlStructureManager get(@NotNull Project project) {
    return project.getComponent(NlStructureManager.class);
  }

  @Override
  protected void initToolWindow() {
    initToolWindow("Nl-Structure", AllIcons.Toolwindows.ToolWindowStructure);
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    if (designer == null) {
      myToolWindow.setAvailable(false, null);
      if (myStructurePanel != null) {
        myStructurePanel.setDesignSurface(null);
      }
    }
    else {
      if (myStructurePanel == null) {
        myStructurePanel = new NlStructurePanel(myProject, getDesignSurface(designer));
        createWindowContent(myStructurePanel.getPanel(), myStructurePanel.getPanel(), null);
      }
      myStructurePanel.setDesignSurface(getDesignSurface(designer));
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    if (!(designer instanceof NlEditorPanel)) {
      // The preview tool window does not have a structure pane.
      return null;
    }
    NlStructurePanel structurePanel = new NlStructurePanel(myProject, getDesignSurface(designer));

    return createContent(designer, structurePanel, "Structure", AllIcons.Toolwindows.ToolWindowStructure, structurePanel.getPanel(),
                         structurePanel.getPanel(), 320, null);
  }

  @Override
  public void disposeComponent() {
    if (myStructurePanel != null) {
      myStructurePanel.dispose();
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "NlStructureManager";
  }
}
