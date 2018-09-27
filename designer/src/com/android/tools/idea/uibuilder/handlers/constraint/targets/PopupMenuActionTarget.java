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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.ActionTarget;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.graphics.NlIcon;
import com.google.common.collect.ImmutableList;
import icons.StudioIcons;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

public class PopupMenuActionTarget extends ActionTarget {
  static final NlIcon OVERFLOW_ICON =
    new NlIcon(StudioIcons.Common.OVERFLOW, StudioIcons.Common.OVERFLOW);

  public PopupMenuActionTarget() {
    super(OVERFLOW_ICON, (SceneComponent c) -> c.getAuthoritativeNlComponent());
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    SceneComponent c = this.getComponent();
    JComponent jComponent = c.getScene().getDesignSurface();
    SceneView sceneView = getComponent().getScene().getDesignSurface().getSceneView(x,y);

    int sx =  Coordinates.getSwingX(sceneView,x);
    int sy =  Coordinates.getSwingY(sceneView,y);
    MouseEvent mouseEvent = new MouseEvent(jComponent,MouseEvent.BUTTON2,System.currentTimeMillis(),0, sx, sy,0,true);
    c.getScene().getDesignSurface().getActionManager().showPopup(mouseEvent);

  }
}
