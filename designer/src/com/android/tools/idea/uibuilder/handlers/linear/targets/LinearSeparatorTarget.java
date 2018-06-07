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

import com.android.tools.idea.uibuilder.handlers.linear.draw.DrawLinearPlaceholder;
import com.android.tools.idea.uibuilder.handlers.linear.draw.DrawLinearSeparator;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.uibuilder.scene.target.Notch;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static java.lang.Math.max;
import static java.lang.Math.min;

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
  @Nullable private SceneComponent myParent;
  /**
   * True if the placeholder won't be hidden outside the parent
   */
  private boolean myCanDisplayPlaceholderAfter;

  /**
   * Create a new separator for linear layout
   *
   * @param layoutVertical is the orientation of the parent LinearLayout
   * @param atEnd          if true, a separator will be drawn at the end of the component
   */
  public LinearSeparatorTarget(boolean layoutVertical, boolean atEnd) {
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
    myParent = myComponent.getParent();
    assert myParent != null : "This target cannot be added to a root component";
    NlComponent nlComponent = myComponent.getNlComponent();
    if (myLayoutVertical) {
      int parentMin = myParent.getDrawY() + DrawLinearSeparator.STROKE_SIZE / 2;
      int parentMax = parentMin + myParent.getDrawHeight() - DrawLinearSeparator.STROKE_SIZE;
      myLeft = myParent.getDrawX();
      myRight = myLeft + myParent.getDrawWidth();
      float y =
        context.pxToDp(NlComponentHelperKt.getY(nlComponent)) + (myAtEnd ? context.pxToDp(NlComponentHelperKt.getH(nlComponent)) : 0);
      myTop = myBottom = max(parentMin, min(parentMax, y));
      myCanDisplayPlaceholderAfter = myComponent.getDrawY() + myComponent.getDrawHeight() < parentMax;
    }
    else {
      int parentMin = myParent.getDrawX() + DrawLinearSeparator.STROKE_SIZE / 2;
      int parentMax = parentMin + myParent.getDrawWidth() - DrawLinearSeparator.STROKE_SIZE;
      float x =
        context.pxToDp(NlComponentHelperKt.getX(nlComponent)) + (myAtEnd ? context.pxToDp(NlComponentHelperKt.getW(nlComponent)) : 0);
      myLeft = myRight = max(parentMin, min(parentMax, x));
      myTop = myParent.getDrawY();
      myBottom = myTop + myParent.getDrawHeight();
      myCanDisplayPlaceholderAfter = myComponent.getDrawX() + myComponent.getDrawWidth() < parentMax;
    }

    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myComponent.isDragging() || myParent == null) {
      return;
    }


    if (myIsHighlight) {
      DrawLinearPlaceholder.add(list, sceneContext,
                                myLayoutVertical,
                                myAtEnd && myCanDisplayPlaceholderAfter,
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
                   transform.getSwingXDip(myLeft), transform.getSwingYDip(myTop),
                   transform.getSwingXDip(myRight) + 1, transform.getSwingYDip(myBottom) + 1);
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

  /**
   * Returns true if the separator is the last one among its siblings.
   * <p>
   * This is useful to know if a component should be inserted before
   * or after the owner of this target
   */
  public boolean isAtEnd() {
    return myAtEnd;
  }

  @Override
  public void fill(@NotNull SceneComponent owner,
                   @NotNull SceneComponent snappableComponent,
                   @NotNull ArrayList<Notch> horizontalNotches,
                   @NotNull ArrayList<Notch> verticalNotches) {

    if (myLayoutVertical) {
      int value = owner.getDrawY();
      int displayValue = owner.getDrawY();
      if (myAtEnd) {
        if (myCanDisplayPlaceholderAfter) {
          value += owner.getDrawHeight();
          displayValue += owner.getDrawHeight();
        }
        else {
          value += owner.getDrawHeight() - snappableComponent.getDrawHeight() / 2;
          displayValue += owner.getDrawHeight() - snappableComponent.getDrawHeight() / 2;
        }
      }
      else {
        value -= snappableComponent.getDrawHeight() / 2f + 0.5f;
      }
      Notch.Vertical notch = new Notch.Vertical(owner, value, displayValue);
      notch.setGap(owner.getDrawHeight() / 2);
      notch.setTarget(this);
      verticalNotches.add(notch);
    }
    else {
      int value = owner.getDrawX();
      int displayValue = owner.getDrawX();
      if (myAtEnd) {
        if (myCanDisplayPlaceholderAfter) {
          value += owner.getDrawWidth();
          displayValue += owner.getDrawWidth();
        }
        else {
          value += owner.getDrawWidth() - snappableComponent.getDrawWidth() / 2;
          displayValue += owner.getDrawWidth() - snappableComponent.getDrawWidth() / 2;
        }
      }
      else {
        value -= snappableComponent.getDrawWidth() / 2f + 0.5f;
      }
      Notch.Horizontal notch = new Notch.Horizontal(owner, value, displayValue);
      notch.setGap(owner.getDrawWidth() / 2);
      notch.setTarget(this);
      horizontalNotches.add(notch);
    }
  }

  /**
   * Get the insertion index corresponding to this separator target or -1 if
   * the insertion can't be found or the component should be inserted at the end
   */
  public int getInsertionIndex() {
    NlComponent before = !isAtEnd() ? myComponent.getNlComponent() : null;
    NlComponent parent = before != null ? before.getParent() : null;
    if (parent != null) {
      return parent.getChildren().indexOf(before);
    }
    return -1;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + " highlighted: " + myIsHighlight + " vertical: " + myLayoutVertical;
  }
}
