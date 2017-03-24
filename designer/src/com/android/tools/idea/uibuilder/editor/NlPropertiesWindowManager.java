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
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NlPropertiesWindowManager extends NlAbstractWindowManager {
  private static final String PROPERTIES_WINDOW_ID = "Properties";

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
    initToolWindow(PROPERTIES_WINDOW_ID, AllIcons.Toolwindows.ToolWindowStructure);
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
        myPropertiesManager = new NlPropertiesManager(myProject, null);
        createWindowContent(myPropertiesManager.getConfigurationPanel(),
                            myPropertiesManager.getConfigurationPanel(),
                            myPropertiesManager.getActions());
      }
      myPropertiesManager.setDesignSurface(getDesignSurface(designer));
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  /**
   * Return true if the specified {@link NlPropertiesManager} is the active properties manager for this editor.
   * There are only 2 possible instances: the floating tool window represented by {@link #myPropertiesManager}
   * or a {@link LightToolWindow} that are docked with the editor.
   * This is a hacky solution...
   */
  public boolean isActivePropertiesManager(@NotNull NlPropertiesManager propertiesManager) {
    if (propertiesManager == myPropertiesManager) {
      // This is the floating tool window properties manager.
      // It is active if the design surface is currently set see {@link #updateToolWindow}.
      return propertiesManager.getDesignSurface() != null;
    }
    else {
      // This is the {@link LightToolWindow} properties manager.
      // It is active if the floating tool window is not active or doesn't exist.
      return myPropertiesManager == null || myPropertiesManager.getDesignSurface() == null;
    }
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  public String getVisibilityKeyName(@NotNull DesignerEditorPanelFacade designer) {
    return getComponentName()+ "-" + designer.getClass().getSimpleName();
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    if (!(designer instanceof NlEditorPanel)) {
      // The preview tool window does not have a properties pane.
      return null;
    }

    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    // When LightToolWindowManager#getEditorMode() is public (or a constructor which lets
    // me not specify it) is available and upstreamed, replace the following with just
    // anchor = getEditorMode() :
    String value = propertiesComponent.getValue(myEditorModeKey);
    ToolWindowAnchor anchor;
    if (value == null) {
      anchor = getAnchor();
    } else {
      anchor = value.equals("ToolWindow") ? null : ToolWindowAnchor.fromText(value);
    }

    NlPropertiesManager properties = new NlPropertiesManager(myProject, getDesignSurface(designer));
    return new LightToolWindow(properties, PROPERTIES_WINDOW_ID, AllIcons.Toolwindows.ToolWindowStructure,
                               properties.getConfigurationPanel(), properties.getConfigurationPanel(),
                               designer.getContentSplitter(), anchor, this, myProject, propertiesComponent,
                               getVisibilityKeyName(designer), 320, properties.getActions());
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "NlPropertiesWindowManager";
  }
}
