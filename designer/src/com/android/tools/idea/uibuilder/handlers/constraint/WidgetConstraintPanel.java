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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.BlueprintColorSet;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintModel.*;

/**
 * UI component for Constraint Inspector
 */
public class WidgetConstraintPanel extends AdtSecondaryPanel implements CustomPanel {
  private static final String HORIZONTAL_TOOL_TIP_TEXT = "Horizontal Bias";
  private static final String VERTICAL_TOOL_TIP_TEXT = "Vertical Bias";
  private static final Color mSliderColor = new JBColor(0xC9C9C9, 0x242627);
  private static final JBDimension PANEL_DIMENSION = JBUI.size(201, 215);
  @NotNull private final SingleWidgetView mMain;
  private final JSlider mVerticalSlider = new JSlider(SwingConstants.VERTICAL);
  private final JSlider mHorizontalSlider = new JSlider(SwingConstants.HORIZONTAL);

  private static final int UNCONNECTED = -1;
  private final static int SLIDER_DEFAULT = 50;
  public final static String VERTICAL_BIAS_SLIDER = "verticalBiasSlider";
  public final static String HORIZONTAL_BIAS_SLIDER = "horizontalBiasSlider";

  private final WidgetConstraintModel myWidgetModel = new WidgetConstraintModel(() -> configureUI());

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
    super(null);
    ColorSet colorSet = new InspectorColorSet();
    mMain = new SingleWidgetView(colorSet, myWidgetModel);
    mMain.setOpaque(false);
    setPreferredSize(PANEL_DIMENSION);
    mVerticalSlider.setMajorTickSpacing(50);
    mHorizontalSlider.setMajorTickSpacing(50);
    mHorizontalSlider.setToolTipText(HORIZONTAL_TOOL_TIP_TEXT);
    mVerticalSlider.setToolTipText(VERTICAL_TOOL_TIP_TEXT);
    mVerticalSlider.setName(VERTICAL_BIAS_SLIDER);
    mHorizontalSlider.setName(HORIZONTAL_BIAS_SLIDER);
    myWidgetModel.setComponent(components.isEmpty() ? null : components.get(0));

    add(mVerticalSlider);
    add(mMain);
    add(mHorizontalSlider);

    mVerticalSlider.setUI(new WidgetSliderUI(mVerticalSlider, colorSet));
    mHorizontalSlider.setUI(new WidgetSliderUI(mHorizontalSlider, colorSet));
    mHorizontalSlider.setBackground(StudioColorsKt.getSecondaryPanelBackground());
    mVerticalSlider.setBackground(StudioColorsKt.getSecondaryPanelBackground());
    mHorizontalSlider.setForeground(mSliderColor);
    mVerticalSlider.setForeground(mSliderColor);
    mHorizontalSlider.addChangeListener(e -> myWidgetModel.setHorizontalBias(mHorizontalSlider.getValue()));
    mVerticalSlider.addChangeListener(e -> myWidgetModel.setVerticalBias(mVerticalSlider.getValue()));
    mHorizontalSlider.addMouseListener(mDoubleClickListener);
    mVerticalSlider.addMouseListener(mDoubleClickListener);
    configureUI();
  }

  @Override
  public void doLayout() {
    int width = getWidth();
    int height = getHeight();
    Dimension mainSize = mMain.getPreferredSize();
    Dimension HSliderSize = mHorizontalSlider.getPreferredSize();
    Dimension VSliderSize = mVerticalSlider.getPreferredSize();

    int mainX = (width - mainSize.width) / 2;
    int mainY = (height - mainSize.height) / 2;
    mMain.setBounds(mainX, mainY, mainSize.width, mainSize.height);
    mHorizontalSlider.setBounds((width - HSliderSize.width) / 2, mainY + mainSize.height, HSliderSize.width, HSliderSize.height);
    mVerticalSlider.setBounds(mainX - VSliderSize.width, (height - VSliderSize.height) / 2, VSliderSize.width, VSliderSize.height);
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return this;
  }

  @Override
  public void useComponent(@Nullable NlComponent component) {
    myWidgetModel.setComponent(component);
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

  /**
   * This loads the parameters form component the ConstraintWidget
   */
  private void configureUI() {
    if (myWidgetModel.getComponent() == null) {
      return;
    }

    int top = myWidgetModel.getMargin(CONNECTION_TOP);
    int left = myWidgetModel.getMargin(CONNECTION_LEFT);
    int right = myWidgetModel.getMargin(CONNECTION_RIGHT);
    int bottom = myWidgetModel.getMargin(CONNECTION_BOTTOM);

    String ratioString = myWidgetModel.getRatioString();

    boolean baseline = myWidgetModel.hasBaseline();

    boolean showVerticalSlider = bottom != UNCONNECTED && top != UNCONNECTED;
    boolean showHorizontalSlider = left != UNCONNECTED && right != UNCONNECTED;

    if (showHorizontalSlider) {
      float bias = myWidgetModel.getHorizontalBias();
      mHorizontalSlider.setValue((int)(bias * 100));
    }

    if (showVerticalSlider) {
      float bias = myWidgetModel.getVerticalBias();
      mVerticalSlider.setValue(100 - (int)(bias * 100));
    }

    mVerticalSlider.setEnabled(showVerticalSlider);
    mHorizontalSlider.setEnabled(showHorizontalSlider);
    mHorizontalSlider.invalidate();
    mVerticalSlider.invalidate();
    mVerticalSlider.setToolTipText(showVerticalSlider ? VERTICAL_TOOL_TIP_TEXT : null);
    mHorizontalSlider.setToolTipText(showHorizontalSlider ? HORIZONTAL_TOOL_TIP_TEXT : null);

    int widthValue = myWidgetModel.convertFromNL(SdkConstants.ATTR_LAYOUT_WIDTH);
    int heightValue = myWidgetModel.convertFromNL(SdkConstants.ATTR_LAYOUT_HEIGHT);
    mMain.configureUi(bottom, top, left, right, baseline, widthValue, heightValue, ratioString);
  }
  /*-----------------------------------------------------------------------*/
  //Look and Feel for the sliders
  /*-----------------------------------------------------------------------*/

  static class WidgetSliderUI extends BasicSliderUI {
    private static final JBDimension THUMB_SIZE = JBUI.size(18);
    private static final int TRACK_THICKNESS = JBUIScale.scale(5);
    private static final int ARC_SIZE = JBUIScale.scale(2);
    private static final int SLIDER_LENGTH = 120;
    private static final Dimension V_SIZE = new Dimension(THUMB_SIZE.width, SLIDER_LENGTH);
    private static final Dimension H_SIZE = new Dimension(SLIDER_LENGTH, THUMB_SIZE.height);
    @NotNull private static Font SMALL_FONT = new Font("Helvetica", Font.PLAIN, JBUIScale.scaleFontSize((float)10));
    private ColorSet mColorSet;

    WidgetSliderUI(JSlider s, ColorSet colorSet) {
      super(s);
      mColorSet = colorSet;
    }

    @Override
    public Dimension getPreferredHorizontalSize() {
      return H_SIZE;
    }

    @Override
    public Dimension getPreferredVerticalSize() {
      return V_SIZE;
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
      g.fillRoundRect(thumbRect.x, thumbRect.y, thumbRect.width - 1, thumbRect.height - 1, thumbRect.width,
                      thumbRect.height);
      g.setColor(StudioColorsKt.getBorder());
      g.drawRoundRect(thumbRect.x, thumbRect.y, thumbRect.width - 1, thumbRect.height - 1, thumbRect.width,
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
