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
import android.support.constraint.solver.widgets.ConstraintWidget;
import com.android.SdkConstants;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * UI component for Constraint Inspector
 */
public class WidgetConstraintPanel extends JPanel {
  private static final String HORIZONTAL_TOOL_TIP_TEXT = "Horizontal Bias";
  private static final String VERTICAL_TOOL_TIP_TEXT = "Vertical Bias";
  public static final String SIZE_ANY = "0dp";
  final SingleWidgetView mMain;
  final JSlider mVerticalSlider = new JSlider(SwingConstants.VERTICAL);
  final JSlider mHorizontalSlider = new JSlider(SwingConstants.HORIZONTAL);
  private WidgetsScene mScene;
  ConstraintModel mConstraintModel;
  NlComponent mComponent;
  ConstraintWidget mWidget;
  private boolean mWidgetModified;
  private Timer mWidgetSaveTimer = new Timer(500, e -> saveWidget());
  public static final int UNCONNECTED = -1;

  private String mWidgetWidthCache;
  private String mWidgetHeightCache;

  ColorSet mColorSet = new InspectorColorSet();

  public void setProperty(NlProperty property) {
    updateComponents(property.getComponents());
  }

  static class InspectorColorSet extends BlueprintColorSet {
    InspectorColorSet() {
      mDrawBackground = false;
      mDrawWidgetInfos = true;
      mInspectorBackgroundColor = new JBColor(0xe8e8e8, 0x3c3f41);
      mInspectorFillColor = new JBColor(0xdcdcdc, 0x45494a);
      mInspectorHighlightsStrokeColor = JBColor.border();
      mInspectorStrokeColor = JBColor.foreground();
    }
  }

  public WidgetConstraintPanel(@NotNull List<NlComponent> components) {
    super(new GridBagLayout());
    super.setBorder(new EmptyBorder(4, 0, 0, 0));
    setBackground(mColorSet.getInspectorBackgroundColor());
    mMain = new SingleWidgetView(this, mColorSet);
    setPreferredSize(new Dimension(200, 216));
    mVerticalSlider.setMajorTickSpacing(50);
    mHorizontalSlider.setMajorTickSpacing(50);
    mVerticalSlider.setBackground(mColorSet.getInspectorBackgroundColor());
    mHorizontalSlider.setBackground(mColorSet.getInspectorBackgroundColor());
    mHorizontalSlider.setToolTipText(HORIZONTAL_TOOL_TIP_TEXT);
    mVerticalSlider.setToolTipText(VERTICAL_TOOL_TIP_TEXT);
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
    mVerticalSlider.setUI(new WidgetSliderUI(mVerticalSlider, mColorSet));
    mHorizontalSlider.setUI(new WidgetSliderUI(mHorizontalSlider, mColorSet));
    mHorizontalSlider.addChangeListener(e -> setHorizontalBias());
    mVerticalSlider.addChangeListener(e -> setVerticalBias());
  }

  /*-----------------------------------------------------------------------*/
  // code for getting values from ConstraintWidget to UI
  /*-----------------------------------------------------------------------*/

  /**
   * Called when mWidget is being changed
   */
  private void widgetChanged() {
    configureUI();
    repaint();
  }

  /**
   * This loads the parameters form mWidget the ConstraintWidget
   */
  private void configureUI() {
    if (mWidget == null) {
      return;
    }
    int top = getMargin(ConstraintAnchor.Type.TOP);
    int left = getMargin(ConstraintAnchor.Type.LEFT);
    int right = getMargin(ConstraintAnchor.Type.RIGHT);
    int bottom = getMargin(ConstraintAnchor.Type.BOTTOM);
    boolean baseline = hasBaseline();

    boolean showVerticalSlider = bottom != UNCONNECTED && top != UNCONNECTED;
    boolean showHorizontalSlider = left != UNCONNECTED && right != UNCONNECTED;

    if (showHorizontalSlider) {
      float bias = mWidget.getHorizontalBiasPercent();
      mHorizontalSlider.setValue((int)(bias * 100));
    }

    if (showVerticalSlider) {
      float bias = mWidget.getVerticalBiasPercent();
      mVerticalSlider.setValue(100 - (int)(bias * 100));
    }

    mVerticalSlider.setEnabled(showVerticalSlider);
    mHorizontalSlider.setEnabled(showHorizontalSlider);
    mHorizontalSlider.invalidate();
    mVerticalSlider.invalidate();
    mVerticalSlider.setToolTipText(showVerticalSlider ? VERTICAL_TOOL_TIP_TEXT : null);
    mHorizontalSlider.setToolTipText(showHorizontalSlider ? HORIZONTAL_TOOL_TIP_TEXT : null);

    int widthVal = convert(mWidget.getHorizontalDimensionBehaviour());
    int heightValue = convert(mWidget.getVerticalDimensionBehaviour());
    mMain.configureUi(bottom, top, left, right, baseline, widthVal, heightValue);
  }

