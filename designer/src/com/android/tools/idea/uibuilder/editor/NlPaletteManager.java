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
import com.android.tools.idea.uibuilder.palette.NlPalettePanel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NlPaletteManager extends NlAbstractWindowManager {
  private NlPalettePanel myPalette;

  public NlPaletteManager(@NotNull Project project, @NotNull FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @NonNull
  public static NlPaletteManager get(@NonNull Project project) {
    return project.getComponent(NlPaletteManager.class);
  }

  @Override
  protected void initToolWindow() {
    initToolWindow("Nl-Palette", AllIcons.Toolwindows.ToolWindowPalette);
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    if (designer == null) {
      myToolWindow.setAvailable(false, null);
      if (myPalette != null) {
        myPalette.setDesignSurface(null);
      }
    }
    else {
      if (myPalette == null) {
        myPalette = new NlPalettePanel();
        createWindowContent(myPalette, myPalette.getFocusedComponent(), myPalette.getActions());
      }
      myPalette.setDesignSurface(getDesignSurface(designer));
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.LEFT;
  }

  @NotNull
  private static DesignSurface getDesignSurface(@NotNull DesignerEditorPanelFacade designer) {
    assert designer instanceof NlEditorPanel;
    NlEditorPanel editor = (NlEditorPanel)designer;
    return editor.getSurface();
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    NlPalettePanel palette = new NlPalettePanel();
    palette.setDesignSurface(getDesignSurface(designer));

    return createContent(designer,
                         palette,
                         "Palette",
                         AllIcons.Toolwindows.ToolWindowPalette,
                         palette,
                         palette.getFocusedComponent(),
                         180,
                         palette.getActions());
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "NlPaletteManager";
  }
}
