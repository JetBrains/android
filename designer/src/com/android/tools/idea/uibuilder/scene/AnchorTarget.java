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
package com.android.tools.idea.uibuilder.scene;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
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
public class AnchorTarget extends ConstraintTarget implements Target {

  // Type of possible anchors
  public enum Type {
    LEFT, TOP, RIGHT, BOTTOM, BASELINE
  }

  private final int mySize = 8;
  private final int myExpandSize = 200;
  private final AnchorTarget.Type myType;
  private boolean myExpandArea = false;

  private int myLastX = -1;
  private int myLastY = -1;

  private int myLeft = 0;
  private int myTop = 0;
  private int myRight = 0;
  private int myBottom = 0;
  private boolean mIsOver = false;
  private HashMap<String, String> mPreviousAttributes = new HashMap();

  /////////////////////////////////////////////////////////////////////////////
  //region Constructor
  /////////////////////////////////////////////////////////////////////////////

  public AnchorTarget(@NotNull AnchorTarget.Type type) {
    myType = type;
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

  @Override
  public void setOver(boolean over) {
    mIsOver = over;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(int l, int t, int r, int b) {
    int minWidth = 4 * mySize;
    int minHeight = 4 * mySize;
    if (r - l < minWidth) {
      int d = (minWidth - (r - l)) / 2;
      l -= d;
      r += d;
    }
    if (b - t < minHeight) {
      int d = (minHeight - (b - t)) / 2;
      t -= d;
      b += d;
    }
    int w = r - l;
    int h = b - t;
    int mw = l + w / 2;
    int mh = t + h / 2;
    switch (myType) {
      case LEFT: {
        myLeft = l - mySize;
        myTop = mh - mySize;
        myRight = l + mySize;
        myBottom = mh + mySize;
        if (myExpandArea) {
          myLeft = l - myExpandSize;
          myTop = t;
          myBottom = b;
        }
      }
      break;
      case TOP: {
        myLeft = mw - mySize;
        myTop = t - mySize;
        myRight = mw + mySize;
        myBottom = t + mySize;
        if (myExpandArea) {
          myTop = t - myExpandSize;
          myLeft = l;
          myRight = r;
        }
      }
      break;
      case RIGHT: {
        myLeft = r - mySize;
        myTop = mh - mySize;
        myRight = r + mySize;
        myBottom = mh + mySize;
        if (myExpandArea) {
          myRight = r + myExpandSize;
          myTop = t;
          myBottom = b;
        }
      }
      break;
      case BOTTOM: {
        myLeft = mw - mySize;
        myTop = b - mySize;
        myRight = mw + mySize;
        myBottom = b + mySize;
        if (myExpandArea) {
          myBottom = b + myExpandSize;
          myLeft = l;
          myRight = r;
        }
      }
      break;
      case BASELINE: {
        // TODO
      }
      break;
    }
    return false;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Display
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public void render(@NotNull DisplayList list) {
    list.addRect(myLeft, myTop, myRight, myBottom, mIsOver ? Color.yellow : Color.green);
    if (myLastX != -1 && myLastY != -1) {
      int x = myLeft + (myRight - myLeft) / 2;
      int y = myTop + (myBottom - myTop) / 2;
      list.addConnection(x, y, myLastX, myLastY, Color.red);
    }
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Utilities
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Clear the attributes related to this Anchor type
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
    }
  }

  /**
   * Store the existing attributes in mPreviousAttributes
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
    }
    return null;
  }

  /**
   * Revert to the original (on mouse down) state.
   */
  private void revertToPreviousState() {
    NlComponent component = myComponent.getNlComponent();
    AttributesTransaction attributes = component.startAttributeTransaction();
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
      targetId = SdkConstants.NEW_ID_PREFIX + targetComponent.ensureId();
    }
    attributes.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);
    attributes.setAttribute(SdkConstants.SHERPA_URI, ourReciprocalAttributes.get(attribute), null);
    cleanup(attributes);
    attributes.apply();
    myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
    return attributes;
  }

  /**
   * Disconnect the anchor
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

  public int getCenterX() {
    return myLeft + (myRight - myLeft) / 2;
  }

  public int getCenterY() {
    return myTop + (myBottom - myTop) / 2;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Mouse Handling
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public int getPreferenceLevel() {
    return 20;
  }

  @Override
  public void addHit(@NotNull ScenePicker picker) {
    mIsOver = false;
    picker.addRect(this, 0, myLeft, myTop, myRight, myBottom);
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
