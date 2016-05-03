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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.sherpa.drawing.ConnectionDraw;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;

/**
 * UI component for Constraint Inspector
 */
public class WidgetConstraintPanel extends JPanel {
  SingleWidgetView mMain;
  JSlider mVerticalSlider = new JSlider(SwingConstants.VERTICAL);
  JSlider mHorizontalSlider = new JSlider(SwingConstants.HORIZONTAL);
  NlComponent mComponent;

  public WidgetConstraintPanel(NlComponent component) {
    super(new GridBagLayout());
    mMain = new SingleWidgetView(this);
    setPreferredSize(new Dimension(200, 216));
    mComponent = component;
    configureUI(component);
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
    mVerticalSlider.setUI(new WidgetSliderUI(mVerticalSlider));
    mHorizontalSlider.setUI(new WidgetSliderUI(mHorizontalSlider));
    mHorizontalSlider.addChangeListener(e -> ConstraintUtilities.saveNlAttribute(
      mComponent, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, String.valueOf(mHorizontalSlider.getValue() / 100f)));
    mVerticalSlider.addChangeListener(e -> ConstraintUtilities.saveNlAttribute(
      mComponent, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, String.valueOf(1f - (mVerticalSlider.getValue() / 100f))));
  }

  /**
   * Convert the android style dp string into an integer
   *
   * @param str
   * @return
   */
  private static int convert(String str) {
    if (str == null) return 0;
    try {
      return Integer.parseInt(StringUtil.trimEnd(str, "dp"));
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Read the values off of the NLcomponent and set up the UI
   *
   * @param component
   */
  public void configureUI(NlComponent component) {
    mComponent = component;
    if (component == null) return;
    String mWidgetName = component.getId();
    int bottom = convert(component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_MARGIN));
    int top = convert(component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_MARGIN));
    int left = convert(component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_MARGIN));
    int right = convert(component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_MARGIN));

    String rl = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF);
    String rr = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF);
    String ll = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF);
    String lr = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF);
    String tt = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF);
    String tb = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF);
    String bt = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF);
    String bb = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF);
    String hbias = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS);
    String vbias = component.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);

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
    mVerticalSlider.setEnabled(bottom >= 0 && top >= 0);
    mHorizontalSlider.setEnabled(left >= 0 && right >= 0);
    mHorizontalSlider.invalidate();
    mVerticalSlider.invalidate();

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

    mMain.configureUi(mWidgetName, bottom, top, left, right);
  }

  public void setTopMargin(int margin) {
    ConstraintUtilities.saveNlAttribute(mComponent, SdkConstants.ATTR_LAYOUT_TOP_MARGIN, (margin < 0) ? null : (margin + "dp"));
  }

  public void setLeftMargin(int margin) {
    ConstraintUtilities.saveNlAttribute(mComponent, SdkConstants.ATTR_LAYOUT_LEFT_MARGIN, (margin < 0) ? null : (margin + "dp"));
  }

  public void setRightMargin(int margin) {
    ConstraintUtilities.saveNlAttribute(mComponent, SdkConstants.ATTR_LAYOUT_RIGHT_MARGIN, (margin < 0) ? null : (margin + "dp"));
  }

  public void setBottomMargin(int margin) {
    ConstraintUtilities.saveNlAttribute(mComponent, SdkConstants.ATTR_LAYOUT_BOTTOM_MARGIN, (margin < 0) ? null : (margin + "dp"));
  }

  public WidgetConstraintPanel() {
    this(null);
  }

  /**
   * Look and Feel for the sliders
   */
  static class WidgetSliderUI extends BasicSliderUI {
    WidgetSliderUI(JSlider s) {
      super(s);
    }

    @Override
    protected Dimension getThumbSize() {
      return new Dimension(18, 18);
    }

    @Override
    public void paintTrack(Graphics g) {
      if (slider.isEnabled()) {
        super.paintTrack(g);
      }
    }

    @Override
    public void paintThumb(Graphics g) {
      Graphics2D g2d = (Graphics2D)g;

      String percentText = Integer.toString(slider.getValue());
      if (slider.isEnabled()) {
        g.setColor(SingleWidgetView.sStrokeColor);
      }
      else {
        g.setColor(Color.LIGHT_GRAY);
        percentText = "";
      }
      ConnectionDraw.drawCircledText(g2d, percentText, thumbRect.x + thumbRect.width / 2 - 1,
                         thumbRect.y + thumbRect.height / 2 - 1);
    }
  }
}
