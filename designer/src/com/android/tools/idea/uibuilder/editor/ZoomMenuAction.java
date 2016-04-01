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
import com.android.tools.idea.configurations.FlatComboAction;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import icons.AndroidIcons;

import javax.swing.*;

/**
 * Dropdown menu action shown in the layout editor toolbar for choosing a different view size
 */
public class ZoomMenuAction extends FlatComboAction {
  @NotNull private final DesignSurface mySurface;

  public ZoomMenuAction(@NotNull DesignSurface surface) {
    mySurface = surface;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Set View Zoom");
    presentation.setIcon(AndroidIcons.NeleIcons.Size);
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    String label = String.format("%d%%", (int)(100 * mySurface.getScale()));
    presentation.setText(label);
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup("Zoom", true);

    for (ZoomType type : ZoomType.values()) {
      if (!type.showInMenu()) {
        continue;
      }
      group.add(new SetZoomAction(mySurface, type));
    }

    return group;
  }

  private static class SetZoomAction extends AnAction {
    @NotNull private final DesignSurface mySurface;
    @NotNull private final ZoomType myType;

    public SetZoomAction(@NotNull DesignSurface surface, @NotNull ZoomType type) {
      super(type.getLabel());
      myType = type;
      mySurface = surface;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySurface.zoom(myType);
    }
  }
}