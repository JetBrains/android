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

import com.android.SdkConstants;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.ActionTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.graphics.NlIcon;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implements an action to cycle chains
 */
public class ChainCycleTarget extends ActionTarget {
  // TODO: add new icon to StudioIcons and replace this icon
  private static final NlIcon CHAIN_ICON = new NlIcon(AndroidIcons.SherpaIcons.Chain, AndroidIcons.SherpaIcons.ChainBlue);

  private ChainChecker myChainChecker = new ChainChecker();

  public ChainCycleTarget(ActionTarget previous, Action action) {
    super(previous, CHAIN_ICON, action);
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    super.layout(sceneTransform, l, t, r, b);
    myChainChecker.checkIsInChain(myComponent);
    myIsVisible = myChainChecker.isInHorizontalChain() || myChainChecker.isInVerticalChain();
    return false;
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    if (myIsVisible && closestTargets.contains(this)) {
      if (myChainChecker.isInHorizontalChain()) {
        ConstraintComponentUtilities.cycleChainStyle(myChainChecker.getHorizontalChainHead(),
                                                     SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE, myComponent);
      }
      else if (myChainChecker.isInVerticalChain()) {
        ConstraintComponentUtilities.cycleChainStyle(myChainChecker.getVerticalChainHead(),
                                                     SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE, myComponent);
      }
    }
  }

  @Override
  public String getToolTipText() {
     return "Cycle Chain mode";
  }
}
