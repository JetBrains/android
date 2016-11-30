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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Implements common checks on attributes of a ConstraintLayout child
 */
public class ConstraintTarget {
  protected static final HashMap<String, String> ourReciprocalAttributes;
  protected static final HashMap<String, String> ourMarginAttributes;
  protected static final ArrayList<String> ourLeftAttributes;
  protected static final ArrayList<String> ourTopAttributes;
  protected static final ArrayList<String> ourRightAttributes;
  protected static final ArrayList<String> ourBottomAttributes;
  protected SceneComponent myComponent;

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

    ourMarginAttributes = new HashMap<>();
    ourMarginAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMarginAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMarginAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMarginAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMarginAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMarginAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMarginAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
    ourMarginAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);

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

  public void setComponent(@NotNull SceneComponent component) {
    myComponent = component;
  }

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

  private boolean hasAttributes(@NotNull AttributesTransaction transaction, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      if (transaction.getAttribute(uri, attribute) != null) {
        return true;
      }
    }
    return false;
  }

  protected boolean hasLeft(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SdkConstants.SHERPA_URI, ourLeftAttributes);
  }

  protected boolean hasTop(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SdkConstants.SHERPA_URI, ourTopAttributes);
  }

  protected boolean hasRight(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SdkConstants.SHERPA_URI, ourRightAttributes);
  }

  protected boolean hasBottom(@NotNull AttributesTransaction transaction) {
    return hasAttributes(transaction, SdkConstants.SHERPA_URI, ourBottomAttributes);
  }

  protected boolean hasLeft() {
    return hasAttributes(SdkConstants.SHERPA_URI, ourLeftAttributes);
  }

  protected boolean hasTop() {
    return hasAttributes(SdkConstants.SHERPA_URI, ourTopAttributes);
  }

  protected boolean hasRight() {
    return hasAttributes(SdkConstants.SHERPA_URI, ourRightAttributes);
  }

  protected boolean hasBottom() {
    return hasAttributes(SdkConstants.SHERPA_URI, ourBottomAttributes);
  }

  protected void setDpAttribute(String uri, String attribute, AttributesTransaction transaction, int value) {
    String position = String.format(SdkConstants.VALUE_N_DP, value);
    transaction.setAttribute(uri, attribute, position);
  }

  protected void cleanup(@NotNull AttributesTransaction transaction) {
    boolean hasLeft = hasLeft(transaction);
    boolean hasRight = hasRight(transaction);
    boolean hasTop = hasTop(transaction);
    boolean hasBottom = hasBottom(transaction);
    if (!hasLeft) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, null);
//      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, null); // TODO: handles RTL correctly
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    if (!hasRight) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, null);
//      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, null); // TODO: handles RTL correctly
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    if (!hasLeft && !hasRight) {
      if (transaction.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X) == null) {
        setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, myComponent.getOffsetParentX());
      }
    } else {
      transaction.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
    }
    if (!hasTop) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, null);
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    if (!hasBottom) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, null);
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
    }
    if (!hasTop && !hasBottom) {
      if (transaction.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y) == null) {
        setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, myComponent.getOffsetParentY());
      }
    } else {
      transaction.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
    }
  }

  protected void clearAttributes(String uri, ArrayList<String> attributes, AttributesTransaction transaction) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      transaction.setAttribute(uri, attribute, null);
    }
  }
}
