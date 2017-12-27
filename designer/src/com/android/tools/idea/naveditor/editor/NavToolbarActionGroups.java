/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.common.actions.SetZoomAction;
import com.android.tools.idea.common.actions.ZoomLabelAction;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.ZoomType;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

/**
 * Toolbar actions for the navigation editor
 */
public class NavToolbarActionGroups extends ToolbarActionGroups {
  public NavToolbarActionGroups(@NotNull DesignSurface surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new SetZoomAction(mySurface, ZoomType.OUT));
    group.add(new ZoomLabelAction(mySurface));
    group.add(new SetZoomAction(mySurface, ZoomType.IN));
    group.add(new ZoomToFitAction(mySurface));
    // TODO group.add(new TogglePanningDialogAction(mySurface));
    return group;
  }

  static class ZoomToFitAction extends SetZoomAction {
    public ZoomToFitAction(@NotNull DesignSurface surface) {
      super(surface, ZoomType.FIT);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      super.actionPerformed(e);
      //noinspection ConstantConditions  In practice we'll always have a view at this point
      mySurface.getCurrentSceneView().setLocation(0, 0);
      //noinspection ConstantConditions  And we'll also always have a scene with a root.
      mySurface.scrollRectToVisible(Coordinates.getSwingRectDip(mySurface.getCurrentSceneView(),
                                                                mySurface.getScene().getRoot().fillRect(null)));
    }
  }
}
