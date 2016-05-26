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

package com.android.tools.idea.editors.layeredimage;

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.editor.NlAbstractWindowManager;
import com.android.tools.pixelprobe.Image;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LayersManager extends NlAbstractWindowManager {
  private LayersPanel myLayersPanel;

  public LayersManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @NotNull
  public static LayersManager get(@NotNull Project project) {
    return project.getComponent(LayersManager.class);
  }

  @Override
  protected void initToolWindow() {
    initToolWindow("Image Layers", AllIcons.Toolwindows.ToolWindowChanges);
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    if (designer == null) {
      myToolWindow.setAvailable(false, null);
      if (myLayersPanel != null) {
        myLayersPanel.setImage(null);
      }
    } else {
      if (myLayersPanel == null) {
        myLayersPanel = new LayersPanel();
        createWindowContent(myLayersPanel, myLayersPanel, null);
      }
      myLayersPanel.setImage(getImage(designer));
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.LEFT;
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    if (!(designer instanceof LayeredImageEditorPanel)) {
      return null;
    }

    LightToolWindow toolWindow = (LightToolWindow) designer.getClientProperty(getComponentName());
    if (toolWindow != null) {
      return toolWindow;
    }

    LayersPanel layersPanel = new LayersPanel();
    layersPanel.setImage(getImage(designer));

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

    return new LightToolWindow(layersPanel, "Image Layers", AllIcons.Toolwindows.ToolWindowPalette,
                               layersPanel, layersPanel, designer.getContentSplitter(), anchor, this,
                               myProject, propertiesComponent, getVisibilityKeyName(designer), 200, null);
  }

  @Override
  public void disposeComponent() {
    myLayersPanel.dispose();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "LayeredImageEditor-LayersManager";
  }

  @Nullable
  @Override
  protected DesignerEditorPanelFacade getDesigner(@Nullable FileEditor editor) {
    if (editor instanceof LayeredImageEditor) {
      return ((LayeredImageEditor) editor).getComponent();
    }
    return null;
  }

  @Nullable
  private static Image getImage(@NotNull DesignerEditorPanelFacade designer) {
    if (designer instanceof LayeredImageEditorPanel) {
      return ((LayeredImageEditorPanel) designer).getImage();
    }
    return null;
  }

  @NonNull
  private String getVisibilityKeyName(@NotNull DesignerEditorPanelFacade designer) {
    return getComponentName() + "-" + designer.getClass().getSimpleName();
  }
}
