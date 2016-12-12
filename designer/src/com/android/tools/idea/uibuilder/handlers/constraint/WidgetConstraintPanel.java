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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;

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
  public static final int UNCONNECTED = -1;

  ColorSet mColorSet = new InspectorColorSet();

  /**
   * When true, updates to the panel are ignored and won't update the widget.
   * This is usually set when the panel is being updated from the widget so there is no need to
   * feed the changes back to the widget.
   */
  private boolean mDisableWidgetUpdates = false;

  public void setProperty(NlProperty property) {
    updateComponents(property.getComponents());
  }

  public void setAspect(String aspect) {
    mWidget.setDimensionRatio(aspect);
    WidgetCompanion companion = (WidgetCompanion)mWidget.getCompanionWidget();
    companion.getWidgetProperties().setDimensionRatio(aspect);
    widgetModified();
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
    // Ignore the the updates to the UI components since we are just going to read them from the widget
    mDisableWidgetUpdates = true;
    configureUI();
    mDisableWidgetUpdates = false;
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
    float ratio = mWidget.getDimensionRatio();
    int side = mWidget.getDimensionRatioSide();
    WidgetCompanion companion = (WidgetCompanion)mWidget.getCompanionWidget();
    String ratioString = companion.getWidgetProperties().getDimensionRatio();
    boolean baseline = hasBaseline();

    boolean showVerticalSlider = bottom != UNCONNECTED && top != UNCONNECTED;
    boolean showHorizontalSlider = left != UNCONNECTED && right != UNCONNECTED;

    if (showHorizontalSlider) {
      float bias = mWidget.getHorizontalBiasPercent();
      if (mWidget.isInHorizontalChain()) {
        ConstraintWidget ctl = mWidget.getHorizontalChainControlWidget();
        if (ctl != null) {
          bias = ctl.getHorizontalBiasPercent();
        }
      }
      mHorizontalSlider.setValue((int)(bias * 100));
    }

    if (showVerticalSlider) {
      float bias = mWidget.getVerticalBiasPercent();
      if (mWidget.isInVerticalChain()) {
        ConstraintWidget ctl = mWidget.getVerticalChainControlWidget();
        if (ctl != null) {
          bias = ctl.getVerticalBiasPercent();
        }
      }
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
    mMain.configureUi(bottom, top, left, right, baseline, widthVal, heightValue, ratioString);
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
      case MATCH_CONSTRAINT:
        return SingleWidgetView.MATCH_CONSTRAINT;
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

  public boolean isApplicable() {
    if (mComponent == null) {
      return false;
    }
    NlComponent parent = mComponent.getParent();
    return parent != null && parent.isOrHasSuperclass(CONSTRAINT_LAYOUT);
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
  // values from ui to widget & NL component
  /*-----------------------------------------------------------------------*/

  /**
   * Method is called when ever we modify the widget
   */
  private void widgetModified() {
    if (mWidget == null || mDisableWidgetUpdates) {
      return;
    }
    if (!mWidgetModified) {
      mConstraintModel.getSelection().addModifiedWidget(mWidget);
    }
    mWidgetModified = true;
    mConstraintModel.getDrawConstraintModels().forEach(DrawConstraintModel::repaint);
    saveWidget();
  }

  private void saveWidget() {
    mConstraintModel.requestSaveToXML();
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
    if (mWidget.isInHorizontalChain()) {
      ConstraintWidget ctl = mWidget.getHorizontalChainControlWidget();
      if (ctl != null) {
        ctl.setHorizontalBiasPercent(bias);
      }

      mWidgetModified = true;
      mConstraintModel.getSelection().addModifiedWidget(ctl);
      mConstraintModel.getDrawConstraintModels().forEach(DrawConstraintModel::repaint);
      saveWidget();
    }
    else {
      mWidget.setHorizontalBiasPercent(bias);
      widgetModified();
    }
  }

  public void setVerticalBias() {
    if (mWidget == null) {
      return;
    }
    float bias = 1f - (mVerticalSlider.getValue() / 100f);
    if (mWidget.isInVerticalChain()) {
      ConstraintWidget ctl = mWidget.getVerticalChainControlWidget();
      if (ctl != null) {
        ctl.setVerticalBiasPercent(bias);
      }

      mWidgetModified = true;
      mConstraintModel.getSelection().addModifiedWidget(ctl);
      mConstraintModel.getDrawConstraintModels().forEach(DrawConstraintModel::repaint);
      saveWidget();
    }
    else {
      mWidget.setVerticalBiasPercent(bias);
      widgetModified();
    }
  }

  public void setTopMargin(int margin) {
    if (mWidget == null) {
      return;
    }
    setMargin(ConstraintAnchor.Type.TOP, margin);
  }

  public void setLeftMargin(int margin) {
    if (mWidget == null) {
      return;
    }
    setMargin(ConstraintAnchor.Type.LEFT, margin);
  }

  public void setRightMargin(int margin) {
    if (mWidget == null) {
      return;
    }
    setMargin(ConstraintAnchor.Type.RIGHT, margin);
  }

  public void setBottomMargin(int margin) {
    if (mWidget == null) {
      return;
    }
    setMargin(ConstraintAnchor.Type.BOTTOM, margin);
  }

  public void killTopConstraint() {
    if (mWidget == null) {
      return;
    }
    killConstraint(ConstraintAnchor.Type.TOP);
  }

  public void killLeftConstraint() {
    if (mWidget == null) {
      return;
    }
    killConstraint(ConstraintAnchor.Type.LEFT);
  }

  public void killRightConstraint() {
    if (mWidget == null) {
      return;
    }
    killConstraint(ConstraintAnchor.Type.RIGHT);
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
    switch (horizontalConstraint) {
      case SingleWidgetView.MATCH_CONSTRAINT:
        mWidget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
        break;
      case SingleWidgetView.FIXED:
        mWidget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        mWidget.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
        break;
    }
    widgetModified();
  }

  public void setVerticalConstraint(int verticalConstraint) {
    if (mWidget == null) {
      return;
    }
    switch (verticalConstraint) {
      case SingleWidgetView.MATCH_CONSTRAINT:
        mWidget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT);
        break;
      case SingleWidgetView.FIXED:
        mWidget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.FIXED);
        break;
      case SingleWidgetView.WRAP_CONTENT:
        mWidget.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
        break;
    }
    widgetModified();
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
        percentText = Integer.toString(100-slider.getValue());
      } else {
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
