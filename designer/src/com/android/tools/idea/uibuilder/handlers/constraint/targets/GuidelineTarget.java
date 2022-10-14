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

import static com.android.tools.idea.res.IdeResourcesUtil.resolveStringValue;

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlAttributesHolder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.actions.ToggleAutoConnectAction;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawHorizontalGuideline;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawVerticalGuideline;
import com.android.tools.idea.uibuilder.scene.target.TargetSnapper;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Implements the drag behaviour for ConstraintLayout Guideline
 */
public class GuidelineTarget extends BaseTarget {

  @AndroidDpCoordinate protected int myOffsetX;
  @AndroidDpCoordinate protected int myOffsetY;
  @AndroidDpCoordinate protected int myFirstMouseX;
  @AndroidDpCoordinate protected int myFirstMouseY;
  protected boolean myChangedComponent;
  @NotNull private final TargetSnapper myTargetSnapper = new TargetSnapper();

  protected final boolean myIsHorizontal;
  private int myBegin = 20;
  private int myEnd = -1;
  private float myPercent = -1;

  private GuidelineDropHandler myDropHandler;

  public GuidelineTarget(boolean isHorizontal) {
    myIsHorizontal = isHorizontal;
  }

  @Override
  public void setComponent(@NotNull SceneComponent component) {
    super.setComponent(component);
    myDropHandler = new GuidelineDropHandler(component, myIsHorizontal);
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    if (myComponent.getParent() == null) {
      return;
    }
    myFirstMouseX = x;
    myFirstMouseY = y;
    myOffsetX = x - myComponent.getDrawX(System.currentTimeMillis());
    myOffsetY = y - myComponent.getDrawY(System.currentTimeMillis());
    myChangedComponent = false;
    myTargetSnapper.reset();
    myTargetSnapper.gatherNotches(myComponent);
  }

