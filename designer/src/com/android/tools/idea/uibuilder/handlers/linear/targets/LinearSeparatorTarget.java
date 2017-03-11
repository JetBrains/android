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
package com.android.tools.idea.uibuilder.handlers.linear.targets;

import com.android.tools.idea.uibuilder.handlers.linear.draw.DrawLinearSeparator;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.ScenePicker;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.target.BaseTarget;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

/**
 * Displays a separator in between LinearLayout's children and used as a target when dropping
 * a component in LinearLayout
 */
public class LinearSeparatorTarget extends BaseTarget {

  private static final boolean DEBUG = false;
  private final boolean myVertical;
  private final boolean myAtEnd;
  private boolean myIsHighlight;

  /**
   * Create a new separator for linear layout
   *
   * @param isVertical is the orientation of the parent LinearLayout
   * @param atEnd      if true, a separator will be drawn at both ends of the component
   */
  public LinearSeparatorTarget(boolean isVertical, boolean atEnd) {
    super();
    myVertical = isVertical;
    myAtEnd = atEnd;
  }

  @Override
  public int getPreferenceLevel() {
    return GUIDELINE_ANCHOR_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext context, int l, int t, int r, int b) {
    SceneComponent parent = myComponent.getParent();
    assert parent != null : "This target cannot be added to a root component";
    NlComponent nlComponent = myComponent.getNlComponent();
    if (myVertical) {
      myLeft = parent.getDrawX();
      myRight = myLeft + parent.getDrawWidth();
      myTop = myBottom = context.pxToDp(nlComponent.y) + (myAtEnd ? context.pxToDp(nlComponent.h) : 0);
    }
    else {
      myLeft = myRight = context.pxToDp(nlComponent.x) + (myAtEnd ? context.pxToDp(nlComponent.w) : 0 );
      myTop = parent.getDrawY();
      myBottom = myTop + parent.getDrawHeight();
    }
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myComponent.isDragging()) {
      return;
    }
    DrawLinearSeparator.add(list, sceneContext, !myVertical,
                            myIsHighlight ? DrawLinearSeparator.STATE_HIGHLIGHT : DrawLinearSeparator.STATE_DEFAULT,
                            myLeft, myTop, myRight, myBottom);
    if (DEBUG) {
      drawDebug(list, sceneContext);
    }
  }

  @Override
  public void addHit(@NotNull SceneContext transform, @NotNull ScenePicker picker) {
    picker.addRect(this, 10,
                   transform.getSwingX(myLeft), transform.getSwingY(myTop),
                   transform.getSwingX(myRight) + 1, transform.getSwingY(myBottom) + 1);
  }

  /**
   * Draw the debug graphics
   */
  private void drawDebug(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, myIsHighlight ? JBColor.GREEN : JBColor.RED);
    list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, myIsHighlight ? JBColor.GREEN : JBColor.RED);
  }

  /**
   * Visually highlight the target
   *
   * @param isHighlight true to highlight
   */
  public void setHighlight(boolean isHighlight) {
    myIsHighlight = isHighlight;
  }

  public boolean isAtEnd() {
    return myAtEnd;
  }
}
