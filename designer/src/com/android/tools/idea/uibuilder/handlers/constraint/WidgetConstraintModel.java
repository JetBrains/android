/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.handlers.constraint.model.ConstraintAnchor;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;

import static com.android.SdkConstants.*;

/**
 * Handles model change coming from interaction on the {@link WidgetConstraintPanel}
 */
public class WidgetConstraintModel {

  private static final String[][] ourConstraintString_ltr = {{
    ATTR_LAYOUT_START_TO_START_OF,
    ATTR_LAYOUT_START_TO_END_OF,
    ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    ATTR_LAYOUT_LEFT_TO_RIGHT_OF
  }, {
    ATTR_LAYOUT_END_TO_START_OF,
    ATTR_LAYOUT_END_TO_END_OF,
    ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    ATTR_LAYOUT_RIGHT_TO_RIGHT_OF
  }, {
    ATTR_LAYOUT_TOP_TO_TOP_OF,
    ATTR_LAYOUT_TOP_TO_BOTTOM_OF
  }, {
    ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF
  }, {
    ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
  }};

  private static final String[][] ourConstraintString_rtl = {{
    ATTR_LAYOUT_END_TO_START_OF,
    ATTR_LAYOUT_END_TO_END_OF,
    ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    ATTR_LAYOUT_LEFT_TO_RIGHT_OF
  }, {
    ATTR_LAYOUT_START_TO_START_OF,
    ATTR_LAYOUT_START_TO_END_OF,
    ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
  }, {ATTR_LAYOUT_TOP_TO_TOP_OF,
    ATTR_LAYOUT_TOP_TO_BOTTOM_OF
  }, {
    ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF
  }, {
    ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
  }};

  private static final String[][] ourMarginString_ltr = {
    {ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_START},
    {ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_END},
    {ATTR_LAYOUT_MARGIN_TOP},
    {ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  private static final String[][] ourMarginString_rtl = {
    {ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_END},
    {ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_START},
    {ATTR_LAYOUT_MARGIN_TOP},
    {ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  private static final String[][] ourDeleteAttributes = {
    {
      ATTR_LAYOUT_START_TO_START_OF,
      ATTR_LAYOUT_START_TO_END_OF,
      ATTR_LAYOUT_LEFT_TO_LEFT_OF,
      ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
      ATTR_LAYOUT_MARGIN_LEFT,
      ATTR_LAYOUT_MARGIN_START,
      ATTR_LAYOUT_HORIZONTAL_BIAS},
    {
      ATTR_LAYOUT_END_TO_END_OF,
      ATTR_LAYOUT_END_TO_START_OF,
      ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
      ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
      ATTR_LAYOUT_MARGIN_RIGHT,
      ATTR_LAYOUT_MARGIN_END,
      ATTR_LAYOUT_HORIZONTAL_BIAS},
    {ATTR_LAYOUT_TOP_TO_TOP_OF,
      ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
      ATTR_LAYOUT_MARGIN_TOP,
      ATTR_LAYOUT_VERTICAL_BIAS},
    {ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
      ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
      ATTR_LAYOUT_MARGIN_BOTTOM,
      ATTR_LAYOUT_VERTICAL_BIAS},
    {ATTR_LAYOUT_BASELINE_TO_BASELINE_OF}
  };

  private static final String[][] ourDeleteNamespace = {
    {SHERPA_URI,
      SHERPA_URI,
      SHERPA_URI,
      SHERPA_URI,
      ANDROID_URI,
      ANDROID_URI,
      SHERPA_URI},
    {SHERPA_URI,
      SHERPA_URI,
      SHERPA_URI,
      SHERPA_URI,
      ANDROID_URI,
      ANDROID_URI,
      SHERPA_URI},
    {SHERPA_URI,
      SHERPA_URI,
      ANDROID_URI,
      SHERPA_URI},
    {SHERPA_URI,
      SHERPA_URI,
      ANDROID_URI,
      SHERPA_URI},
    {SHERPA_URI}
  };

  public static final int CONNECTION_LEFT = 0;
  public static final int CONNECTION_RIGHT = 1;
  public static final int CONNECTION_TOP = 2;
  public static final int CONNECTION_BOTTOM = 3;
  public static final int CONNECTION_BASELINE = 4;

  private final static int DELAY_BEFORE_COMMIT = 400; // ms

  private Runnable myUpdateCallback;
  @Nullable private NlComponent myComponent;

  @NotNull private final ChangeListener myChangeLiveListener = e -> {
    if (myUpdateCallback != null) myUpdateCallback.run();
  };

  @Nullable private ComponentModification myModification;

  @NotNull private final Timer myTimer = new Timer(DELAY_BEFORE_COMMIT, (c) -> {
    if (myModification != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myModification.commit();
          myModification = null;
        }
      });
    }
  });

  public WidgetConstraintModel(@NotNull Runnable modelUpdateCallback) {
    myUpdateCallback = modelUpdateCallback;
  }

  public int getMargin(int type) {
    if (myComponent == null) {
      return 0;
    }
    boolean rtl = ConstraintUtilities.isInRTL(myComponent);

    String[][] marginsAttr = rtl ? ourMarginString_rtl : ourMarginString_ltr;
    String marginString = myComponent.getLiveAttribute(NS_RESOURCES, marginsAttr[type][0]);
    for (int i = 1; marginString == null && marginsAttr[type].length > i; i++) {
      marginString = myComponent.getLiveAttribute(NS_RESOURCES, marginsAttr[type][i]);
    }

    int margin = 0;
    if (marginString != null) {
      margin = ConstraintUtilities.getDpValue(myComponent, marginString);
    }
    String[][] ourConstraintString = rtl ? ourConstraintString_rtl : ourConstraintString_ltr;
    String connection = myComponent.getLiveAttribute(SHERPA_URI, ourConstraintString[type][0]);
    for (int i = 1; connection == null && i < ourConstraintString[type].length; i++) {
      connection = myComponent.getLiveAttribute(SHERPA_URI, ourConstraintString[type][i]);
    }
    if (connection == null) {
      margin = -1;
    }
    return margin;
  }

  /**
   * Returns true if the current component has a baseline constraint
   */
  public boolean hasBaseline() {
    return myComponent != null &&
           myComponent.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
  }

  public void setComponent(@Nullable NlComponent component) {
    if (myComponent != null) {
      myComponent.removeLiveChangeListener(myChangeLiveListener);
    }
    myComponent = isApplicable(component) ? component : null;
    if (myComponent != null) {
      myComponent.addLiveChangeListener(myChangeLiveListener);
      myUpdateCallback.run();
    }
  }

  @Nullable
  public NlComponent getComponent() {
    return myComponent;
  }

  @Nullable
  public String getRatioString() {
    return myComponent == null ? null : myComponent.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_DIMENSION_RATIO);
  }

