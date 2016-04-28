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

import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import org.jetbrains.annotations.NotNull;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.Nullable;

public class NlPropertiesWindowManager extends NlAbstractWindowManager {
  private NlPropertiesManager myPropertiesManager;

  public NlPropertiesWindowManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @NotNull
  public static NlPropertiesWindowManager get(@NotNull Project project) {
    return project.getComponent(NlPropertiesWindowManager.class);
  }

  @Override
  protected void initToolWindow() {
    initToolWindow("Nl-Properties", AllIcons.Toolwindows.ToolWindowStructure);
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    if (designer == null) {
      myToolWindow.setAvailable(false, null);
      if (myPropertiesManager != null) {
        myPropertiesManager.setDesignSurface(null);
      }
    }
    else {
      if (myPropertiesManager == null) {
        myPropertiesManager = new NlPropertiesManager(myProject, getDesignSurface(designer));
        createWindowContent(myPropertiesManager.getConfigurationPanel(), myPropertiesManager.getConfigurationPanel(), null);
      }
      myPropertiesManager.setDesignSurface(getDesignSurface(designer));
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
      // The preview tool window does not have a properties pane.
      return null;
    }

    myPropertiesManager = new NlPropertiesManager(myProject, getDesignSurface(designer));
    return createContent(designer, myPropertiesManager, "Properties", AllIcons.Toolwindows.ToolWindowStructure, myPropertiesManager.getConfigurationPanel(),
                         myPropertiesManager.getConfigurationPanel(), 320, null);
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "NlPropertiesWindowManager";
  }
}
