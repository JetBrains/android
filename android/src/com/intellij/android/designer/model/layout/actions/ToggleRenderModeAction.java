/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.model.layout.actions;

import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.AndroidDesignerIcons;
import org.jetbrains.annotations.NotNull;

public class ToggleRenderModeAction extends ToggleAction {
  private final DesignerEditorPanel myDesigner;
  /** Whether we should render just the viewport */
  private static boolean ourRenderViewPort;

  public ToggleRenderModeAction(@NotNull DesignerEditorPanel designer) {
    myDesigner = designer;

    Presentation presentation = getTemplatePresentation();
    String label = "Toggle Viewport Render Mode";
    presentation.setDescription(label);
    presentation.setText(label);
    updateIcon(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updateIcon(e.getPresentation());
  }

  private static void updateIcon(Presentation presentation) {
    presentation.setIcon(ourRenderViewPort ? AndroidDesignerIcons.NormalRender : AndroidDesignerIcons.ViewportRender);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return ourRenderViewPort;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    ourRenderViewPort = state;
    updateIcon(e.getPresentation());
    ((AndroidDesignerEditorPanel)myDesigner).requestRender();
  }

  public static boolean isRenderViewPort() {
    return ourRenderViewPort;
  }
}
