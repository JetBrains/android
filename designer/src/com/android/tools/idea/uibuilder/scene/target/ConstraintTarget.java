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
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Implements common checks on attributes of a ConstraintLayout child
 */
public abstract class ConstraintTarget extends BaseTarget {
  protected static final HashMap<String, String> ourReciprocalAttributes;
  protected static final HashMap<String, String> ourMapMarginAttributes;
  protected static final ArrayList<String> ourLeftAttributes;
  protected static final ArrayList<String> ourTopAttributes;
  protected static final ArrayList<String> ourRightAttributes;
  protected static final ArrayList<String> ourBottomAttributes;
  protected static final ArrayList<String> ourMarginAttributes;

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

    ourMapMarginAttributes = new HashMap<>();
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
    ourMapMarginAttributes.put(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);

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

    ourMarginAttributes = new ArrayList<>();
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_LEFT);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_START);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_END);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    ourMarginAttributes.add(SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
  }

  protected boolean myIsInHorizontalChain = false;
  protected boolean myIsInVerticalChain = false;
  protected SceneComponent myHorizontalChainHead;
  protected SceneComponent myVerticalChainHead;

  protected boolean hasAttributes(@NotNull NlComponent component, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = attributes.get(i);
      if (component.getLiveAttribute(uri, attribute) != null) {
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

  protected String getConnectionId(@NotNull NlComponent component, String uri, ArrayList<String> attributes) {
    int count = attributes.size();
    for (int i = 0; i < count; i++) {
      String attribute = component.getLiveAttribute(uri, attributes.get(i));
      if (attribute != null) {
        return NlComponent.extractId(attribute);
      }
    }
    return null;
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

  protected void setDpAttribute(String uri, String attribute, AttributesTransaction transaction, int value) {
    String position = String.format(SdkConstants.VALUE_N_DP, value);
    transaction.setAttribute(uri, attribute, position);
  }

  protected void cleanup(@NotNull AttributesTransaction transaction) {
    boolean hasLeft = hasLeft(transaction);
    boolean hasRight = hasRight(transaction);
    boolean hasTop = hasTop(transaction);
    boolean hasBottom = hasBottom(transaction);
    boolean hasBaseline = transaction.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;

    if (!hasLeft) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, null);
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_START, null); // TODO: handles RTL correctly
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    if (!hasRight) {
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, null);
      transaction.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_END, null); // TODO: handles RTL correctly
      transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    }
    if (!hasLeft && !hasRight) {
      if (transaction.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X) == null) {
        setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, myComponent.getOffsetParentX());
      }
    }
    else {
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
    if (!hasTop && !hasBottom && !hasBaseline) {
      if (transaction.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y) == null) {
        setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, myComponent.getOffsetParentY());
      }
    }
    else {
      transaction.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, null);
    }
    if (!myComponent.allowsFixedPosition()) {
      transaction.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, null);
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

  protected void clearAllAttributes(AttributesTransaction transaction) {
    clearAttributes(SdkConstants.SHERPA_URI, ourLeftAttributes, transaction);
    clearAttributes(SdkConstants.SHERPA_URI, ourTopAttributes, transaction);
    clearAttributes(SdkConstants.SHERPA_URI, ourRightAttributes, transaction);
    clearAttributes(SdkConstants.SHERPA_URI, ourBottomAttributes, transaction);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, null);
    clearAttributes(SdkConstants.ANDROID_URI, ourMarginAttributes, transaction);
    setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, myComponent.getOffsetParentX());
    setDpAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, myComponent.getOffsetParentY());
  }

  protected boolean checkIsInChain() {
    myHorizontalChainHead = null;
    myVerticalChainHead = null;
    myIsInHorizontalChain = false;
    myIsInVerticalChain = false;
    if (isInChain(ourRightAttributes, ourLeftAttributes)) {
      myIsInHorizontalChain = true;
    }
    else if (isInChain(ourLeftAttributes, ourRightAttributes)) {
      myIsInHorizontalChain = true;
    }
    if (myIsInHorizontalChain) {
      myHorizontalChainHead = findChainHead(myComponent, ourLeftAttributes, ourRightAttributes);
    }

    if (isInChain(ourBottomAttributes, ourTopAttributes)) {
      myIsInVerticalChain = true;
    }
    else if (isInChain(ourTopAttributes, ourBottomAttributes)) {
      myIsInVerticalChain = true;
    }
    if (myIsInVerticalChain) {
      myVerticalChainHead = findChainHead(myComponent, ourTopAttributes, ourBottomAttributes);
    }
    return myIsInHorizontalChain || myIsInVerticalChain;
  }

  private SceneComponent findChainHead(SceneComponent component, ArrayList<String> sideA, ArrayList<String> sideB) {
    while (true) {
      NlComponent nlComponent = component.getNlComponent();
      String attributeA = getConnectionId(nlComponent, SdkConstants.SHERPA_URI, sideA);
      if (attributeA == null) {
        return component;
      }
      SceneComponent target = myComponent.getScene().getSceneComponent(attributeA);
      if (target == null) {
        return component;
      }
      String attributeB = getConnectionId(target.getNlComponent(), SdkConstants.SHERPA_URI, sideB);
      if (attributeB == null) {
        return component;
      }
      if (attributeB.equalsIgnoreCase(nlComponent.getId())) {
        component = target;
      }
      else {
        return component;
      }
    }
  }

  private boolean isInChain(ArrayList<String> sideA, ArrayList<String> sideB) {
    String attributeA = getConnectionId(myComponent.getNlComponent(), SdkConstants.SHERPA_URI, sideA);
    if (attributeA != null) {
      SceneComponent target = myComponent.getScene().getSceneComponent(attributeA);
      if (target != null) {
        String attributeB = getConnectionId(target.getNlComponent(), SdkConstants.SHERPA_URI, sideB);
        if (attributeB != null) {
          if (attributeB.equalsIgnoreCase(myComponent.getNlComponent().getId())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  protected void cycleChainStyle(SceneComponent chainHeadComponent, String orientationStyle) {
    NlComponent chainHead = chainHeadComponent.getNlComponent();
    String chainStyle = chainHead.getLiveAttribute(SdkConstants.SHERPA_URI, orientationStyle);
    if (chainStyle != null) {
      if (chainStyle.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD)) {
        chainStyle = SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD_INSIDE;
      }
      else if (chainStyle.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD_INSIDE)) {
        chainStyle = SdkConstants.ATTR_LAYOUT_CHAIN_PACKED;
      }
      else if (chainStyle.equalsIgnoreCase(SdkConstants.ATTR_LAYOUT_CHAIN_PACKED)) {
        chainStyle = SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD;
      }
    }
    else {
      chainStyle = SdkConstants.ATTR_LAYOUT_CHAIN_SPREAD;
    }
    AttributesTransaction transaction = chainHead.startAttributeTransaction();
    transaction.setAttribute(SdkConstants.SHERPA_URI, orientationStyle, chainStyle);
    transaction.apply();

    NlModel nlModel = chainHead.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Cycle chain style";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        transaction.commit();
      }
    };
    action.execute();

    myComponent.getScene().needsRebuildList();
  }
}
