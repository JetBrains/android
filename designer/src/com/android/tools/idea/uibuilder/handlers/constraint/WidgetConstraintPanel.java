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
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.scene.ConstraintComponentUtilities;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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
  final SingleWidgetView mMain;
  final JSlider mVerticalSlider = new JSlider(SwingConstants.VERTICAL);
  final JSlider mHorizontalSlider = new JSlider(SwingConstants.HORIZONTAL);
  private boolean mConfiguringUI = false;
  NlComponent mComponent;
  public static final int UNCONNECTED = -1;
  WriteCommandAction myWriteAction;
  private final static int DELAY_BEFORE_COMMIT = 400; // ms
  ColorSet mColorSet = new InspectorColorSet();
  Timer myTimer = new Timer(DELAY_BEFORE_COMMIT, (c) -> {
    if (myWriteAction != null) {
      ApplicationManager.getApplication().invokeLater(() -> myWriteAction.execute());
    }
  });

  /**
   * When true, updates to the panel are ignored and won't update the widget.
   * This is usually set when the panel is being updated from the widget so there is no need to
   * feed the changes back to the widget.
   */
  private ChangeListener myChangeLiveListener = new ChangeListener() {
    @Override
    public void stateChanged(ChangeEvent e) {
      configureUI();
    }
  };

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

  static final int CONNECTION_LEFT = 0;
  static final int CONNECTION_RIGHT = 1;
  static final int CONNECTION_TOP = 2;
  static final int CONNECTION_BOTTOM = 3;
  static final int CONNECTION_BASELINE = 4;

  static String[][] ourConstraintString = {
    {SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF},
    {SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF},
    {SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF},
    {SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF},
    {SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF}
  };
  static String[] ourMarginString = {
    SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
  };
  static String[][] ourDeleteAttributes = {
    {SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
      SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
      SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
      SdkConstants.ATTR_LAYOUT_MARGIN_START,
      SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS},
    {SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
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

  static String[][] ourDeleteNamespace = {
    {SdkConstants.SHERPA_URI,
      SdkConstants.SHERPA_URI,
      SdkConstants.ANDROID_URI,
      SdkConstants.ANDROID_URI,
      SdkConstants.SHERPA_URI},
    {SdkConstants.SHERPA_URI,
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
    int margin = ConstraintUtilities.getMargin(mComponent, ourMarginString[type]);
    String connection = mComponent.getLiveAttribute(SdkConstants.SHERPA_URI, ourConstraintString[type][0]);
    if (connection == null && ourConstraintString[type].length > 1) {
      connection = mComponent.getLiveAttribute(SdkConstants.SHERPA_URI, ourConstraintString[type][1]);
      if (connection == null) {
        margin = -1;
      }
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
    String horizontalBias = mComponent.getLiveAttribute(sherpaNamespace, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS);
    String verticalBias = mComponent.getLiveAttribute(sherpaNamespace, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);

    boolean baseline = hasBaseline();

    boolean showVerticalSlider = bottom != UNCONNECTED && top != UNCONNECTED;
    boolean showHorizontalSlider = left != UNCONNECTED && right != UNCONNECTED;

    if (showHorizontalSlider) {
      if (NlComponentUtils.isHorizontalChain(mComponent)) {
        NlComponent ctl = NlComponentUtils.getLeftMostInChain(mComponent);
        horizontalBias = ctl.getLiveAttribute(sherpaNamespace, SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS);
      }
      float bias = parseFloat(horizontalBias, 0.5f);
      mHorizontalSlider.setValue((int)(bias * 100));
    }

    if (showVerticalSlider) {

      if (NlComponentUtils.isVerticalChain(mComponent)) {
        NlComponent ctl = NlComponentUtils.getTopMostInChain(mComponent);
        verticalBias = ctl.getLiveAttribute(sherpaNamespace, SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS);
      }
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
    if (string != null && string.length() > 0) {
      try {
        return Float.parseFloat(string);
      }
      catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }


  int getDimension(NlComponent component, String nameSpace, String attribute) {
    String v = component.getLiveAttribute(nameSpace, attribute);
    if ("wrap_content".equalsIgnoreCase(v)) {
      return -1;
    }
    return ConstraintUtilities.getDpValue(component, v);
  }

  void setDimension(NlComponent component, String nameSpace, String attribute, int currentValue) {
    String v = component.getLiveAttribute(nameSpace, attribute);
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
    if (mConfiguringUI) {
      return;
    }
    NlModel model = mComponent.getModel();

    AttributesTransaction transaction = mComponent.startAttributeTransaction();
    transaction.setAttribute(nameSpace, attribute, value);
    transaction.apply();

    myTimer.setRepeats(false);

    Project project = model.getProject();
    XmlFile file = model.getFile();

    String label = "Change Widget";
    myWriteAction = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        AttributesTransaction transaction = mComponent.startAttributeTransaction();
        transaction.setAttribute(nameSpace, attribute, value);
        transaction.commit();
      }
    };

    myTimer.restart();
  }

  private void removeAttribute(int type) {
    String label = "Constraint Disconnected";
    String[] attribute = ourDeleteAttributes[type];
    String[] namespace = ourDeleteNamespace[type];
    NlModel nlModel = mComponent.getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    AttributesTransaction transaction = mComponent.startAttributeTransaction();
    for (int i = 0; i < attribute.length; i++) {
      transaction.setAttribute(namespace[i], attribute[i], null);
    }

    ConstraintComponentUtilities.ensureHorizontalPosition(mComponent, transaction);
    ConstraintComponentUtilities.ensureVerticalPosition(mComponent, transaction);

    transaction.apply();
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        transaction.commit();
      }
    };
    action.execute();
  }

  public static final int UNKNOWN = -1;
  public static final int HORIZONTAL = 0;
  public static final int VERTICAL = 1;

  /**
   * Convert Any to SingleWidgetView flags
   *
   * @param behaviour
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
    return parent != null && parent.isOrHasSuperclass(CONSTRAINT_LAYOUT);
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
    }
  }

  public void setHorizontalBias() {
    float bias = (mHorizontalSlider.getValue() / 100f);

    setSherpaAttribute(SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS, Float.toString(bias));
  }

  public void setVerticalBias() {
    float bias = 1f - (mVerticalSlider.getValue() / 100f);
    setSherpaAttribute(SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS, Float.toString(bias));
  }

  public void setTopMargin(int margin) {
    setDimension(mComponent, SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, margin);
  }

  public void setLeftMargin(int margin) {
    setDimension(mComponent, SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT, margin);
  }

  public void setRightMargin(int margin) {
    setDimension(mComponent, SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT, margin);
  }

  public void setBottomMargin(int margin) {
    setDimension(mComponent, SdkConstants.NS_RESOURCES, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM, margin);
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
          oldValue = ((int)(0.5f + mComponent.w / dipValue)) + "dp";
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
          oldValue = ((int)(0.5f + mComponent.h / dipValue)) + "dp";
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
