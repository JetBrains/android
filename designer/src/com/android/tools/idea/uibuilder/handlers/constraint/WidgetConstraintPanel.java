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

import android.support.constraint.solver.widgets.ConstraintAnchor;
import com.android.SdkConstants;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

/**
 * UI component for Constraint Inspector
 */
public class WidgetConstraintPanel extends JPanel {
  private static final String HORIZONTAL_TOOL_TIP_TEXT = "Horizontal Bias";
  private static final String VERTICAL_TOOL_TIP_TEXT = "Vertical Bias";
  private final SingleWidgetView mMain;
  private final JSlider mVerticalSlider = new JSlider(SwingConstants.VERTICAL);
  private final JSlider mHorizontalSlider = new JSlider(SwingConstants.HORIZONTAL);
  private boolean mConfiguringUI = false;
  NlComponent mComponent;
  private static final int UNCONNECTED = -1;
  private Runnable myWriteAction;
  private final static int SLIDER_DEFAULT = 50;
  public final static String VERTICAL_BIAS_SLIDER = "verticalBiasSlider";
  public final static String HORIZONTAL_BIAS_SLIDER = "horizontalBiasSlider";
  private final static int DELAY_BEFORE_COMMIT = 400; // ms
  private Timer myTimer = new Timer(DELAY_BEFORE_COMMIT, (c) -> {
    if (myWriteAction != null) {
      ApplicationManager.getApplication().invokeLater(myWriteAction);
    }
  });

  /**
   * When true, updates to the panel are ignored and won't update the widget.
   * This is usually set when the panel is being updated from the widget so there is no need to
   * feed the changes back to the widget.
   */
  private ChangeListener myChangeLiveListener = e -> configureUI();

  public void setProperty(NlProperty property) {
    updateComponents(property.getComponents());
  }

  public void setAspect(String aspect) {
    setSherpaAttribute(SdkConstants.ATTR_LAYOUT_DIMENSION_RATIO, aspect);
  }

  static class InspectorColorSet extends BlueprintColorSet {
    InspectorColorSet() {
      mDrawBackground = false;
      mDrawWidgetInfos = true;
      mInspectorBackgroundColor = new JBColor(0xe8e8e8, 0x3c3f41);
      mInspectorFillColor = new JBColor(0xdcdcdc, 0x45494a);
      mInspectorHighlightsStrokeColor = JBColor.border();
      mInspectorStrokeColor = JBColor.foreground();
      mInspectorConstraintColor = new JBColor(0x4481d8, 0x4880c8);
    }
  }

  public WidgetConstraintPanel(@NotNull List<NlComponent> components) {
    super(new GridBagLayout());
    super.setBorder(new EmptyBorder(4, 0, 0, 0));
    ColorSet colorSet = new InspectorColorSet();
    setBackground(colorSet.getInspectorBackgroundColor());
    mMain = new SingleWidgetView(this, colorSet);
    setPreferredSize(new Dimension(200, 216));
    mVerticalSlider.setMajorTickSpacing(50);
    mHorizontalSlider.setMajorTickSpacing(50);
    mVerticalSlider.setBackground(colorSet.getInspectorBackgroundColor());
    mHorizontalSlider.setBackground(colorSet.getInspectorBackgroundColor());
    mHorizontalSlider.setToolTipText(HORIZONTAL_TOOL_TIP_TEXT);
    mVerticalSlider.setToolTipText(VERTICAL_TOOL_TIP_TEXT);
    mVerticalSlider.setName(VERTICAL_BIAS_SLIDER);
    mHorizontalSlider.setName(HORIZONTAL_BIAS_SLIDER);
    updateComponents(components);

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
    mHorizontalSlider.addChangeListener(e -> setHorizontalBias());
    mVerticalSlider.addChangeListener(e -> setVerticalBias());
    mHorizontalSlider.addMouseListener(mDoubleClickListener);
    mVerticalSlider.addMouseListener(mDoubleClickListener);
  }

