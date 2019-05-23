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
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Implements an action to cycle chains
 */
public class ChainCycleViewAction extends DirectViewAction {
  private static final String CHAIN_LABEL = "Cycle Chain mode";

  public ChainCycleViewAction() {
    super(StudioIcons.LayoutEditor.Toolbar.CYCLE_CHAIN_SPREAD_INLINE, CHAIN_LABEL);
  }

  @Override
  public void perform(@NotNull ViewEditor editor,
                      @NotNull ViewHandler handler,
                      @NotNull NlComponent component,
                      @NotNull List<NlComponent> selectedChildren,
                      int modifiers) {
    if (selectedChildren.isEmpty()) {
      return;
    }
    NlComponent primaryNlComponent = selectedChildren.get(0);
    SceneComponent primary = editor.getScene().getSceneComponent(primaryNlComponent);

    List<SceneComponent> nonPrimaryComponents = selectedChildren.stream()
      .filter(it -> it != primaryNlComponent)
      .map(it -> editor.getScene().getSceneComponent(it))
      .collect(Collectors.toList());

    if (primary == null) {
      return;
    }

    ChainChecker checker = new ChainChecker();
    if (checker.checkIsInChain(primary)) {
      if (checker.isInHorizontalChain()) {
        SceneComponent horizontalChainHead = checker.getHorizontalChainHead();
        if (isInSameHorizontalChain(horizontalChainHead, nonPrimaryComponents)) {
          ConstraintComponentUtilities.cycleChainStyle(horizontalChainHead, SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE, primary);
        }
      }
      if (checker.isInVerticalChain()) {
        SceneComponent verticalChainHead = checker.getVerticalChainHead();
        if (isInSameVerticalChain(verticalChainHead, nonPrimaryComponents)) {
          ConstraintComponentUtilities.cycleChainStyle(verticalChainHead, SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE, primary);
        }
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

    boolean isVisible = true;
    if (selectedChildren.isEmpty()) {
      isVisible = false;
    }
    if (!isApplicable(editor, selectedChildren)) {
      isVisible = false;
    }
    presentation.setVisible(isVisible);
  }

  private static boolean isApplicable(@NotNull ViewEditor editor, @NotNull List<NlComponent> selectedChildren) {
    if (selectedChildren.isEmpty()) {
      return false;
    }
    NlComponent primaryNlComponent = selectedChildren.get(0);
    SceneComponent primary = editor.getScene().getSceneComponent(primaryNlComponent);
    if (primary == null) {
      return false;
    }

    List<SceneComponent> nonPrimaryComponents = selectedChildren.stream()
      .filter(it -> it != primaryNlComponent)
      .map(it -> editor.getScene().getSceneComponent(it))
      .collect(Collectors.toList());

    ChainChecker checker = new ChainChecker();
    if (checker.checkIsInChain(primary)) {
      if (checker.isInHorizontalChain()) {
        SceneComponent horizontalChainHead = checker.getHorizontalChainHead();
        if (isInSameHorizontalChain(horizontalChainHead, nonPrimaryComponents)) {
          return true;
        }
      }
      if (checker.isInVerticalChain()) {
        SceneComponent verticalChainHead = checker.getVerticalChainHead();
        if (isInSameVerticalChain(verticalChainHead, nonPrimaryComponents)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isInSameVerticalChain(@NotNull SceneComponent head, @NotNull List<SceneComponent> components) {
    for (SceneComponent component: components) {
      if (component == null) {
        return false;
      }
      ChainChecker checker = new ChainChecker();
      checker.checkIsInChain(component);
      if (!checker.isInVerticalChain() || checker.getVerticalChainHead() != head) {
        return false;
      }
    }
    return true;
  }

  private static boolean isInSameHorizontalChain(@NotNull SceneComponent head, @NotNull List<SceneComponent> components) {
    for (SceneComponent component: components) {
      if (component == null) {
        return false;
      }
      ChainChecker checker = new ChainChecker();
      checker.checkIsInChain(component);
      if (!checker.isInHorizontalChain() || checker.getHorizontalChainHead() != head) {
        return false;
      }
    }
    return true;
  }
}
