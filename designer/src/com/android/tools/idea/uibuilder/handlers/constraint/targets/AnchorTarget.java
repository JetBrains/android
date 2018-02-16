/*
 * Copyright (C) 2017 - 2018 The Android Open Source Project
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
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.BaseTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scout.Scout;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Implements a target anchor for the ConstraintLayout.
 */
public class AnchorTarget extends BaseTarget {

  private static final boolean DEBUG_RENDERER = false;

  private static final int EXPANDED_SIZE = 200;
  private static final int ANCHOR_SIZE = 3;

  private final boolean myVisibility;
  private final AnchorTarget.Type myType;

  private AnchorTarget myCurrentClosestTarget; // used to define the closest target during drag;
  private boolean myInDrag = false;
  private boolean myRenderingTemporaryConnection = false;
  private boolean myExpandArea = false;
  @AndroidDpCoordinate private int myLastX = -1;
  @AndroidDpCoordinate private int myLastY = -1;
  @AndroidDpCoordinate private int myConnectedX = -1;
  @AndroidDpCoordinate private int myConnectedY = -1;

  private final HashMap<String, String> mPreviousAttributes = new HashMap<>();

  /**
   * Type of possible anchors.
   */
  public enum Type {
    LEFT, TOP, RIGHT, BOTTOM, BASELINE;
    public int getMask() {
      switch (this){
        case LEFT:
          return DecoratorUtilities.MASK_LEFT;
        case TOP:
          return DecoratorUtilities.MASK_TOP;
        case RIGHT:
          return DecoratorUtilities.MASK_RIGHT;
        case BOTTOM:
          return DecoratorUtilities.MASK_BOTTOM;
        case BASELINE:
          return DecoratorUtilities.MASK_BASELINE;
      }
      return 0;
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public AnchorTarget(@NotNull AnchorTarget.Type type, boolean visible) {
    myType = type;
    myVisibility = visible;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean canChangeSelection() {
    return false;
  }

  public Type getType() {
    return myType;
  }

  @Override
  public void setExpandSize(boolean expand) {
    myExpandArea = expand;
  }

  public boolean isHorizontalAnchor() {
    return myType == Type.LEFT || myType == Type.RIGHT;
  }

  public boolean isVerticalAnchor() {
    return myType == Type.TOP || myType == Type.BOTTOM;
  }

  @Override
  public void setMouseHovered(boolean over) {
    if (over != mIsOver) {
      changeMouseOverState(over);
      myComponent.getScene().needsRebuildList();
      myComponent.getScene().repaint();
    }
  }

  private void changeMouseOverState(boolean newValue) {
    mIsOver = newValue;
    String dir;
    switch (myType) {
      case LEFT:
        dir = DecoratorUtilities.LEFT_CONNECTION;
        break;
      case TOP:
        dir = DecoratorUtilities.TOP_CONNECTION;
        break;
      case RIGHT:
        dir = DecoratorUtilities.RIGHT_CONNECTION;
        break;
      case BOTTOM:
        dir = DecoratorUtilities.BOTTOM_CONNECTION;
        break;
      default:
        dir = DecoratorUtilities.BASELINE_CONNECTION;
        break;
    }
    DecoratorUtilities.ViewStates mode = DecoratorUtilities.ViewStates.SELECTED;
    if (isTargeted()) {
      mode = DecoratorUtilities.ViewStates.WILL_DESTROY;
    }
    DecoratorUtilities.setTimeChange(myComponent.getNlComponent(), dir, mode);
  }

  /**
   * Returns true if this anchor is currently the destination target in a new constraint creation.
   */
  private boolean isTargeted() {
    return mIsOver && !myComponent.isSelected();
  }

  @Override
  public void onComponentSelectionChanged(boolean selection) {
    String dir;
    switch (myType) {
      case LEFT:
        dir = DecoratorUtilities.LEFT_CONNECTION;
        break;
      case TOP:
        dir = DecoratorUtilities.TOP_CONNECTION;
        break;
      case RIGHT:
        dir = DecoratorUtilities.RIGHT_CONNECTION;
        break;
      case BOTTOM:
        dir = DecoratorUtilities.BOTTOM_CONNECTION;
        break;
      default:
        dir = DecoratorUtilities.BASELINE_CONNECTION;
        break;
    }
    DecoratorUtilities.ViewStates mode = (selection) ? DecoratorUtilities.ViewStates.SELECTED : DecoratorUtilities.ViewStates.NORMAL;

    DecoratorUtilities.setTimeChange(myComponent.getNlComponent(), dir, mode);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    float ratio = 1f / (float)sceneTransform.getScale();
    if (ratio > 2) {
      ratio = 2;
    }
    float size = (ANCHOR_SIZE * ratio);
    float minWidth = 4 * size;
    float minHeight = 4 * size;
    if (r - l < minWidth) {
      float d = (minWidth - (r - l)) / 2;
      l -= d;
      r += d;
    }
    if (b - t < minHeight) {
      float d = (minHeight - (b - t)) / 2;
      t -= d;
      b += d;
    }
    int w = r - l;
    int h = b - t;
    int mw = l + w / 2;
    int mh = t + h / 2;
    switch (myType) {
      case LEFT: {
        myLeft = l - size;
        myTop = mh - size;
        myRight = l + size;
        myBottom = mh + size;
        if (myExpandArea) {
          myLeft = l - EXPANDED_SIZE;
          myTop = t;
          myBottom = b;
        }
      }
      break;
      case TOP: {
        myLeft = mw - size;
        myTop = t - size;
        myRight = mw + size;
        myBottom = t + size;
        if (myExpandArea) {
          myTop = t - EXPANDED_SIZE;
          myLeft = l;
          myRight = r;
        }
      }
      break;
      case RIGHT: {
        myLeft = r - size;
        myTop = mh - size;
        myRight = r + size;
        myBottom = mh + size;
        if (myExpandArea) {
          myRight = r + EXPANDED_SIZE;
          myTop = t;
          myBottom = b;
        }
      }
      break;
      case BOTTOM: {
        myLeft = mw - size;
        myTop = b - size;
        myRight = mw + size;
        myBottom = b + size;
        if (myExpandArea) {
          myBottom = b + EXPANDED_SIZE;
          myLeft = l;
          myRight = r;
        }
      }
      break;
      case BASELINE: {
        myLeft = l + size;
        myTop = t + myComponent.getBaseline() - size / 2;
        myRight = r - size;
        myBottom = t + myComponent.getBaseline() + size / 2;
      }
      break;
    }
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  private boolean isConnected(AnchorTarget target) {
    if (target == null) {
      return false;
    }
    if (!isConnected()) {
      return false;
    }
    String attribute = null;
    switch (myType) {
      case LEFT: {
        attribute = ConstraintComponentUtilities.getConnectionId(myComponent.getAuthoritativeNlComponent(),
                                                                 SdkConstants.SHERPA_URI,
                                                                 ConstraintComponentUtilities.ourLeftAttributes);
        break;
      }
      case RIGHT: {
        attribute = ConstraintComponentUtilities.getConnectionId(myComponent.getAuthoritativeNlComponent(),
                                                                 SdkConstants.SHERPA_URI,
                                                                 ConstraintComponentUtilities.ourRightAttributes);
        break;
      }
      case TOP: {
        attribute = ConstraintComponentUtilities.getConnectionId(myComponent.getAuthoritativeNlComponent(),
                                                                 SdkConstants.SHERPA_URI,
                                                                 ConstraintComponentUtilities.ourTopAttributes);
        break;
      }
      case BOTTOM: {
        attribute = ConstraintComponentUtilities.getConnectionId(myComponent.getAuthoritativeNlComponent(),
                                                                 SdkConstants.SHERPA_URI,
                                                                 ConstraintComponentUtilities.ourBottomAttributes);
        break;
      }
      case BASELINE: {
        attribute = myComponent.getAuthoritativeNlComponent().getLiveAttribute(SdkConstants.SHERPA_URI,
                                                                               SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
        if (attribute != null) {
          attribute = NlComponent.extractId(attribute);
        }
        break;
      }
    }
    if (attribute == null) {
      return false;
    }
    return attribute.equalsIgnoreCase(target.getComponent().getId());
  }

  private boolean isConnected() {
    return ConstraintComponentUtilities.isAnchorConnected(myType, myComponent.getAuthoritativeNlComponent(), useRtlAttributes(), isRtl());
  }

  @SuppressWarnings("UseJBColor")
  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (!myVisibility) {
      return;
    }
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }
    if (myComponent.isDragging() && !isConnected()) {
      return;
    }

    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.green);
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, Color.red);
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, Color.red);
    }