  /**
   * Convert Any to SingleWidgetView flags
   *
   * @param behaviour
   * @return
   */
  private static int convert(ConstraintWidget.DimensionBehaviour behaviour) {
    switch (behaviour) {
      case FIXED:
        return SingleWidgetView.FIXED;
      case WRAP_CONTENT:
        return SingleWidgetView.WRAP_CONTENT;
      case ANY:
        return SingleWidgetView.ANY;
    }
    return SingleWidgetView.FIXED;
  }

  /**
   * Returns true if mWidget has a baseline
   *
   * @return
   */
  private boolean hasBaseline() {
    ConstraintAnchor anchor = mWidget.getAnchor(ConstraintAnchor.Type.BASELINE);
    return anchor != null && anchor.isConnected();
  }

  public void updateComponents(@NotNull List<NlComponent> components) {
    mComponent = components.isEmpty() ? null : components.get(0);
    mWidget = null;
    if (mComponent != null) {
      mConstraintModel = ConstraintModel.getConstraintModel(mComponent.getModel());
      mScene = mConstraintModel.getScene();
      mConstraintModel.getSelection().setContinuousListener(e -> widgetChanged());
      //TODO: improve the tear-down mechanism
      ConstraintWidget widget = mScene.getWidget(mComponent);
      if (widget == null) return;
      if (mWidgetModified && mWidget != null && widget != mWidget) { // we are changing
        saveWidget();
      }
      mWidget = widget;
      configureUI();
    }
  }

  /**
   * Get the margin from connection return -1 if no connection
   *
   * @param type
   * @return
   */
  private int getMargin(ConstraintAnchor.Type type) {
    ConstraintAnchor anchor = mWidget.getAnchor(type);
    if (anchor != null && anchor.isConnected()) {
      return anchor.getMargin();
    }
    return UNCONNECTED;
  }

  /*-----------------------------------------------------------------------*/
  // values from widget & NL component to ui
  /*-----------------------------------------------------------------------*/