  @Override
  public void mouseDrag(@AndroidDpCoordinate int x,
                        @AndroidDpCoordinate int y,
                        @NotNull List<Target> closestTargets,
                        @NotNull SceneContext ignored) {
    if (myComponent.getParent() == null) {
      return;
    }
    myComponent.setDragging(true);
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    x -= myOffsetX;
    y -= myOffsetY;
    int snappedX = myTargetSnapper.trySnapHorizontal(x).orElse(x);
    int snappedY = myTargetSnapper.trySnapVertical(y).orElse(y);
    ComponentModification modification = new ComponentModification(component, "Drag");
    updateAttributes(modification, snappedX, snappedY);
    modification.apply();
    component.fireLiveChangeEvent();
    myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
    myChangedComponent = true;
  }

  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    if (!myComponent.isDragging()) {
      return;
    }
    myComponent.setDragging(false);
    if (myComponent.getParent() != null) {
      boolean commitChanges = true;
      if (Math.abs(x - myFirstMouseX) <= 1 && Math.abs(y - myFirstMouseY) <= 1) {
        commitChanges = false;
      }
      NlComponent component = myComponent.getAuthoritativeNlComponent();
      ComponentModification modification = new ComponentModification(component, "Drag");
      x -= myOffsetX;
      y -= myOffsetY;
      int snappedX = myTargetSnapper.trySnapHorizontal(x).orElse(x);
      int snappedY = myTargetSnapper.trySnapVertical(y).orElse(y);
      if (isAutoConnectionEnabled()) {
        myTargetSnapper.applyNotches(modification);
      }
      updateAttributes(modification, snappedX, snappedY);
      modification.apply();

      if (commitChanges) {
        modification.commit();
      }
    }
    if (myChangedComponent) {
      myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
    }
  }

  /**
   * Reset the status when the dragging is canceled.
   */
  @Override
  public void mouseCancel() {
    int originalX = myFirstMouseX - myOffsetX;
    int originalY = myFirstMouseY - myOffsetY;
    myComponent.setPosition(originalX, originalY);

    // rollback the transaction. The value may be temporarily changed by live rendering.
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.rollback();
    component.fireLiveChangeEvent();

    myComponent.setDragging(false);
    myTargetSnapper.reset();
    myChangedComponent = false;
    myComponent.getScene().markNeedsLayout(Scene.IMMEDIATE_LAYOUT);
  }

  private boolean isAutoConnectionEnabled() {
    return !AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE.isEqualsIgnoreCase(myComponent.getNlComponent().getTagName()) &&
           ToggleAutoConnectAction.isAutoconnectOn();
  }

  @Override
  public int getPreferenceLevel() {
    return Target.GUIDELINE_LEVEL;
  }

  @Override
  protected boolean isHittable() {
    if (myComponent.isSelected()) {
      return myComponent.canShowBaseline() || !myComponent.isDragging();
    }
    return true;
  }

  @Override
  public Cursor getMouseCursor(@JdkConstants.InputEventMask int modifiersEx) {
    if (myIsHorizontal) {
      return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
    }
    return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (myIsHorizontal) {
      int y = (int)(myTop + (myBottom - myTop) / 2);
      SceneComponent parent = myComponent.getParent();
      DrawHorizontalGuideline
        .add(list, sceneContext, myLeft, y, myRight, parent.getDrawX(), parent.getDrawY(), parent.getDrawHeight(), myBegin, myEnd, myPercent, myComponent.isSelected());
    }
    else {
      int x = (int)(myLeft + (myRight - myLeft) / 2);
      SceneComponent parent = myComponent.getParent();
      DrawVerticalGuideline
        .add(list, sceneContext, x, myTop, myBottom, parent.getDrawX(), parent.getDrawY(), parent.getDrawWidth(), myBegin, myEnd,
             myPercent, myComponent.isSelected());
    }
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    int dist = 6;
    SceneComponent parent = myComponent.getParent();
    if (parent != null) {
      if (myIsHorizontal) {
        myLeft = parent.getDrawX();
        myRight = parent.getDrawX() + parent.getDrawWidth();
        myTop = t - dist;
        myBottom = t + dist;
      }
      else {
        myLeft = l - dist;
        myRight = l + dist;
        myTop = parent.getDrawY();
        myBottom = parent.getDrawY() + parent.getDrawHeight();
      }
    }
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    String begin = component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);
    String percent = component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT);
    if (begin != null) {
      myBegin = ConstraintComponentUtilities.getDpValue(component, begin);
      myEnd = -1;
      myPercent = -1;
    }
    else if (end != null) {
      myBegin = -1;
      myEnd = ConstraintComponentUtilities.getDpValue(component, end);
      myPercent = -1;
    }
    else if (percent != null) {
      myBegin = -1;
      myEnd = -1;
      Configuration configuration = component.getModel().getConfiguration();
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      percent = resolveStringValue(resourceResolver, percent);
      try {
        myPercent = Float.parseFloat(percent);
      } catch (NumberFormatException e) {
        myPercent = 0;
      }
    }
    return false;
  }

  protected void updateAttributes(@NotNull NlAttributesHolder attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myDropHandler.updateAttributes(attributes, x, y);
  }

  @Override
  public String getToolTipText() {
    String str = "Guideline (";
    if (myBegin != -1) {
      str += "< "+myBegin+")";
    }
    else if (myEnd != -1) {
      str += myEnd+" >)";
    }
    else {
      float percentValue = myPercent;
      if (percentValue > 1) {
        percentValue = 1;
      }
      if (percentValue < 0) {
        percentValue = 0;
      }
      percentValue = 100*Math.round(percentValue * 100) / 100f;
      if (!Float.isNaN(percentValue)) {
        str += String.valueOf(percentValue)+"%)";
      }
      else {
        str += "50";
      }
    }
    return str;
  }

  @Nullable
  @Override
  public List<SceneComponent> newSelection() {
    return ImmutableList.of(getComponent());
  }

  public static class GuidelineDropHandler {

    @NotNull private SceneComponent myComponent;
    private boolean myHorizontal;

    public GuidelineDropHandler(@NotNull SceneComponent component, boolean horizontal) {
      myComponent = component;
      myHorizontal = horizontal;
    }

    public void updateAttributes(@NotNull NlAttributesHolder attributes, @AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
      String begin = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
      String end = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);
      String percent = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT);
      SceneComponent parent = myComponent.getParent();
      int value = y - parent.getDrawY();
      float dimension = parent.getDrawHeight();
      if (!myHorizontal) {
        value = x - parent.getDrawX();
        dimension = parent.getDrawWidth();
      }
      if (begin != null || (end == null && percent == null)) {
        String position = String.format(SdkConstants.VALUE_N_DP, value);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, position);
      }
      else if (end != null) {
        String position = String.format(SdkConstants.VALUE_N_DP, (int)dimension - value);
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, position);
      }
      else {
        String percentStringValue;
        float percentValue = value / dimension;
        if (percentValue > 1) {
          percentValue = 1;
        }
        if (percentValue < 0) {
          percentValue = 0;
        }
        percentValue = Math.round(percentValue * 100) / 100f;
        percentStringValue = String.valueOf(percentValue);
        if (percentStringValue.equalsIgnoreCase("NaN")) {
          percentStringValue = "0.5";
        }
        attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, percentStringValue);
      }
      ConstraintComponentUtilities.cleanup(attributes, myComponent.getNlComponent());
    }
  }
}