    Integer state = DecoratorUtilities.getTryingToConnectState(myComponent.getNlComponent());

    boolean can_connect = state != null && (state & myType.getMask()) != 0;
    boolean is_connected = isConnected();
    int drawState = ((can_connect)?1:0) | (mIsOver?2:0) | (is_connected?4:0) | (isTargeted()?8:0) | (myComponent.isSelected()?16:0);

    int[] modeTable = {
      DrawAnchor.DO_NOT_DRAW, //
      DrawAnchor.CAN_CONNECT, // can_connect
      DrawAnchor.OVER,        // mIsOver
      DrawAnchor.CAN_CONNECT, // can_connect & mIsOver
      DrawAnchor.NORMAL,      // is_connected
      DrawAnchor.CAN_CONNECT, // is_connected & can_connect
      DrawAnchor.OVER,        // is_connected & mIsOver
      DrawAnchor.CAN_CONNECT, // is_connected & can_connect & mIsOver
      DrawAnchor.NORMAL,      // myThisIsTheTarget
      DrawAnchor.NORMAL,      // myThisIsTheTarget & can_connect
      DrawAnchor.DO_NOT_DRAW, // myThisIsTheTarget & mIsOver
      DrawAnchor.CAN_CONNECT, // myThisIsTheTarget & can_connect & mIsOver
      DrawAnchor.NORMAL,      // myThisIsTheTarget & is_connected &
      DrawAnchor.NORMAL,      // myThisIsTheTarget & is_connected & can_connect
      DrawAnchor.CANNOT_CONNECT, // myThisIsTheTarget & is_connected & mIsOver
      DrawAnchor.CAN_CONNECT, // myThisIsTheTarget & is_connected & can_connect & mIsOver
      DrawAnchor.NORMAL,      // isSelected
      DrawAnchor.NORMAL,      // isSelected & can_connect
      DrawAnchor.OVER,        // isSelected & mIsOver
      DrawAnchor.CAN_CONNECT, // isSelected & can_connect & mIsOver
      DrawAnchor.NORMAL,      // isSelected & is_connected
      DrawAnchor.NORMAL,      // isSelected & is_connected & can_connect
      DrawAnchor.OVER,        // isSelected & is_connected & mIsOver
      DrawAnchor.CAN_CONNECT, // isSelected & is_connected & can_connect & mIsOver
      DrawAnchor.NORMAL,      // isSelected & myThisIsTheTarget
      DrawAnchor.NORMAL,      // isSelected & myThisIsTheTarget & can_connect
      DrawAnchor.CANNOT_CONNECT,   // isSelected & myThisIsTheTarget & mIsOver
      DrawAnchor.CAN_CONNECT, // isSelected & myThisIsTheTarget & can_connect & mIsOver
      DrawAnchor.NORMAL,      // isSelected & myThisIsTheTarget & is_connected &
      DrawAnchor.NORMAL,      // isSelected & myThisIsTheTarget & is_connected & can_connect
      DrawAnchor.OVER,        // isSelected & myThisIsTheTarget & is_connected & mIsOver
      DrawAnchor.CAN_CONNECT, // isSelected & myThisIsTheTarget & is_connected & can_connect & mIsOver
    };
    int mode = modeTable[drawState];