  private static boolean isApplicable(@Nullable NlComponent component) {
    if (component == null) {
      return false;
    }
    NlComponent parent = component.getParent();
    return parent != null && NlComponentHelperKt.isOrHasSuperclass(parent, CONSTRAINT_LAYOUT);
  }

  public void killConstraint(@NotNull ConstraintAnchor.Type type) {
    switch (type) {
      case LEFT:
        removeAttribute(CONNECTION_LEFT);
        break;
      case TOP:
        removeAttribute(CONNECTION_TOP);
        break;
      case RIGHT:
        removeAttribute(CONNECTION_RIGHT);
        break;
      case BOTTOM:
        removeAttribute(CONNECTION_BOTTOM);
        break;
      case BASELINE:
        removeAttribute(CONNECTION_BASELINE);
        break;
      default:
    }
  }

  private void removeAttribute(int type) {
    if (myComponent == null) {
      return;
    }
    String label = "Constraint Disconnected";
    String[] attribute = ourDeleteAttributes[type];
    String[] namespace = ourDeleteNamespace[type];

    ComponentModification modification = new ComponentModification(myComponent, label);
    for (int i = 0; i < attribute.length; i++) {
      modification.setAttribute(namespace[i], attribute[i], null);
    }

    ConstraintComponentUtilities.ensureHorizontalPosition(myComponent, modification);
    ConstraintComponentUtilities.ensureVerticalPosition(myComponent, modification);

    modification.apply();
    modification.commit();
  }

  public void setHorizontalBias(int biasPercent) {
    if (myComponent == null) {
      return;
    }
    float bias = (biasPercent / 100f);
    String biasString = (biasPercent == 50) ? null : Float.toString(bias);
    NlComponent chain = findHorizontalChainHead(myComponent);
    if (chain != null && chain != myComponent) {
      setAttribute(chain, SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS, biasString);
    }
    else {
      setSherpaAttribute(ATTR_LAYOUT_HORIZONTAL_BIAS, biasString);
    }
  }

  public void setVerticalBias(int biasPercent) {
    if (myComponent == null) {
      return;
    }
    float bias = 1f - (biasPercent / 100f);
    String biasString = (biasPercent == 50) ? null : Float.toString(bias);
    NlComponent chain = findVerticalChainHead(myComponent);
    if (chain != null && chain != myComponent) {
      setAttribute(chain, SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS, biasString);
    }
    else {
      setSherpaAttribute(ATTR_LAYOUT_VERTICAL_BIAS, biasString);
    }
  }

