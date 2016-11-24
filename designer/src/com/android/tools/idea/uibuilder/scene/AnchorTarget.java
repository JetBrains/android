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
public class AnchorTarget implements Target {

  // Type of possible anchors
  public enum Type {
    LEFT, TOP, RIGHT, BOTTOM, BASELINE
  }

  private final int mySize = 8;
  private final int myExpandSize = 200;
  private SceneComponent myComponent;
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

  private static final HashMap<String, String> ourReciprocalAttributes;
  private static final ArrayList<String> ourLeftAttributes;
  private static final ArrayList<String> ourTopAttributes;
  private static final ArrayList<String> ourRightAttributes;
  private static final ArrayList<String> ourBottomAttributes;

  static {
    ourReciprocalAttributes = new HashMap<>();
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    ourReciprocalAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);

    ourLeftAttributes = new ArrayList<>();
    ourLeftAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    ourLeftAttributes.add(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);

    ourTopAttributes = new ArrayList<>();
    ourTopAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    ourTopAttributes.add(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);

    ourRightAttributes = new ArrayList<>();
    ourRightAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    ourRightAttributes.add(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);

    ourBottomAttributes = new ArrayList<>();
    ourBottomAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    ourBottomAttributes.add(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
  }

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

  @Override
  public void setComponent(@NotNull SceneComponent component) {
    myComponent = component;
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Layout
  /////////////////////////////////////////////////////////////////////////////

  @Override
  public boolean layout(int l, int t, int r, int b) {
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

  private boolean hasAttributes(String uri, ArrayList<String> attributes) {
    NlComponent component = myComponent.getNlComponent();
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      if (component.getAttribute(uri, attribute) != null) {
        return true;
      }
    }
    return false;
  }

  private void clearAttributes(String uri, ArrayList<String> attributes, AttributesTransaction transaction) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      transaction.setAttribute(uri, attribute, null);
    }
  }

  private boolean hasLeft() {
    return hasAttributes(SdkConstants.SHERPA_URI, ourLeftAttributes);
  }

  private boolean hasTop() {
    return hasAttributes(SdkConstants.SHERPA_URI, ourTopAttributes);
  }

  private boolean hasRight() {
    return hasAttributes(SdkConstants.SHERPA_URI, ourRightAttributes);
  }

  private boolean hasBottom() {
    return hasAttributes(SdkConstants.SHERPA_URI, ourBottomAttributes);
  }

  private void setDpAttribute(String uri, String attribute, AttributesTransaction transaction, int value) {
    String position = String.format(SdkConstants.VALUE_N_DP, value);
    transaction.setAttribute(uri, attribute, position);
  }

  private void clear(@NotNull AttributesTransaction transaction) {
    switch (myType) {
      case LEFT: {
        clearAttributes(SdkConstants.SHERPA_URI, ourLeftAttributes, transaction);
        if (!hasRight()) {
          setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, myComponent.getOffsetParentX());
        }
      }
      break;
      case RIGHT: {
        clearAttributes(SdkConstants.SHERPA_URI, ourRightAttributes, transaction);
        if (!hasLeft()) {
          setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, myComponent.getOffsetParentX());
        }
      }
      break;
      case TOP: {
        clearAttributes(SdkConstants.SHERPA_URI, ourTopAttributes, transaction);
        if (!hasBottom()) {
          setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, myComponent.getOffsetParentY());
        }
      }
      break;
      case BOTTOM: {
        clearAttributes(SdkConstants.SHERPA_URI, ourBottomAttributes, transaction);
        if (!hasTop()) {
          setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, myComponent.getOffsetParentY());
        }
      }
      break;
    }
  }

  private void rememberPreviousAttribute(@NotNull String uri, @NotNull ArrayList<String> attributes) {
    NlComponent component = myComponent.getNlComponent();
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      mPreviousAttributes.put(attribute, component.getAttribute(uri, attribute));
    }
  }

  private String setReciprocalAttribute(@NotNull AttributesTransaction attributes, @NotNull String attribute) {
    switch (myType) {
      case LEFT:
      case RIGHT: {
        attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
      }
      break;
      case TOP:
      case BOTTOM: {
        attributes.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
      }
    }
    attributes.setAttribute(SdkConstants.SHERPA_URI, ourReciprocalAttributes.get(attribute), null);
    return null;
  }

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
          AttributesTransaction attributes = component.startAttributeTransaction();
          String targetId = null;
          if (targetComponent == component.getParent()) {
            targetId = SdkConstants.ATTR_PARENT;
          }
          else {
            targetId = SdkConstants.NEW_ID_PREFIX + targetComponent.ensureId();
          }
          attributes.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);
          setReciprocalAttribute(attributes, attribute);
          attributes.apply();
          myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
          return;
        }
      }
    }
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
        String label = "Constraint";
        NlModel nlModel = component.getModel();
        Project project = nlModel.getProject();
        XmlFile file = nlModel.getFile();
        WriteCommandAction action = new WriteCommandAction(project, label, file) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            AttributesTransaction attributes = component.startAttributeTransaction();
            clear(attributes);
            attributes.commit();
          }
        };
        action.execute();
        myComponent.getScene().needsLayout(Scene.ANIMATED_LAYOUT);
      }
      else {
        String attribute = getAttribute(closestTarget);
        if (attribute != null) {
          AnchorTarget targetAnchor = (AnchorTarget)closestTarget;
          NlComponent targetComponent = targetAnchor.myComponent.getNlComponent();
          NlModel nlModel = component.getModel();
          Project project = nlModel.getProject();
          XmlFile file = nlModel.getFile();

          AttributesTransaction attributes = component.startAttributeTransaction();
          String targetId = null;
          if (targetComponent == component.getParent()) {
            targetId = SdkConstants.ATTR_PARENT;
          }
          else {
            targetId = SdkConstants.NEW_ID_PREFIX + targetComponent.ensureId();
          }
          attributes.setAttribute(SdkConstants.SHERPA_URI, attribute, targetId);
          setReciprocalAttribute(attributes, attribute);
          attributes.apply();

          String label = "Constraint";
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
