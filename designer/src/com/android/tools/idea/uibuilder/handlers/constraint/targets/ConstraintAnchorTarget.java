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
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import com.android.tools.idea.uibuilder.scout.Scout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Implements a target anchor for the ConstraintLayout.
 */
public class ConstraintAnchorTarget extends AnchorTarget {

  private final boolean myVisibility;
  private final Type myType;

  private ConstraintAnchorTarget myCurrentClosestTarget; // used to define the closest target during drag;
  private boolean myInDrag = false;
  private boolean myRenderingTemporaryConnection = false;
  @AndroidDpCoordinate private int myConnectedX = -1;
  @AndroidDpCoordinate private int myConnectedY = -1;

  private final HashMap<String, String> mPreviousAttributes = new HashMap<>();

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public ConstraintAnchorTarget(@NotNull Type type, boolean visible) {
    super(type);
    myType = type;
    myVisibility = visible;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Accessors
  /////////////////////////////////////////////////////////////////////////////

  public boolean isHorizontalAnchor() {
    return myType == Type.LEFT || myType == Type.RIGHT;
  }

  public boolean isVerticalAnchor() {
    return myType == Type.TOP || myType == Type.BOTTOM;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  private boolean isConnected(ConstraintAnchorTarget target) {
    if (target == null) {
      return false;
    }
    if (!isConnected()) {
      return false;
    }

    String attribute = null;
    //noinspection EnumSwitchStatementWhichMissesCases
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

  @Override
  protected boolean isConnected() {
    return ConstraintComponentUtilities.isAnchorConnected(myType, myComponent.getAuthoritativeNlComponent(), useRtlAttributes(), isRtl());
  }

  @SuppressWarnings("UseJBColor")
  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (!myVisibility) {
      return;
    }

    super.render(list, sceneContext);

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

  @NotNull
  @Override
  protected DrawAnchor.Mode getDrawMode() {
    Integer state = DecoratorUtilities.getTryingToConnectState(myComponent.getNlComponent());
    boolean can_connect = state != null && (state & myType.getMask()) != 0;
    boolean is_connected = isConnected();
    int drawState =
      ((can_connect) ? 1 : 0) | (mIsOver ? 2 : 0) | (is_connected ? 4 : 0) | (isTargeted() ? 8 : 0) | (myComponent.isSelected() ? 16 : 0);

    DrawAnchor.Mode[] modeTable = {
      DrawAnchor.Mode.DO_NOT_DRAW, //
      DrawAnchor.Mode.CAN_CONNECT, // can_connect
      DrawAnchor.Mode.OVER,        // mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // is_connected
      DrawAnchor.Mode.CAN_CONNECT, // is_connected & can_connect
      DrawAnchor.Mode.OVER,        // is_connected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // is_connected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // myThisIsTheTarget
      DrawAnchor.Mode.NORMAL,      // myThisIsTheTarget & can_connect
      DrawAnchor.Mode.DO_NOT_DRAW, // myThisIsTheTarget & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // myThisIsTheTarget & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // myThisIsTheTarget & is_connected &
      DrawAnchor.Mode.NORMAL,      // myThisIsTheTarget & is_connected & can_connect
      DrawAnchor.Mode.CANNOT_CONNECT, // myThisIsTheTarget & is_connected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // myThisIsTheTarget & is_connected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected
      DrawAnchor.Mode.NORMAL,      // isSelected & can_connect
      DrawAnchor.Mode.OVER,        // isSelected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // isSelected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & is_connected
      DrawAnchor.Mode.NORMAL,      // isSelected & is_connected & can_connect
      DrawAnchor.Mode.OVER,        // isSelected & is_connected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // isSelected & is_connected & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & myThisIsTheTarget
      DrawAnchor.Mode.NORMAL,      // isSelected & myThisIsTheTarget & can_connect
      DrawAnchor.Mode.CANNOT_CONNECT,   // isSelected & myThisIsTheTarget & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // isSelected & myThisIsTheTarget & can_connect & mIsOver
      DrawAnchor.Mode.NORMAL,      // isSelected & myThisIsTheTarget & is_connected &
      DrawAnchor.Mode.NORMAL,      // isSelected & myThisIsTheTarget & is_connected & can_connect
      DrawAnchor.Mode.OVER,        // isSelected & myThisIsTheTarget & is_connected & mIsOver
      DrawAnchor.Mode.CAN_CONNECT, // isSelected & myThisIsTheTarget & is_connected & can_connect & mIsOver
    };
    return modeTable[drawState];
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
    if (!(target instanceof ConstraintAnchorTarget)) {
      return null;
    }
    ConstraintAnchorTarget anchorTarget = (ConstraintAnchorTarget)target;
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
    ConstraintAnchorTarget
      targetAnchor = ConstraintComponentUtilities.getTargetAnchor(scene, targetComponent, attribute, useRtlAttributes(), isRtl());
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
    super.mouseDown(x, y);

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
    //noinspection EnumSwitchStatementWhichMissesCases
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
    super.mouseDrag(x, y, closestTargets);

    ConstraintAnchorTarget targetAnchor = null;
    for (Target target : closestTargets) {
      if (target instanceof ConstraintAnchorTarget && target != this) {
        targetAnchor = (ConstraintAnchorTarget)target;
        break;
      }
    }
    if (!myInDrag) {
      myInDrag = true;
      DecoratorUtilities.setTryingToConnectState(myComponent.getNlComponent(), myType, true);
    }
    myCurrentClosestTarget = targetAnchor;

    if (targetAnchor != null) {
      NlComponent component = myComponent.getAuthoritativeNlComponent();
      String attribute = getAttribute(targetAnchor);
      if (attribute != null) {
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
    super.mouseRelease(x, y, closestTargets);

    try {
      if (myComponent.getParent() != null) {
        myComponent.getParent().setExpandTargetArea(false);
      }
      ConstraintAnchorTarget closestTarget = null;
      for (Target target : closestTargets) {
        if (target instanceof ConstraintAnchorTarget && target != this) {
          closestTarget = (ConstraintAnchorTarget)target;
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

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
