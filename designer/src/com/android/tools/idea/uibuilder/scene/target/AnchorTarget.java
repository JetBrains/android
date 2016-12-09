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
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.draw.DrawAnchor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Implements a target anchor for the ConstraintLayout viewgroup
 */
public class AnchorTarget extends ConstraintTarget {

  private static final boolean DEBUG_RENDERER = false;
  private final boolean myVisibility;

  // Type of possible anchors
  public enum Type {
    LEFT, TOP, RIGHT, BOTTOM, BASELINE
  }

  protected final int mySize = 3;
  private final int myExpandSize = 200;
  private final AnchorTarget.Type myType;
  private boolean myExpandArea = false;

  private int myLastX = -1;
  private int myLastY = -1;

  private HashMap<String, String> mPreviousAttributes = new HashMap();

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

  public Type getType() {
    return myType;
  }

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
  public void setOver(boolean over) {
    if (over != mIsOver) {
      mIsOver = over;
      myComponent.getScene().needsRebuildList();
      myComponent.getScene().repaint();
    }
  }
  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {
    float ratio = 1f / (float) sceneTransform.getScale();
    if (ratio > 2) {
      ratio = 2;
    }
    float size = (mySize * ratio);
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
          myLeft = l - myExpandSize;
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
          myTop = t - myExpandSize;
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
          myRight = r + myExpandSize;
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
          myBottom = b + myExpandSize;
          myLeft = l;
          myRight = r;
        }
      }
      break;
      case BASELINE: {
        myLeft = l + size;
        myTop = t + myComponent.getBaseline() - size/2;
        myRight = r - size;
        myBottom = t + myComponent.getBaseline() + size/2;
      }
      break;
    }
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  private boolean isConnected() {
    NlComponent component = myComponent.getNlComponent();
    switch (myType) {
      case LEFT:
        return hasAttributes(component, SdkConstants.SHERPA_URI, ourLeftAttributes);
      case TOP:
        return hasAttributes(component, SdkConstants.SHERPA_URI, ourTopAttributes);
      case RIGHT:
        return hasAttributes(component, SdkConstants.SHERPA_URI, ourRightAttributes);
      case BOTTOM:
        return hasAttributes(component, SdkConstants.SHERPA_URI, ourBottomAttributes);
      case BASELINE:
        return component.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
    }
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    if (!myVisibility) {
      return;
    }
    if (!myComponent.getScene().allowsTarget(this)) {
      return;
    }
    if (DEBUG_RENDERER) {
      list.addRect(sceneContext, myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.green);
      list.addLine(sceneContext, myLeft, myTop, myRight, myBottom, Color.red);
      list.addLine(sceneContext, myLeft, myBottom, myRight, myTop, Color.red);
    }
    DrawAnchor.add(list, sceneContext, myLeft, myTop, myRight, myBottom,
                   myType == Type.BASELINE ? DrawAnchor.TYPE_BASELINE : DrawAnchor.TYPE_NORMAL, isConnected(),
                   mIsOver ? DrawAnchor.OVER : DrawAnchor.NORMAL);
    if (myLastX != -1 && myLastY != -1) {
      float x = myLeft + (myRight - myLeft) / 2;
      float y = myTop + (myBottom - myTop) / 2;
      list.addConnection(sceneContext, x, y, myLastX, myLastY, myType.ordinal());
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Clear the attributes related to this Anchor type
   *
   * @param transaction
   */
  private void clearMe(@NotNull AttributesTransaction transaction) {
    switch (myType) {
      case LEFT: {
        clearAttributes(SdkConstants.SHERPA_URI, ourLeftAttributes, transaction);
      }
      break;
      case RIGHT: {
        clearAttributes(SdkConstants.SHERPA_URI, ourRightAttributes, transaction);
      }
      break;
      case TOP: {
        clearAttributes(SdkConstants.SHERPA_URI, ourTopAttributes, transaction);
      }
      break;
      case BOTTOM: {
        clearAttributes(SdkConstants.SHERPA_URI, ourBottomAttributes, transaction);
      }
      break;
      case BASELINE: {
        transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
      }
      break;
    }
  }

  /**
   * Store the existing attributes in mPreviousAttributes
   *
   * @param uri
   * @param attributes
   */
  private void rememberPreviousAttribute(@NotNull String uri, @NotNull ArrayList<String> attributes) {
    NlComponent component = myComponent.getNlComponent();
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      mPreviousAttributes.put(attribute, component.getAttribute(uri, attribute));
    }
  }

  /**
   * Return the correct attribute string given our type and the target type
   *
   * @param target
   * @return
   */
  private String getAttribute(@NotNull Target target) {
    if (!(target instanceof AnchorTarget)) {
      return null;
    }
    AnchorTarget anchorTarget = (AnchorTarget)target;
    switch (myType) {
      case LEFT: {
        if (anchorTarget.myType == Type.LEFT) {
          return SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF;
        }
        if (anchorTarget.myType == Type.RIGHT) {
          return SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF;
        }
      }
      break;
      case RIGHT: {
        if (anchorTarget.myType == Type.LEFT) {
          return SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF;
        }
        if (anchorTarget.myType == Type.RIGHT) {
          return SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF;
        }
      }
      break;
      case TOP: {
        if (anchorTarget.myType == Type.TOP) {
          return SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
        }
        if (anchorTarget.myType == Type.BOTTOM) {
          return SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
        }
      }
      break;
      case BOTTOM: {
        if (anchorTarget.myType == Type.TOP) {
          return SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
        }
        if (anchorTarget.myType == Type.BOTTOM) {
          return SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
        }
      }
      break;
      case BASELINE: {
        return SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF;
      }
    }
    return null;
  }

  /**
   * Revert to the original (on mouse down) state.
   */
  private void revertToPreviousState() {
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
    attributes.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
    for (String key : mPreviousAttributes.keySet()) {
      if (key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X)) {
        attributes.setAttribute(SdkConstants.TOOLS_URI, key, mPreviousAttributes.get(key));
      }
      else if (key.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y)) {
        attributes.setAttribute(SdkConstants.TOOLS_URI, key, mPreviousAttributes.get(key));
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
   *
   * @param component
   * @param attribute
   * @param targetComponent
   * @return
   */
  private AttributesTransaction connectMe(NlComponent component, String attribute, NlComponent targetComponent) {
    AttributesTransaction attributes = component.startAttributeTransaction();
    String targetId = null;
    if (targetComponent == component.getParent()) {
      targetId = SdkConstants.ATTR_PARENT;
    }
    else {
      targetId = SdkConstants.NEW_ID_PREFIX + targetComponent.ensureLiveId();
    }
    attributes.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);
    if (ourReciprocalAttributes.get(attribute) != null) {
      attributes.setAttribute(SdkConstants.SHERPA_URI, ourReciprocalAttributes.get(attribute), null);
    }
    cleanup(attributes);
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
    return attributes;
  }

  /**
   * Disconnect the anchor
   *
   * @param component
   */
  private void disconnectMe(NlComponent component) {
    String label = "Constraint Disconnected";
    NlModel nlModel = component.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();
    AttributesTransaction attributes = component.startAttributeTransaction();
    clearMe(attributes);
    cleanup(attributes);
    attributes.apply();
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        attributes.commit();
      }
    };
    action.execute();
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
  public void mouseDown(int x, int y) {
    myLastX = -1;
    myLastY = -1;
    mPreviousAttributes.clear();
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                            myComponent.getNlComponent().getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X));
    mPreviousAttributes.put(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                            myComponent.getNlComponent().getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y));
    if (myComponent.getParent() != null) {
      myComponent.getParent().setExpandTargetArea(true);
    }
    switch (myType) {
      case LEFT: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ourLeftAttributes);
      }
      break;
      case RIGHT: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ourRightAttributes);
      }
      break;
      case TOP: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ourTopAttributes);
      }
      break;
      case BOTTOM: {
        rememberPreviousAttribute(SdkConstants.SHERPA_URI, ourBottomAttributes);
      }
      break;
    }
  }

  /**
   * On mouse drag, we can connect (in memory) to existing targets, or revert to the
   * original state that we capatured on mouseDown.
   *
   * @param x
   * @param y
   * @param closestTarget
   */
  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {
    myLastX = x;
    myLastY = y;
    if (closestTarget != null && closestTarget instanceof AnchorTarget) {
      NlComponent component = myComponent.getNlComponent();
      String attribute = getAttribute(closestTarget);
      if (attribute != null) {
        AnchorTarget targetAnchor = (AnchorTarget)closestTarget;
        if (targetAnchor.myComponent != myComponent) {
          NlComponent targetComponent = targetAnchor.myComponent.getNlComponent();
          connectMe(component, attribute, targetComponent);
          return;
        }
      }
    }
    revertToPreviousState();
  }

  /**
   * On mouseRelease, we can either disconnect the current anchor (if the mouse release is on ourselve)
   * or connect the anchor to a given target. Modifications are applied first in memory then commited
   * to the XML model.
   *
   * @param x
   * @param y
   * @param closestTarget
   */
  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    myLastX = -1;
    myLastY = -1;
    if (myComponent.getParent() != null) {
      myComponent.getParent().setExpandTargetArea(false);
    }
    if (closestTarget != null && closestTarget instanceof AnchorTarget) {
      NlComponent component = myComponent.getNlComponent();
      if (closestTarget == this) {
        disconnectMe(component);
      }
      else {
        String attribute = getAttribute(closestTarget);
        if (attribute != null) {
          AnchorTarget targetAnchor = (AnchorTarget)closestTarget;
          NlComponent targetComponent = targetAnchor.myComponent.getNlComponent();
          AttributesTransaction attributes = connectMe(component, attribute, targetComponent);

          NlModel nlModel = component.getModel();
          Project project = nlModel.getProject();
          XmlFile file = nlModel.getFile();

          String label = "Constraint Connected";
          WriteCommandAction action = new WriteCommandAction(project, label, file) {
            @Override
            protected void run(@NotNull Result result) throws Throwable {
              attributes.commit();
            }
          };
          action.execute();
          myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
        }
      }
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
}