  /**
   * Read the values off of the NLcomponent and set up the UI
   *
   * @param component
   */
  public void configureUI(NlComponent component) {
    mComponent = component;
    if (component == null) return;

    String mWidgetName = component.getId();
    int bottom = ConstraintUtilities.getMargin(component, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM);
    int top = ConstraintUtilities.getMargin(component, SdkConstants.ATTR_LAYOUT_MARGIN_TOP);
    int left = ConstraintUtilities.getMargin(component, SdkConstants.ATTR_LAYOUT_MARGIN_START);
    int right = ConstraintUtilities.getMargin(component, SdkConstants.ATTR_LAYOUT_MARGIN_END);

    String rl = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    String rr = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    String ll = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    String lr = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    String tt = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    String tb = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    String bt = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    String bb = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    String basline = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF);
    String hbias = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS);
    String vbias = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);
    String widthStr = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH);
    String heightStr = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT);
    if (rl == null && rr == null) {
      right = -1;
    }
    if (ll == null && lr == null) {
      left = -1;
    }
    if (tt == null && tb == null) {
      top = -1;
    }
    if (bb == null && bt == null) {
      bottom = -1;
    }

    boolean showVerticalSlider = bottom >= 0 && top >= 0;
    boolean showHorizontalSlider = left >= 0 && right >= 0;
    mVerticalSlider.setEnabled(showVerticalSlider);
    mHorizontalSlider.setEnabled(showHorizontalSlider);
    mHorizontalSlider.invalidate();
    mVerticalSlider.invalidate();
    mVerticalSlider.setToolTipText(showVerticalSlider ? VERTICAL_TOOL_TIP_TEXT : null);
    mHorizontalSlider.setToolTipText(showHorizontalSlider ? HORIZONTAL_TOOL_TIP_TEXT : null);

    float horizBias = 0.5f;
    if (hbias != null && hbias.length() > 0) {
      horizBias = Float.parseFloat(hbias);
    }
    float vertBias = 0.5f;
    if (vbias != null && vbias.length() > 0) {
      vertBias = Float.parseFloat(vbias);
    }
    mHorizontalSlider.setValue((int)(horizBias * 100));
    mVerticalSlider.setValue(100 - (int)(vertBias * 100));
    int widthVal = SingleWidgetView.FIXED;
    int heightValue = SingleWidgetView.FIXED;
    if (SdkConstants.VALUE_WRAP_CONTENT.equals(widthStr)) {
      widthVal = SingleWidgetView.WRAP_CONTENT;
    }
    else if (SIZE_ANY.equals(widthStr)) {
      widthVal = SingleWidgetView.ANY;
    }
    else {
      mWidgetWidthCache = widthStr;
    }

    updateCacheSize(heightStr, heightValue);

    mMain.configureUi(bottom, top, left, right, basline != null, widthVal, heightValue);
  }

  private int updateCacheSize(String heightStr, int heightValue) {
    Configuration configuration = mComponent.getModel().getConfiguration();
    float scale = configuration.getDensity().getDpiValue() / 160.0f;

    // We don't have a width cache, which means the widget was initially
    // created in spring or wrap_content mode, we can just use the current
    // widget model's width as a fallback
    if (mWidgetWidthCache == null) {
      mWidgetWidthCache = String.valueOf((int)Math.max(mComponent.w / scale, 1.0)) + "dp";
    }

    if (SdkConstants.VALUE_WRAP_CONTENT.equals(heightStr)) {
      heightValue = SingleWidgetView.WRAP_CONTENT;
    }
    else if (SIZE_ANY.equals(heightStr)) {
      heightValue = SingleWidgetView.ANY;
    }
    else {
      mWidgetHeightCache = heightStr;
    }

    // See width above
    if (mWidgetHeightCache == null) {
      mWidgetHeightCache = String.valueOf((int)Math.max(mComponent.h / scale, 1.0)) + "dp";
    }
    return heightValue;
  }

  /*-----------------------------------------------------------------------*/
  // values from ui to widget & NL component
  /*-----------------------------------------------------------------------*/

  /**
   * Method is called when ever we modify the widget
   */
  private void widgetModified() {
    if (mWidget == null) {
      return;
    }
    if (!mWidgetModified) {
      mConstraintModel.getSelection().addModifiedWidget(mWidget);
    }
    mWidgetModified = true;
    mWidgetSaveTimer.restart();
  }

  private void saveWidget() {
    mConstraintModel.requestSaveToXML();
    mWidgetSaveTimer.stop();
    mWidgetModified = false;
  }

  private void setMargin(ConstraintAnchor.Type type, int margin) {
    if (mWidget == null) {
      return;
    }
    ConstraintAnchor anchor = mWidget.getAnchor(type);
    if (anchor != null) {
      if (margin == -1) {
        anchor.reset();
      }
      else {
        anchor.setMargin(margin);
      }
      widgetModified();
    }
  }

  private void killConstraint(ConstraintAnchor.Type type) {
    if (mWidget == null) {
      return;
    }
    ConstraintAnchor anchor = mWidget.getAnchor(type);
    anchor.reset();
    widgetModified();
  }

  public void setHorizontalBias() {
    if (mWidget == null) {
      return;
    }
    float bias = (mHorizontalSlider.getValue() / 100f);
    mConstraintModel.allowsUpdate(false);
    mWidget.setHorizontalBiasPercent(bias);
    widgetModified();
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void setVerticalBias() {
    if (mWidget == null) {
      return;
    }
    float bias = 1f - (mVerticalSlider.getValue() / 100f);
    mConstraintModel.allowsUpdate(false);
    mWidget.setVerticalBiasPercent(bias);
    widgetModified();
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void setTopMargin(int margin) {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    setMargin(ConstraintAnchor.Type.TOP, margin);
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void setLeftMargin(int margin) {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    setMargin(ConstraintAnchor.Type.LEFT, margin);
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void setRightMargin(int margin) {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    setMargin(ConstraintAnchor.Type.RIGHT, margin);
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void setBottomMargin(int margin) {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    setMargin(ConstraintAnchor.Type.BOTTOM, margin);
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void killTopConstraint() {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    killConstraint(ConstraintAnchor.Type.TOP);
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void killLeftConstraint() {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    killConstraint(ConstraintAnchor.Type.LEFT);
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void killRightConstraint() {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    killConstraint(ConstraintAnchor.Type.RIGHT);
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void killBottomConstraint() {
    if (mWidget == null) {
      return;
    }
    killConstraint(ConstraintAnchor.Type.BOTTOM);

  }

  public void killBaselineConstraint() {
    killConstraint(ConstraintAnchor.Type.BASELINE);
  }

  public void setHorizontalConstraint(int horizontalConstraint) {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    switch (horizontalConstraint) {
      case SingleWidgetView.ANY:
        mWidget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
        break;
      case SingleWidgetView.FIXED:
        mWidget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        mWidget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
        break;
    }
    widgetModified();
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
  }

  public void setVerticalConstraint(int verticalConstraint) {
    if (mWidget == null) {
      return;
    }
    mConstraintModel.allowsUpdate(false);
    switch (verticalConstraint) {
      case SingleWidgetView.ANY:
        mWidget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
        break;
      case SingleWidgetView.FIXED:
        mWidget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        mWidget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
        break;
    }
    widgetModified();
    mConstraintModel.allowsUpdate(true);
    mConstraintModel.requestRender();
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
      String percentText = Integer.toString(slider.getValue());
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
