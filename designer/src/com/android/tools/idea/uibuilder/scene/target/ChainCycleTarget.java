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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DrawAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Implements an action to cycle chains
 */
public class ChainCycleTarget extends ActionTarget {

  public ChainCycleTarget(ActionTarget previous, Action action) {
    super(previous, action);
    setActionType(DrawAction.CHAIN);
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {
    super.layout(sceneTransform, l, t, r, b);
    checkIsInChain();
    myIsVisible = myIsInHorizontalChain || myIsInVerticalChain;
    return false;
  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    if (closestTarget == this && myIsVisible) {
      if (myIsInHorizontalChain) {
        cycleChainStyle(myHorizontalChainHead, SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE);
      }
      else if (myIsInVerticalChain) {
        cycleChainStyle(myVerticalChainHead, SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE);
      }
    }
  }
}
