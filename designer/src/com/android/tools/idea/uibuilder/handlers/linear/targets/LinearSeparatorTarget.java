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

import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.linear.draw.DrawLinearPlaceholder;
import com.android.tools.idea.uibuilder.handlers.linear.draw.DrawLinearSeparator;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.ScenePicker;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawComponentBackground;
import com.android.tools.idea.uibuilder.scene.target.BaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Notch;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * Displays a separator in between LinearLayout's children and used as a target when dropping
 * a component in LinearLayout
 */
public class LinearSeparatorTarget extends BaseTarget implements Notch.Provider {

  private static final boolean DEBUG = false;
  private final boolean myLayoutVertical;
  private final boolean myAtEnd;
  private boolean myIsHighlight;
  private int myHighLightSize;

  /**
   * Create a new separator for linear layout
   *
   * @param layoutVertical is the orientation of the parent LinearLayout
   * @param atEnd          if true, a separator will be drawn at both ends of the component
   */
  public LinearSeparatorTarget(boolean layoutVertical, boolean atEnd) {
    super();
    myLayoutVertical = layoutVertical;
    myAtEnd = atEnd;
  }

  @Override
  public int getPreferenceLevel() {
    return GUIDELINE_ANCHOR_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext context,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    SceneComponent parent = myComponent.getParent();
    assert parent != null : "This target cannot be added to a root component";
    NlComponent nlComponent = myComponent.getNlComponent();
    if (myLayoutVertical) {
      myLeft = parent.getDrawX();
      myRight = myLeft + parent.getDrawWidth();
      myTop = myBottom = context.pxToDp(nlComponent.y) + (myAtEnd ? context.pxToDp(nlComponent.h) : 0);
    }
    else {
      myLeft = myRight = context.pxToDp(nlComponent.x) + (myAtEnd ? context.pxToDp(nlComponent.w) : 0);
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

    if (myIsHighlight) {
      DrawLinearPlaceholder.add(list, sceneContext,
                                myLayoutVertical,
                                myAtEnd,
                                myHighLightSize,
                                myLeft, myTop, myRight, myBottom);
    }
    else {
      DrawLinearSeparator.add(list, sceneContext, myLayoutVertical, // draw the separator orthogonally to the layout
                              myLeft, myTop, myRight, myBottom);
    }
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
    if (myLayoutVertical) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom + myHighLightSize, JBColor.MAGENTA);
    }
    else {
      list.addRect(sceneContext, myLeft, myTop, myRight + myHighLightSize, myBottom, JBColor.MAGENTA);
    }
  }

  /**
   * Visually highlight the target
   *
   * @param isHighlight true to highlight
   */
  public void setHighlight(boolean isHighlight) {
    setHighlight(isHighlight, 5, 5);
  }

  public void setHighlight(boolean isHighlight, int width, int height) {
    myIsHighlight = isHighlight;
    myHighLightSize = myLayoutVertical ? height : width;
  }

  public boolean isAtEnd() {
    return myAtEnd;
  }

  @Override
  public void fill(@NotNull SceneComponent owner,
                   @NotNull SceneComponent snappableComponent,
                   @NotNull ArrayList<Notch> horizontalNotches,
                   @NotNull ArrayList<Notch> verticalNotches) {

    Notch.Action action = attributes -> LinearLayoutHandler.insertComponentAtTarget(snappableComponent, this, false);
    if (myLayoutVertical) {
      int value = owner.getDrawY();
      int displayValue = owner.getDrawY();
      if (myAtEnd) {
        value += owner.getDrawHeight();
        displayValue += owner.getDrawHeight();
      }
      else {
        value -= snappableComponent.getDrawHeight() / 2f + 0.5f;
      }
      Notch.Vertical notch = new Notch.Vertical(owner, value, displayValue, action);
      if(myAtEnd) {
        notch.setGap(owner.getDrawHeight() / 2);
      }
      notch.setTarget(this);
      verticalNotches.add(notch);
    }
    else {
      int value = owner.getDrawX();
      int displayValue = owner.getDrawX();
      if (myAtEnd) {
        value += owner.getDrawWidth();
        displayValue += owner.getDrawWidth();
      }
      else {
        value -= snappableComponent.getDrawWidth() / 2f + 0.5f;
      }
      Notch.Horizontal notch = new Notch.Horizontal(owner, value, displayValue, action);
      if(myAtEnd) {
        notch.setGap(owner.getDrawWidth() / 2);
      }
      notch.setTarget(this);
      horizontalNotches.add(notch);
    }
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + " highlighted: " + myIsHighlight + " vertical: " + myLayoutVertical;
  }
}