  // Reset mouse on double click
  MouseListener mDoubleClickListener = new MouseAdapter() {
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

  private static String[][] ourConstraintString_ltr = {{
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
  private static String[][] ourConstraintString_rtl = {{
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

  private static String[][] ourMarginString_ltr = {
    {SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_START},
    {SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN_END},
    {SdkConstants.ATTR_LAYOUT_MARGIN_TOP},
    {SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  private static String[][] ourMarginString_rtl = {
    {SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, SdkConstants.ATTR_LAYOUT_MARGIN_END},
    {SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, SdkConstants.ATTR_LAYOUT_MARGIN_START},
    {SdkConstants.ATTR_LAYOUT_MARGIN_TOP},
    {SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM},
  };
  private static String[][] ourDeleteAttributes = {
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

  private static String[][] ourDeleteNamespace = {
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

      float bias = parseFloat(horizontalBias, 0.5f);
      mHorizontalSlider.setValue((int)(bias * 100));
    }

    if (showVerticalSlider) {
      NlComponent source = findInVerticalChain(mComponent);
      if (source == null) {
        source = mComponent;
      }
      verticalBias = source.getLiveAttribute(sherpaNamespace, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);

      float bias = parseFloat(verticalBias, 0.5f);
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

  private static float parseFloat(String string, float defaultValue) {
    if (string != null && !string.isEmpty()) {
      try {
        return Float.parseFloat(string);
      }
      catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }


  private static int getDimension(NlComponent component, String nameSpace, String attribute) {
    String v = component.getLiveAttribute(nameSpace, attribute);
    if ("wrap_content".equalsIgnoreCase(v)) {
      return -1;
    }
    return ConstraintUtilities.getDpValue(component, v);
  }

  private void setDimension(NlComponent component, String attribute, int currentValue) {
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

  private void setAndroidAttribute(String attribute, String value) {
    setAttribute(SdkConstants.ANDROID_URI, attribute, value);
  }

  private void setSherpaAttribute(String attribute, String value) {
    setAttribute(SdkConstants.SHERPA_URI, attribute, value);
  }

  private void setAttribute(String nameSpace, String attribute, String value) {
    setAttribute(mComponent, nameSpace, attribute, value);
  }

  private void setAttribute(NlComponent component, String nameSpace, String attribute, String value) {
    if (mConfiguringUI) {
      return;
    }
    NlModel model = component.getModel();
    AttributesTransaction transaction = component.startAttributeTransaction();
    transaction.setAttribute(nameSpace, attribute, value);
    transaction.apply();
    model.notifyLiveUpdate(false);
    myTimer.setRepeats(false);

    myWriteAction = new NlWriteCommandAction(Collections.singletonList(component), "Change Widget", () -> {
      AttributesTransaction transaction2 = component.startAttributeTransaction();

      transaction2.setAttribute(nameSpace, attribute, value);
      transaction2.commit();
    });

    myTimer.restart();
  }

  private void removeAttribute(int type) {
    String label = "Constraint Disconnected";
    String[] attribute = ourDeleteAttributes[type];
    String[] namespace = ourDeleteNamespace[type];

    AttributesTransaction transaction = mComponent.startAttributeTransaction();
    for (int i = 0; i < attribute.length; i++) {
      transaction.setAttribute(namespace[i], attribute[i], null);
    }

    ConstraintComponentUtilities.ensureHorizontalPosition(mComponent, transaction);
    ConstraintComponentUtilities.ensureVerticalPosition(mComponent, transaction);

    transaction.apply();
    NlWriteCommandAction.run(mComponent, label, transaction::commit);
  }

  public static final int UNKNOWN = -1;
  public static final int HORIZONTAL = 0;
  public static final int VERTICAL = 1;

  /**
   * Convert Any to SingleWidgetView flags
   *
   * @return
   */
  private int convertFromNL(String attribute) {
    int dimen = getDimension(mComponent, SdkConstants.ANDROID_URI, attribute);
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
    return null != getSherpaAttribute(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
  }

  private String getSherpaAttribute(String attr) {
    return mComponent.getLiveAttribute(SdkConstants.SHERPA_URI, attr);
  }

  public void updateComponents(@NotNull List<NlComponent> components) {
    mComponent = components.isEmpty() ? null : components.get(0);
    if (mComponent != null) {
      mComponent.addLiveChangeListener(myChangeLiveListener);
      //mComponent.getModel().getSelectionModel().addListener(new SelectionListener() {
      //  @Override
      //  public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
      //    widgetChanged();
      //  }
      //});
      configureUI();
    }
  }

  public boolean isApplicable() {
    if (mComponent == null) {
      return false;
    }
    NlComponent parent = mComponent.getParent();
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

  private static NlComponent findInHorizontalChain(NlComponent component) {
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

  private static NlComponent findInVerticalChain(NlComponent component) {
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
  }

  public void setRightMargin(int margin) {
    setDimension(mComponent, SdkConstants.ATTR_LAYOUT_MARGIN_END, margin);
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
          float dipValue = mComponent.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
          oldValue = ((int)(0.5f + NlComponentHelperKt.getW(mComponent) / dipValue)) + "dp";
        }
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, oldValue);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        setAndroidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_WRAP_CONTENT);
        break;
    }
  }

  public void setVerticalConstraint(int verticalConstraint) {
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
          float dipValue = mComponent.getModel().getConfiguration().getDensity().getDpiValue() / 160f;
          oldValue = ((int)(0.5f + NlComponentHelperKt.getH(mComponent) / dipValue)) + "dp";
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
    static final int thumbSize = 22;
    private static Font sSmallFont = new Font("Helvetica", Font.PLAIN, 10);
    ColorSet mColorSet;

    WidgetSliderUI(JSlider s, ColorSet colorSet) {
      super(s);
      mColorSet = colorSet;
    }

    @Override
    protected Dimension getThumbSize() {
      return new Dimension(thumbSize, thumbSize);
    }

    @Override
    public void paintTrack(Graphics g) {
      if (slider.isEnabled()) {
        super.paintTrack(g);
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

    @Override
    protected Color getFocusColor() {
      return new Color(0, 0, 0, 0);
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
      g.setColor(mColorSet.getInspectorFillColor());
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.fillRoundRect(thumbRect.x + 1, thumbRect.y + 1, thumbRect.width - 2, thumbRect.height - 2, thumbRect.width - 2,
                      thumbRect.height - 2);
      g.setColor(mColorSet.getInspectorStrokeColor());
      g.drawRoundRect(thumbRect.x + 1, thumbRect.y + 1, thumbRect.width - 2, thumbRect.height - 2, thumbRect.width - 2,
                      thumbRect.height - 2);
      int x = thumbRect.x + thumbRect.width / 2;
      int y = thumbRect.y + thumbRect.height / 2 - 1;
      g.setFont(sSmallFont);
      FontMetrics fm = g.getFontMetrics();
      Rectangle2D bounds = fm.getStringBounds(percentText, g);
      double tw = bounds.getWidth();

      g.setColor(mColorSet.getInspectorStrokeColor());
      g.drawString(percentText, (int)(x - tw / 2), (y + fm.getAscent() / 2));
    }
  }
}
