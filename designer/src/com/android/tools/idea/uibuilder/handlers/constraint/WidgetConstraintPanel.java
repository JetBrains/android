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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.SdkConstants;
import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.BlueprintColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.model.ConstraintAnchor;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

/**
 * UI component for Constraint Inspector
 */
public class WidgetConstraintPanel extends AdtSecondaryPanel implements CustomPanel {
  private static final String HORIZONTAL_TOOL_TIP_TEXT = "Horizontal Bias";
  private static final String VERTICAL_TOOL_TIP_TEXT = "Vertical Bias";
  private static final Color mSliderColor = new JBColor(0xC9C9C9, 0x242627);
  private static final JBDimension PANEL_DIMENSION = JBUI.size(200, 216);
  @NotNull private final SingleWidgetView mMain;
  private final JSlider mVerticalSlider = new JSlider(SwingConstants.VERTICAL);
  private final JSlider mHorizontalSlider = new JSlider(SwingConstants.HORIZONTAL);
  private boolean mConfiguringUI = false;
  @Nullable NlComponent mComponent;
  private static final int UNCONNECTED = -1;
  private ComponentModification myModification;

  private final static int SLIDER_DEFAULT = 50;
  public final static String VERTICAL_BIAS_SLIDER = "verticalBiasSlider";
  public final static String HORIZONTAL_BIAS_SLIDER = "horizontalBiasSlider";
  private final static int DELAY_BEFORE_COMMIT = 400; // ms
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

  /**
   * When true, updates to the panel are ignored and won't update the widget.
   * This is usually set when the panel is being updated from the widget so there is no need to
   * feed the changes back to the widget.
   */
  @NotNull private final ChangeListener myChangeLiveListener = e -> configureUI();

  public void setAspect(String aspect) {
    setSherpaAttribute(SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO, aspect);
  }

  static class InspectorColorSet extends BlueprintColorSet {
    InspectorColorSet() {
      mDrawBackground = false;
      mDrawWidgetInfos = true;
      mInspectorBackgroundColor = StudioColorsKt.getSecondaryPanelBackground();
      mInspectorFillColor = StudioColorsKt.getSecondaryPanelBackground();
      mInspectorHighlightsStrokeColor = new JBColor(0xB0B0B0, 0x6F7171);
      mInspectorStrokeColor = new JBColor(0x8A8A8A, 0x808080);
      mInspectorConstraintColor = new JBColor(0x4481d8, 0x4880c8);
    }
  }

  public WidgetConstraintPanel(@NotNull List<NlComponent> components) {
    super(new GridBagLayout());
    setBorder(JBUI.Borders.emptyTop(WidgetSliderUI.THUMB_SIZE.height));
    ColorSet colorSet = new InspectorColorSet();
    mMain = new SingleWidgetView(this, colorSet);
    setPreferredSize(PANEL_DIMENSION);
    mVerticalSlider.setMajorTickSpacing(50);
    mHorizontalSlider.setMajorTickSpacing(50);
    mHorizontalSlider.setToolTipText(HORIZONTAL_TOOL_TIP_TEXT);
    mVerticalSlider.setToolTipText(VERTICAL_TOOL_TIP_TEXT);
    mVerticalSlider.setName(VERTICAL_BIAS_SLIDER);
    mHorizontalSlider.setName(HORIZONTAL_BIAS_SLIDER);
    updateComponent(components.isEmpty() ? null : components.get(0));

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.fill = GridBagConstraints.BOTH;
    add(mVerticalSlider, gbc);
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.gridx = 1;

    add(mMain, gbc);
    gbc.weightx = 0;
    gbc.weighty = 0;
    gbc.gridy = 1;

    add(mHorizontalSlider, gbc);
    mVerticalSlider.setUI(new WidgetSliderUI(mVerticalSlider, colorSet));
    mHorizontalSlider.setUI(new WidgetSliderUI(mHorizontalSlider, colorSet));
    mHorizontalSlider.setBackground(StudioColorsKt.getSecondaryPanelBackground());
    mVerticalSlider.setBackground(StudioColorsKt.getSecondaryPanelBackground());
    mHorizontalSlider.setForeground(mSliderColor);
    mVerticalSlider.setForeground(mSliderColor);
    mHorizontalSlider.addChangeListener(e -> setHorizontalBias());
    mVerticalSlider.addChangeListener(e -> setVerticalBias());
    mHorizontalSlider.addMouseListener(mDoubleClickListener);
    mVerticalSlider.addMouseListener(mDoubleClickListener);
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return this;
  }

