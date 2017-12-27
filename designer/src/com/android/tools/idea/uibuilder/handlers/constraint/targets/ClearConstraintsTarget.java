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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.graphics.NlIcon;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.ActionTarget;
import icons.AndroidIcons;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.SHERPA_URI;

public class ClearConstraintsTarget extends ActionTarget implements ActionTarget.Action {
  private static final NlIcon CLEAR_ICON =
    new NlIcon(StudioIcons.LayoutEditor.Toolbar.CLEAR_CONSTRAINTS, AndroidIcons.SherpaIcons.DeleteConstraintB);

  public ClearConstraintsTarget(ActionTarget previous) {
    super(previous, CLEAR_ICON, null);
    setAction(this);
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    NlComponent component = myComponent.getNlComponent();
    myIsVisible = false;
    for (int i = 0; i < ConstraintComponentUtilities.ourConstraintLayoutAttributesToClear.length; i++) {
      String attr = ConstraintComponentUtilities.ourConstraintLayoutAttributesToClear[i];
      String val = component.getAttribute(SHERPA_URI, attr);
      if (val != null) {
        myIsVisible = true;
      }
    }
    super.layout(sceneTransform, l, t, r, b);
    return false;
  }

  @Override
  public void apply(SceneComponent component) {
    ConstraintComponentUtilities.clearAttributes(component.getAuthoritativeNlComponent());
  }

  @Override
  public String getToolTipText() {
    return "Delete Constraints";
  }
}