  /**
   * Returns the vertical bias for the current component as a float between 0 and 1.
   * If no bias is set 0.5 is returned.
   */
  public float getVerticalBias() {
    String verticalBias = getVerticalBiasString();
    return parseBiasStringFloat(verticalBias);
  }

  public float getHorizontalBias() {
    String horizontalBias = getHorizontalBiasString();
    return parseBiasStringFloat(horizontalBias);
  }

  /**
   * Return the string value for the bias from the xml tag
   */
  @Nullable
  public String getVerticalBiasString() {
    if (myComponent == null) {
      return null;
    }
    NlComponent source = findVerticalChainHead(myComponent);
    if (source == null) {
      source = myComponent;
    }
    return source.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_VERTICAL_BIAS);
  }

  /**
   * Return the string value for the bias from the xml tag
   */
  @Nullable
  public String getHorizontalBiasString() {
    if (myComponent == null) {
      return null;
    }
    NlComponent source = findHorizontalChainHead(myComponent);
    if (source == null) {
      source = myComponent;
    }
    return source.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_HORIZONTAL_BIAS);
  }

  private static float parseBiasStringFloat(@Nullable String string) {
    if (string != null && !string.isEmpty()) {
      try {
        return Float.parseFloat(string);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return 0.5f;
  }

  private int getDimension(@NotNull String attribute) {
    if (myComponent == null) {
      return 0;
    }
    String v = myComponent.getLiveAttribute(ANDROID_URI, attribute);
    if (VALUE_WRAP_CONTENT.equalsIgnoreCase(v)) {
      return -1;
    }
    return ConstraintUtilities.getDpValue(myComponent, v);
  }

  private void setDimension(@Nullable NlComponent component, @Nullable String attribute, int currentValue) {
    if (component == null) {
      return;
    }
    attribute = ConstraintComponentUtilities.mapStartEndStrings(component, attribute);
    String marginString = component.getLiveAttribute(ANDROID_URI, attribute);
    int marginValue = -1;
    if (marginString != null) {
      marginValue = ConstraintComponentUtilities.getDpValue(component, component.getLiveAttribute(ANDROID_URI, attribute));
    }
    if (marginValue != -1 && marginValue == currentValue) {
      setAttribute(ANDROID_URI, attribute, marginString);
    }
    else {
      String marginY = String.format(VALUE_N_DP, currentValue);
      setAttribute(ANDROID_URI, attribute, marginY);
    }
  }

  private void setAndroidAttribute(@NotNull String attribute, @Nullable String value) {
    setAttribute(ANDROID_URI, attribute, value);
  }

  private void setSherpaAttribute(@NotNull String attribute, @Nullable String value) {
    setAttribute(SHERPA_URI, attribute, value);
  }

  private void setAttribute(@NotNull String nameSpace, @NotNull String attribute, @Nullable String value) {
    if (myComponent != null) {
      setAttribute(myComponent, nameSpace, attribute, value);
    }
  }

  private void setAttribute(@NotNull NlComponent component, @NotNull String nameSpace, @NotNull String attribute, @Nullable String value) {
    NlModel model = component.getModel();

    if (myModification == null || myModification.getComponent() != component) {
      myModification = new ComponentModification(component, "Change Widget");
    }
    myModification.setAttribute(nameSpace, attribute, value);
    myModification.apply();
    model.notifyLiveUpdate(false);
    myTimer.setRepeats(false);
    myTimer.restart();
  }

  /**
   * Convert Any to SingleWidgetView flags
   *
   * @return
   */
  public int convertFromNL(@NotNull String attribute) {
    if (myComponent == null) {
      return SingleWidgetView.WRAP_CONTENT;
    }
    int dimen = getDimension(attribute);
    switch (dimen) {
      default:
        return SingleWidgetView.FIXED;
      case -1:
        return SingleWidgetView.WRAP_CONTENT;
      case 0:
        return SingleWidgetView.MATCH_CONSTRAINT;
    }
  }

  @Nullable
  public static NlComponent findHorizontalChainHead(@NotNull NlComponent component) {
    if (ConstraintComponentUtilities
          .isInChain(ConstraintComponentUtilities.ourRightAttributes, ConstraintComponentUtilities.ourLeftAttributes, component)
        ||
        ConstraintComponentUtilities
          .isInChain(ConstraintComponentUtilities.ourLeftAttributes, ConstraintComponentUtilities.ourRightAttributes, component)) {
      return ConstraintComponentUtilities
        .findChainHead(component, ConstraintComponentUtilities.ourLeftAttributes, ConstraintComponentUtilities.ourRightAttributes);
    }
    if (ConstraintComponentUtilities
          .isInChain(ConstraintComponentUtilities.ourStartAttributes, ConstraintComponentUtilities.ourEndAttributes, component)
        ||
        ConstraintComponentUtilities
          .isInChain(ConstraintComponentUtilities.ourEndAttributes, ConstraintComponentUtilities.ourStartAttributes, component)) {

      return ConstraintComponentUtilities
        .findChainHead(component, ConstraintComponentUtilities.ourStartAttributes, ConstraintComponentUtilities.ourEndAttributes);
    }
    return null;
  }

  @Nullable
  public static NlComponent findVerticalChainHead(@NotNull NlComponent component) {
    if (ConstraintComponentUtilities
          .isInChain(ConstraintComponentUtilities.ourBottomAttributes, ConstraintComponentUtilities.ourTopAttributes, component)
        ||
        ConstraintComponentUtilities
          .isInChain(ConstraintComponentUtilities.ourTopAttributes, ConstraintComponentUtilities.ourBottomAttributes, component)) {
      return ConstraintComponentUtilities
        .findChainHead(component, ConstraintComponentUtilities.ourTopAttributes, ConstraintComponentUtilities.ourBottomAttributes);
    }
    return null;
  }

  public void setAspect(String aspect) {
    setSherpaAttribute(ATTR_LAYOUT_DIMENSION_RATIO, aspect);
  }

  public void setTopMargin(int margin) {
    setDimension(myComponent, ATTR_LAYOUT_MARGIN_TOP, margin);
  }

  public void setLeftMargin(int margin) {
    setDimension(myComponent, ATTR_LAYOUT_MARGIN_START, margin);
    setDimension(myComponent, ATTR_LAYOUT_MARGIN_LEFT, margin);
  }

  public void setRightMargin(int margin) {
    setDimension(myComponent, ATTR_LAYOUT_MARGIN_END, margin);
    setDimension(myComponent, ATTR_LAYOUT_MARGIN_RIGHT, margin);
  }

  public void setBottomMargin(int margin) {
    setDimension(myComponent, ATTR_LAYOUT_MARGIN_BOTTOM, margin);
  }

  public void killBaselineConstraint() {
    killConstraint(ConstraintAnchor.Type.BASELINE);
  }

  public void setHorizontalConstraint(int horizontalConstraint) {
    if (myComponent == null) {
      return;
    }
    String width = myComponent.getLiveAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH);
    assert width != null;

    if (width.endsWith("dp") && !width.equals("0dp")) {
      myComponent.putClientProperty(ATTR_LAYOUT_WIDTH, width);
    }
    switch (horizontalConstraint) {
      case SingleWidgetView.MATCH_CONSTRAINT:
        setAndroidAttribute(ATTR_LAYOUT_WIDTH, VALUE_ZERO_DP);
        break;
      case SingleWidgetView.FIXED:
        String oldValue = (String)myComponent.getClientProperty(ATTR_LAYOUT_WIDTH);
        if (oldValue == null) {
          oldValue = Coordinates.pxToDp(myComponent.getModel(), NlComponentHelperKt.getW(myComponent)) + "dp";
        }
        setAndroidAttribute(ATTR_LAYOUT_WIDTH, oldValue);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        setAndroidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
        break;
    }
  }

  public void setVerticalConstraint(int verticalConstraint) {
    if (myComponent == null) {
      return;
    }
    String height = myComponent.getLiveAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
    assert height != null;

    if (height.endsWith("dp") && !height.equals("0dp")) {
      myComponent.putClientProperty(ATTR_LAYOUT_HEIGHT, height);
    }
    switch (verticalConstraint) {
      case SingleWidgetView.MATCH_CONSTRAINT:
        setAndroidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_ZERO_DP);
        break;
      case SingleWidgetView.FIXED:
        String oldValue = (String)myComponent.getClientProperty(ATTR_LAYOUT_HEIGHT);
        if (oldValue == null) {
          oldValue = Coordinates.pxToDp(myComponent.getModel(), NlComponentHelperKt.getH(myComponent)) + "dp";
        }
        setAndroidAttribute(ATTR_LAYOUT_HEIGHT, oldValue);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        setAndroidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
        break;
    }
  }
}