  @Override
  public void useComponent(@Nullable NlComponent component) {
    updateComponent(component);
  }

  @Override
  public void refresh() {
    configureUI();
  }

  // Reset mouse on double click
  @NotNull MouseListener mDoubleClickListener = new MouseAdapter() {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2) {
        ((JSlider)e.getSource()).setValue(SLIDER_DEFAULT);
      }
    }
  };

  /*-----------------------------------------------------------------------*/
  // code for getting values from ConstraintWidget to UI
  /*-----------------------------------------------------------------------*/

  private static final int CONNECTION_LEFT = 0;
  private static final int CONNECTION_RIGHT = 1;
  private static final int CONNECTION_TOP = 2;
  private static final int CONNECTION_BOTTOM = 3;
  private static final int CONNECTION_BASELINE = 4;

  private static final String[][] ourConstraintString_ltr = {{
    SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF
  }, {
    SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF
  }, {
    SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
    SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF
  }, {
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF
  }, {
    SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
  }};

  private static final String[][] ourConstraintString_rtl = {{
    SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF
  }, {
    SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
  }, {SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
    SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF
  }, {
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF
  }, {
    SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF
  }};

  private static final String[][] ourMarginString_ltr = {
    {SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_START},
    {SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN_END},
    {SdkConstants.ATTR_LAYOUT_MARGIN_TOP},
    {SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  private static final String[][] ourMarginString_rtl = {
    {SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_END},
    {SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN_START},
    {SdkConstants.ATTR_LAYOUT_MARGIN_TOP},
    {SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  private static final String[][] ourDeleteAttributes = {
    {
      SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
      SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
      SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
      SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
      SdkConstants.ATTR_LAYOUT_MARGIN_START,
      SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS},
    {
      SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
      SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
      SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
      SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
      SdkConstants.ATTR_LAYOUT_MARGIN_END,
      SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS},
    {SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
      SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
      SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS},
    {SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
      SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
      SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS},
    {SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF}
  };

  private static final String[][] ourDeleteNamespace = {
    {SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.ANDROID_URI,
      SdkConstants.ANDROID_URI,
      SdkConstants.SHERPA_URI},
    {SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.ANDROID_URI,
      SdkConstants.ANDROID_URI,
      SdkConstants.SHERPA_URI},
    {SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.ANDROID_URI,
      SdkConstants.SHERPA_URI},
    {SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.ANDROID_URI,
      SdkConstants.SHERPA_URI},
    {SdkConstants.SHERPA_URI}
  };

  private int getMargin(int type) {
    if (mComponent == null) {
      return 0;
    }
    boolean rtl = ConstraintUtilities.isInRTL(mComponent);

    String[][] marginsAttr = rtl ? ourMarginString_rtl : ourMarginString_ltr;
    String marginString = mComponent.getLiveAttribute(SdkConstants.NS_RESOURCES, marginsAttr[type][0]);
    for (int i = 1; marginString == null && marginsAttr[type].length > i; i++) {
      marginString = mComponent.getLiveAttribute(SdkConstants.NS_RESOURCES, marginsAttr[type][i]);
    }

    int margin = 0;
    if (marginString != null) {
      margin = ConstraintUtilities.getDpValue(mComponent, marginString);
    }
    String[][] ourConstraintString = rtl ? ourConstraintString_rtl : ourConstraintString_ltr;
    String connection = mComponent.getLiveAttribute(SdkConstants.SHERPA_URI, ourConstraintString[type][0]);
    for (int i = 1; connection == null && i < ourConstraintString[type].length; i++) {
      connection = mComponent.getLiveAttribute(SdkConstants.SHERPA_URI, ourConstraintString[type][i]);
    }
    if (connection == null) {
      margin = -1;
    }
    return margin;
  }

  /**
   * This loads the parameters form component the ConstraintWidget
   */
  private void configureUI() {
    if (mComponent == null) {
      return;
    }
    mConfiguringUI = true;

    final String sherpaNamespace = SdkConstants.SHERPA_URI;
    int top = getMargin(CONNECTION_TOP);
    int left = getMargin(CONNECTION_LEFT);
    int right = getMargin(CONNECTION_RIGHT);
    int bottom = getMargin(CONNECTION_BOTTOM);

    String ratioString = mComponent.getLiveAttribute(sherpaNamespace, SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO);
    String horizontalBias;
    String verticalBias;

    boolean baseline = hasBaseline();

    boolean showVerticalSlider = bottom != UNCONNECTED && top != UNCONNECTED;
    boolean showHorizontalSlider = left != UNCONNECTED && right != UNCONNECTED;

    if (showHorizontalSlider) {
      NlComponent source = findInHorizontalChain(mComponent);
      if (source == null) {
        source = mComponent;
      }
      horizontalBias = source.getLiveAttribute(sherpaNamespace, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS);

      float bias = parseFloat(horizontalBias);
      mHorizontalSlider.setValue((int)(bias * 100));
    }

    if (showVerticalSlider) {
      NlComponent source = findInVerticalChain(mComponent);
      if (source == null) {
        source = mComponent;
      }
      verticalBias = source.getLiveAttribute(sherpaNamespace, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);

      float bias = parseFloat(verticalBias);
      mVerticalSlider.setValue(100 - (int)(bias * 100));
    }

    mVerticalSlider.setEnabled(showVerticalSlider);
    mHorizontalSlider.setEnabled(showHorizontalSlider);
    mHorizontalSlider.invalidate();
    mVerticalSlider.invalidate();
    mVerticalSlider.setToolTipText(showVerticalSlider ? VERTICAL_TOOL_TIP_TEXT : null);
    mHorizontalSlider.setToolTipText(showHorizontalSlider ? HORIZONTAL_TOOL_TIP_TEXT : null);

    int widthValue = convertFromNL(SdkConstants.ATTR_LAYOUT_WIDTH);
    int heightValue = convertFromNL(SdkConstants.ATTR_LAYOUT_HEIGHT);
    mMain.configureUi(bottom, top, left, right, baseline, widthValue, heightValue, ratioString);
    mConfiguringUI = false;
  }

  private static float parseFloat(@Nullable String string) {
    if (string != null && !string.isEmpty()) {
      try {
        return Float.parseFloat(string);
      }
      catch (NumberFormatException ignore) {
      }
    }
    return 0.5f;
  }


  private static int getDimension(@NotNull NlComponent component, @NotNull String attribute) {
    String v = component.getLiveAttribute(SdkConstants.ANDROID_URI, attribute);
    if (SdkConstants.VALUE_WRAP_CONTENT.equalsIgnoreCase(v)) {
      return -1;
    }
    return ConstraintUtilities.getDpValue(component, v);
  }

  private void setDimension(@Nullable NlComponent component, @Nullable String attribute, int currentValue) {
    if (component == null) {
      return;
    }
    attribute = ConstraintComponentUtilities.mapStartEndStrings(component, attribute);
    String marginString = component.getLiveAttribute(SdkConstants.ANDROID_URI, attribute);
    int marginValue = -1;
    if (marginString != null) {
      marginValue = ConstraintComponentUtilities.getDpValue(component, component.getLiveAttribute(SdkConstants.ANDROID_URI, attribute));
    }
    if (marginValue != -1 && marginValue == currentValue) {
      setAttribute(SdkConstants.ANDROID_URI, attribute, marginString);
    }
    else {
      String marginY = String.format(SdkConstants.VALUE_N_DP, currentValue);
      setAttribute(SdkConstants.ANDROID_URI, attribute, marginY);
    }
  }

  private void setAndroidAttribute(@NotNull String attribute, @Nullable String value) {
    setAttribute(SdkConstants.ANDROID_URI, attribute, value);
  }

  private void setSherpaAttribute(@NotNull String attribute, @Nullable String value) {
    setAttribute(SdkConstants.SHERPA_URI, attribute, value);
  }

  private void setAttribute(@NotNull String nameSpace, @NotNull String attribute, @Nullable String value) {
    if (mComponent != null) {
      setAttribute(mComponent, nameSpace, attribute, value);
    }
  }

  private void setAttribute(@NotNull NlComponent component, @NotNull String nameSpace, @NotNull String attribute, @Nullable String value) {
    if (mConfiguringUI) {
      return;
    }
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

  private void removeAttribute(int type) {
    if (mComponent == null) {
      return;
    }
    String label = "Constraint Disconnected";
    String[] attribute = ourDeleteAttributes[type];
    String[] namespace = ourDeleteNamespace[type];

    ComponentModification modification = new ComponentModification(mComponent, label);
    for (int i = 0; i < attribute.length; i++) {
      modification.setAttribute(namespace[i], attribute[i], null);
    }

    ConstraintComponentUtilities.ensureHorizontalPosition(mComponent, modification);
    ConstraintComponentUtilities.ensureVerticalPosition(mComponent, modification);

    modification.apply();
    modification.commit();
  }

  public static final int UNKNOWN = -1;
  public static final int HORIZONTAL = 0;
  public static final int VERTICAL = 1;

  /**
   * Convert Any to SingleWidgetView flags
   *
   * @return
   */
  private int convertFromNL(@NotNull String attribute) {
    if (mComponent == null) {
      return SingleWidgetView.WRAP_CONTENT;
    }
    int dimen = getDimension(mComponent, attribute);
    switch (dimen) {
      default:
        return SingleWidgetView.FIXED;
      case -1:
        return SingleWidgetView.WRAP_CONTENT;
      case 0:
        return SingleWidgetView.MATCH_CONSTRAINT;
    }
  }

  /**
   * Returns true if mWidget has a baseline
   *
   * @return
   */
  private boolean hasBaseline() {
    return mComponent != null &&
           mComponent.getLiveAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF) != null;
  }

  private void updateComponent(@Nullable NlComponent component) {
    if (mComponent != null) {
      mComponent.removeLiveChangeListener(myChangeLiveListener);
    }
    mComponent = isApplicable(component) ? component : null;
    if (mComponent != null) {
      mComponent.addLiveChangeListener(myChangeLiveListener);
      configureUI();
    }
  }

  private static boolean isApplicable(@Nullable NlComponent component) {
    if (component == null) {
      return false;
    }
    NlComponent parent = component.getParent();
    return parent != null && NlComponentHelperKt.isOrHasSuperclass(parent, CONSTRAINT_LAYOUT);
  }

  /*-----------------------------------------------------------------------*/
  // values from ui to widget & NL component
  /*-----------------------------------------------------------------------*/

  private void killConstraint(ConstraintAnchor.Type type) {
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

  private void setHorizontalBias() {
    if (mComponent == null) {
      return;
    }
    int biasVal = mHorizontalSlider.getValue();
    float bias = (biasVal / 100f);
    String biasString = (biasVal == 50) ? null : Float.toString(bias);
    NlComponent chain = findInHorizontalChain(mComponent);
    if (chain != null && chain != mComponent) {
      setAttribute(chain, SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, biasString);
    }
    else {
      setSherpaAttribute(SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, biasString);
    }
  }

  @Nullable
  private static NlComponent findInHorizontalChain(@NotNull NlComponent component) {
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
  private static NlComponent findInVerticalChain(@NotNull NlComponent component) {
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

  private void setVerticalBias() {
    if (mComponent == null) {
      return;
    }
    int biasVal = mVerticalSlider.getValue();
    float bias = 1f - (biasVal / 100f);
    String biasString = (biasVal == 50) ? null : Float.toString(bias);
    NlComponent chain = findInVerticalChain(mComponent);
    if (chain != null && chain != mComponent) {
      setAttribute(chain, SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, biasString);
    }
    else {
      setSherpaAttribute(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, biasString);
    }
  }

  public void setTopMargin(int margin) {
    setDimension(mComponent, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, margin);
  }

  public void setLeftMargin(int margin) {
    setDimension(mComponent, SdkConstants.ATTR_LAYOUT_MARGIN_START, margin);
    setDimension(mComponent, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, margin);
  }

  public void setRightMargin(int margin) {
    setDimension(mComponent, SdkConstants.ATTR_LAYOUT_MARGIN_END, margin);
    setDimension(mComponent, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, margin);
  }

  public void setBottomMargin(int margin) {
    setDimension(mComponent, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, margin);
  }

  public void killTopConstraint() {
    killConstraint(ConstraintAnchor.Type.TOP);
  }

  public void killLeftConstraint() {
    killConstraint(ConstraintAnchor.Type.LEFT);
  }

  public void killRightConstraint() {
    killConstraint(ConstraintAnchor.Type.RIGHT);
  }

  public void killBottomConstraint() {
    killConstraint(ConstraintAnchor.Type.BOTTOM);
  }

  public void killBaselineConstraint() {
    killConstraint(ConstraintAnchor.Type.BASELINE);
  }

  public void setHorizontalConstraint(int horizontalConstraint) {
    if (mComponent == null) {
      return;
    }
    String width = mComponent.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
    assert width != null;

    if (width.endsWith("dp") && !width.equals("0dp")) {
      mComponent.putClientProperty(SdkConstants.ATTR_LAYOUT_WIDTH, width);
    }
    switch (horizontalConstraint) {
      case SingleWidgetView.MATCH_CONSTRAINT:
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_ZERO_DP);
        break;
      case SingleWidgetView.FIXED:
        String oldValue = (String)mComponent.getClientProperty(SdkConstants.ATTR_LAYOUT_WIDTH);
        if (oldValue == null) {
          oldValue = Coordinates.pxToDp(mComponent.getModel(), NlComponentHelperKt.getW(mComponent)) + "dp";
        }
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, oldValue);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_WRAP_CONTENT);
        break;
    }
  }

  public void setVerticalConstraint(int verticalConstraint) {
    if (mComponent == null) {
      return;
    }
    String height = mComponent.getLiveAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
    assert height != null;

    if (height.endsWith("dp") && !height.equals("0dp")) {
      mComponent.putClientProperty(SdkConstants.ATTR_LAYOUT_HEIGHT, height);
    }
    switch (verticalConstraint) {
      case SingleWidgetView.MATCH_CONSTRAINT:
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_ZERO_DP);
        break;
      case SingleWidgetView.FIXED:
        String oldValue = (String)mComponent.getClientProperty(SdkConstants.ATTR_LAYOUT_HEIGHT);
        if (oldValue == null) {
          oldValue = Coordinates.pxToDp(mComponent.getModel(), NlComponentHelperKt.getH(mComponent)) + "dp";
        }
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, oldValue);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_WRAP_CONTENT);
        break;
    }
  }

  /*-----------------------------------------------------------------------*/
  //Look and Feel for the sliders
  /*-----------------------------------------------------------------------*/

  static class WidgetSliderUI extends BasicSliderUI {
    private static final JBDimension THUMB_SIZE = JBUI.size(18);
    private static final int TRACK_THICKNESS = JBUI.scale(5);
    private static final int ARC_SIZE = JBUI.scale(5);
    @NotNull private static Font SMALL_FONT = new Font("Helvetica", Font.PLAIN, JBUI.scaleFontSize(10));
    private ColorSet mColorSet;

    WidgetSliderUI(JSlider s, ColorSet colorSet) {
      super(s);
      mColorSet = colorSet;
    }

    @NotNull
    @Override
    protected Dimension getThumbSize() {
      return THUMB_SIZE;
    }

    @Override
    public void paintTrack(Graphics g) {
      if (slider.isEnabled()) {
        g.setColor(slider.getForeground());
        int trackThickness = TRACK_THICKNESS;
        if (slider.getOrientation() == SwingConstants.VERTICAL) {
          int offset = trackRect.width / 2 - trackThickness / 2;
          g.fillRoundRect(trackRect.x + offset, trackRect.y, trackThickness, trackRect.height, ARC_SIZE, ARC_SIZE);
        }
        else {
          int offset = trackRect.height / 2 - trackThickness / 2;
          g.fillRoundRect(trackRect.x, trackRect.y + offset, trackRect.width, trackThickness, ARC_SIZE, ARC_SIZE);
        }
      }
    }

    @Override
    protected Color getShadowColor() {
      return mColorSet.getInspectorStrokeColor();
    }

    @Override
    protected Color getHighlightColor() {
      return mColorSet.getInspectorStrokeColor();
    }

    @NotNull
    @Override
    protected Color getFocusColor() {
      return JBColor.BLACK;
    }

    @Override
    public void paintThumb(Graphics g) {
      String percentText;
      if (slider.getOrientation() == SwingConstants.VERTICAL) {
        percentText = Integer.toString(100 - slider.getValue());
      }
      else {
        percentText = Integer.toString(slider.getValue());
      }
      if (!slider.isEnabled()) {
        return;
      }
      g.setColor(mColorSet.getInspectorFillColor().brighter());
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.fillRoundRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height, thumbRect.width,
                      thumbRect.height);
      g.setColor(StudioColorsKt.getBorder());
      g.drawRoundRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height, thumbRect.width,
                      thumbRect.height);
      g.setColor(mColorSet.getInspectorStrokeColor());
      int x = thumbRect.x + thumbRect.width / 2;
      int y = thumbRect.y + thumbRect.height / 2;
      g.setFont(SMALL_FONT);
      FontMetrics fm = g.getFontMetrics();
      Rectangle2D bounds = fm.getStringBounds(percentText, g);
      double tw = bounds.getWidth();

      g.setColor(mColorSet.getInspectorStrokeColor());
      g.drawString(percentText, (int)(x - tw / 2), (y + fm.getAscent() / 2));
    }
  }
}
