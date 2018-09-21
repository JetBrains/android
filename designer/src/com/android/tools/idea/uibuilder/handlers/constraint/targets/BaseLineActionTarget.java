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

import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.EDIT_BASELINE_ACTION_TOOLTIP;

import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.ActionTarget;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.graphics.NlIcon;
import com.google.common.collect.ImmutableList;
import icons.StudioIcons;

public class BaseLineActionTarget extends ActionTarget {
  private static final NlIcon BASELINE_ICON =
    new NlIcon(StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED, StudioIcons.LayoutEditor.Toolbar.BASELINE_ALIGNED_CONSTRAINT);

  public BaseLineActionTarget() {
    super(BASELINE_ICON, (SceneComponent c) -> c.setShowBaseline(!c.canShowBaseline()));
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    SceneComponent c = this.getComponent();
    boolean baseline = !myComponent.canShowBaseline();
    myComponent.setShowBaseline(baseline);
    if (baseline) {
      ImmutableList<Target> targets = myComponent.getTargets();
      for (Target t : targets) {
        if (t instanceof AnchorTarget) {
          AnchorTarget at = (AnchorTarget)t;
          if (at.getType() == AnchorTarget.Type.BASELINE) {
            myComponent.getScene().setHitTarget(at);
            at.mouseDown((int)at.getCenterX(), (int)at.getCenterY());
            at.mouseDrag(x, y, myComponent.getTargets());
          }
        }
      }
    }
    myComponent.getScene().needsRebuildList();
    myComponent.getScene().repaint();
  }
  @Override
  public String getToolTipText() {
    return   EDIT_BASELINE_ACTION_TOOLTIP;
  }

}