    if (mode != DrawAnchor.DO_NOT_DRAW) {
      DrawAnchor.add(list, sceneContext, myLeft, myTop, myRight, myBottom,
                     myType == Type.BASELINE ? DrawAnchor.TYPE_BASELINE : DrawAnchor.TYPE_NORMAL, is_connected && !isTargeted(),
                     mode);
    }

    if (!myRenderingTemporaryConnection) {
      if (myLastX != -1 && myLastY != -1) {
        if ((myConnectedX == -1 && myConnectedY == -1)
            || !(myLastX == myConnectedX && myLastY == myConnectedY)) {
          float x = myLeft + (myRight - myLeft) / 2;
          float y = myTop + (myBottom - myTop) / 2;
          list.addConnection(sceneContext, x, y, myLastX, myLastY, myType.ordinal());
        }
      }
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Clear the attributes related to this Anchor type
   */
  private void clearMe(@NotNull AttributesTransaction transaction) {
    ConstraintComponentUtilities.clearAnchor(myType, transaction, useRtlAttributes(), isRtl());
  }

  /**
   * Store the existing attributes in mPreviousAttributes
   */
  private void rememberPreviousAttribute(@NotNull String uri, @NotNull ArrayList<String> attributes) {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    for (String attribute : attributes) {
      mPreviousAttributes.put(attribute, component.getLiveAttribute(uri, attribute));
    }
  }

  private boolean useRtlAttributes() {
    return myComponent.useRtlAttributes();
  }

  private boolean isRtl() {
    return myComponent.getScene().isInRTL();
  }

  /**
   * Return the correct attribute string given our type and the target type
   */
  private String getAttribute(@NotNull Target target) {
    if (!(target instanceof AnchorTarget)) {
      return null;
    }
    AnchorTarget anchorTarget = (AnchorTarget)target;
    return ConstraintComponentUtilities.getAttribute(myType, anchorTarget.myType, useRtlAttributes(), isRtl());
  }

  /**
   * Revert to the original (on mouse down) state.
   */
  private void revertToPreviousState() {
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
    for (String key : mPreviousAttributes.keySet()) {
      if (key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X) ||
          key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)) {
        attributes.setAttribute(SdkConstants.TOOLS_URI, key, mPreviousAttributes.get(key));
      }
      else if (key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_TOP)
            || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM)
            || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT)
            || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT)
            || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_START)
            || key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_MARGIN_END)) {
        attributes.setAttribute(SdkConstants.ANDROID_URI, key, mPreviousAttributes.get(key));
      }
      else {
        attributes.setAttribute(SdkConstants.SHERPA_URI, key, mPreviousAttributes.get(key));
      }
    }
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
  }

  /**
   * Connect the anchor to the given target. Applied immediately in memory.
   */
  private AttributesTransaction connectMe(NlComponent component, String attribute, NlComponent targetComponent) {
    AttributesTransaction attributes = component.startAttributeTransaction();
    String targetId;
    NlComponent parent = component.getParent();
    assert parent != null;
    if (NlComponentHelperKt.isOrHasSuperclass(parent, SdkConstants.CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS)) {
      parent = parent.getParent();
    }
    if (targetComponent == parent) {
      targetId = SdkConstants.ATTR_PARENT;
    }
    else {
      targetId = SdkConstants.NEW_ID_PREFIX + NlComponentHelperKt.ensureLiveId(targetComponent);
    }
    attributes.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);
    if (myType == Type.BASELINE) {
      ConstraintComponentUtilities.clearAttributes(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes, attributes);
      ConstraintComponentUtilities.clearAttributes(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes, attributes);
      attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, null);
      attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, null);
    }
    else if (ConstraintComponentUtilities.ourReciprocalAttributes.get(attribute) != null) {
      attributes.setAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourReciprocalAttributes.get(attribute), null);
    }

    if (ConstraintComponentUtilities.ourMapMarginAttributes.get(attribute) != null) {
      Scene scene = myComponent.getScene();
      int marginValue = getDistance(attribute, targetComponent, scene);
      if (!scene.isControlDown()) {
        if (marginValue < 0) {
          marginValue = 0;
        }
        else {
          marginValue = Scout.getMargin();
        }
      }
      else {
        marginValue = Math.max(marginValue, 0);
      }
      String margin = String.format(SdkConstants.VALUE_N_DP, marginValue);
      String attr = ConstraintComponentUtilities.ourMapMarginAttributes.get(attribute);
      attributes.setAttribute(SdkConstants.ANDROID_URI, attr, margin);
      if (SdkConstants.ATTR_LAYOUT_MARGIN_END.equals(attr)) {
        attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, margin);
      }
      else if (SdkConstants.ATTR_LAYOUT_MARGIN_START.equals(attr)) {
        attributes.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, margin);
      }
      scene.needsRebuildList();
      myConnectedX = myLastX;
      myConnectedY = myLastY;
    }
    ConstraintComponentUtilities.cleanup(attributes, myComponent.getNlComponent());
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
    myRenderingTemporaryConnection = true;
    return attributes;
  }

  private int getDistance(String attribute, NlComponent targetComponent, Scene scene) {
    int marginValue;
    AnchorTarget targetAnchor = ConstraintComponentUtilities.getTargetAnchor(scene, targetComponent, attribute, useRtlAttributes(), isRtl());
    if (targetAnchor == null) {
      return 0;
    }
    switch (myType) {
      case LEFT: {
        switch (targetAnchor.getType()) {
          case LEFT:
          case RIGHT: {
            marginValue = (int)(getCenterX() - targetAnchor.getCenterX());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case RIGHT: {
        switch (targetAnchor.getType()) {
          case LEFT:
          case RIGHT: {
            marginValue = (int)(targetAnchor.getCenterX() - getCenterX());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case TOP: {
        switch (targetAnchor.getType()) {
          case TOP:
          case BOTTOM: {
            marginValue = (int)(getCenterY() - targetAnchor.getCenterY());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      case BOTTOM: {
        switch (targetAnchor.getType()) {
          case TOP:
          case BOTTOM: {
            marginValue = (int)(targetAnchor.getCenterY() - getCenterY());
          }
          break;
          default:
            marginValue = 0;
        }
      }
      break;
      default:
        marginValue = 0;
    }
    return marginValue;
  }

  /**
   * Disconnect the anchor
   *
   * @param component
   */
  private void disconnectMe(NlComponent component) {
    AttributesTransaction attributes = component.startAttributeTransaction();
    clearMe(attributes);
    ConstraintComponentUtilities.cleanup(attributes, myComponent.getNlComponent());
    attributes.apply();
    NlWriteCommandAction.run(component, "Constraint Disconnected", attributes::commit);
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  @Override
  public void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    myLastX = -1;
    myLastY = -1;
    myConnectedX = -1;
    myConnectedY = -1;
    NlComponent component = myComponent.getAuthoritativeNlComponent();
    mPreviousAttributes.clear();
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                            component.getLiveAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X));
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                            component.getLiveAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y));
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF,
                            component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF));
    if (myComponent.getParent() != null) {
      myComponent.getParent().setExpandTargetArea(true);
    }
    switch (myType) {
      case LEFT:
      case RIGHT: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourLeftAttributes);
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourRightAttributes);
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourStartAttributes);
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourEndAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_START,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_END,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS));
      }
      break;
      case TOP: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
      case BOTTOM: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
      case BASELINE: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourTopAttributes);
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ConstraintComponentUtilities.ourBottomAttributes);
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
                                component.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM));
        mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
                                component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS));
      }
      break;
    }
  }

  /**
   * On mouse drag, we can connect (in memory) to existing targets, or revert to the
   * original state that we captured on mouseDown.
   */
  @Override
  public void mouseDrag(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    myLastX = x;
    myLastY = y;
    Target closestTarget = null;
    for (Target target : closestTargets) {
      if (target instanceof AnchorTarget && target != this) {
        closestTarget = target;
        break;
      }
    }
    if (!myInDrag) {
      myInDrag = true;
      DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, true);
    }
    if (myCurrentClosestTarget != closestTarget) {
      myCurrentClosestTarget = null;
      if (closestTarget != null) {
        myCurrentClosestTarget = ((AnchorTarget)closestTarget);
      }
    }

    if (closestTarget != null) {
      NlComponent component = myComponent.getAuthoritativeNlComponent();
      String attribute = getAttribute(closestTarget);
      if (attribute != null) {
        AnchorTarget targetAnchor = (AnchorTarget)closestTarget;
        if (targetAnchor.myComponent != myComponent && !targetAnchor.isConnected(this)) {
          if (myComponent.getParent() != targetAnchor.myComponent) {
            Integer state = DecoratorUtilities.getTryingToConnectState(targetAnchor.myComponent.getNlComponent());
            if (state == null) {
              return;
            }
            int mask = state & targetAnchor.myType.getMask();
            if (mask == 0) {
              return;
            }
          }

          NlComponent targetComponent = targetAnchor.myComponent.getAuthoritativeNlComponent();
          connectMe(component, attribute, targetComponent);
          return;
        }
      }
    }
    revertToPreviousState();
    myRenderingTemporaryConnection = false;
  }

  /**
   * On mouseRelease, we can either disconnect the current anchor (if the mouse release is on ourselves)
   * or connect the anchor to a given target. Modifications are applied first in memory then committed
   * to the XML model.
   */
  @Override
  public void mouseRelease(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, @NotNull List<Target> closestTargets) {
    myLastX = -1;
    myLastY = -1;
    try {
      if (myComponent.getParent() != null) {
        myComponent.getParent().setExpandTargetArea(false);
      }
      AnchorTarget closestTarget = null;
      for (Target target : closestTargets) {
        if (target instanceof AnchorTarget && target != this) {
          closestTarget = (AnchorTarget) target;
          break;
        }
      }
      if (closestTarget == null && closestTargets.contains(this)) {
        closestTarget = this;
      }
      if (closestTarget != null && !closestTarget.isConnected(this)) {
        NlComponent component = myComponent.getAuthoritativeNlComponent();
        if (closestTarget == this) {
          disconnectMe(component);
        }
        else {
          String attribute = getAttribute(closestTarget);
          if (attribute != null) {
            if (closestTarget.myComponent == myComponent) {
              return;
            }
            if (myComponent.getParent() != closestTarget.myComponent) {
              Integer state = DecoratorUtilities.getTryingToConnectState(closestTarget.myComponent.getNlComponent());
              if (state == null) {
                return;
              }
              int mask = state & closestTarget.myType.getMask();
              if (mask == 0) {
                return;
              }
            }
            NlComponent targetComponent = closestTarget.myComponent.getAuthoritativeNlComponent();
            AttributesTransaction attributes = connectMe(component, attribute, targetComponent);

            NlWriteCommandAction.run(component, "Constraint Connected", attributes::commit);
            myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
          }
        }
      }
    }
    finally {
      if (myInDrag) {
        myInDrag = false;
        DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, false);
      }
    }
  }

  @Override
  public String getToolTipText() {
    return (isConnected()) ? "Delete Connection" : "Create Connection";
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
