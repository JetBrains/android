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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import icons.StudioIcons;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Implements an action to cycle chains
 */
public class ChainCycleViewAction extends DirectViewAction {
  private static final String CHAIN_LABEL = "Cycle Chain mode";

  private ChainChecker myChainChecker = new ChainChecker();

  public ChainCycleViewAction() {
    super(StudioIcons.LayoutEditor.Toolbar.CYCLE_CHAIN_SPREAD_INLINE, CHAIN_LABEL);
  }

  @Override
  public void perform(@NotNull ViewEditor editor,
                      @NotNull ViewHandler handler,
                      @NotNull NlComponent component,
                      @NotNull List<NlComponent> selectedChildren,
                      int modifiers) {
    if (selectedChildren.size() != 1) {
      // The action can only operate in one element that is part of the chain at a time
      return;
    }

    SceneComponent selectedComponent = editor.getScene().getSceneComponent(selectedChildren.get(0));
    if (selectedComponent != null) {
      myChainChecker.checkIsInChain(selectedComponent);
      if (myChainChecker.isInHorizontalChain()) {
        ConstraintComponentUtilities.cycleChainStyle(myChainChecker.getHorizontalChainHead(),
                                                     SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE, selectedComponent);
      }
      else if (myChainChecker.isInVerticalChain()) {
        ConstraintComponentUtilities.cycleChainStyle(myChainChecker.getVerticalChainHead(),
                                                     SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE, selectedComponent);
      }
    }
  }

  @Override
  public void updatePresentation(@NotNull ViewActionPresentation presentation,
                                 @NotNull ViewEditor editor,
                                 @NotNull ViewHandler handler,
                                 @NotNull NlComponent component,
                                 @NotNull List<NlComponent> selectedChildren,
                                 int modifiers) {
    super.updatePresentation(presentation, editor, handler, component, selectedChildren, modifiers);

    boolean isVisible = false;
    if (selectedChildren.size() == 1) {
      SceneComponent selectedComponent = editor.getScene().getSceneComponent(selectedChildren.get(0));
      if (selectedComponent != null) {
        myChainChecker.checkIsInChain(selectedComponent);
        isVisible = myChainChecker.isInHorizontalChain() || myChainChecker.isInVerticalChain();
      }
    }

    presentation.setVisible(isVisible);
  }
}
