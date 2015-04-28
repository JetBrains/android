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

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.structure.NlStructurePanel;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;

public class NlStructureManager extends NlAbstractManager {

  public NlStructureManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @NonNull
  public static NlStructureManager get(@NonNull Project project) {
    return project.getComponent(NlStructureManager.class);
  }

  @Override
  protected void initToolWindow() {
    initToolWindow("Nl-Structure", AllIcons.Toolwindows.ToolWindowStructure);
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    // should be whatever is bound in NlEditor
    assert designer instanceof NlEditorPanel;

    NlStructurePanel structurePanel = new NlStructurePanel(((NlEditorPanel)designer).getSurface());

    return createContent(designer,
                         structurePanel,
                         "Structure",
                         AllIcons.Toolwindows.ToolWindowStructure,
                         structurePanel.getPanel(),
                         structurePanel.getPanel(),
                         320,
                         null);  }

  @NotNull
  @Override
  public String getComponentName() {
    return "NlStructureManager";
  }
}
