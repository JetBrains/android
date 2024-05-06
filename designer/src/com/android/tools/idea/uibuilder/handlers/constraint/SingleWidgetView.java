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

import static com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities.registerAttributeHelp;
import static com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintModel.CONNECTION_BOTTOM;
import static com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintModel.CONNECTION_LEFT;
import static com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintModel.CONNECTION_RIGHT;
import static com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintModel.CONNECTION_TOP;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ConnectionDraw;
import com.android.tools.idea.uibuilder.handlers.constraint.model.ConstraintAnchorConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.model.ConstraintWidgetConstants;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

/**
 * Uses a SceneDraw to render an iconic form of the widget
 */
public class SingleWidgetView extends JPanel {

  /**
   * Constant representing unlocked aspect ratio
   */
  private static final int RATIO_UNLOCK = 0;

  /**
   * Constant representing aspect ratio locked on both width and height
   */
  private static final int RATIO_LOCK = 1;

  /**
   * Constant representing aspect ratio locked on height
   */
  private static final int RATIO_LOCK_HEIGHT = 2;

  /**
   * Constant representing aspect ratio locked on both width
   */
  private static final int RATIO_LOCK_WIDTH = 3;

  public final static String TOP_MARGIN_WIDGET = "topMarginWidget";
  public final static String LEFT_MARGIN_WIDGET = "leftMarginWidget";
  public final static String BOTTOM_MARGIN_WIDGET = "bottomMarginWidget";
  public final static String RIGHT_MARGIN_WIDGET = "rightMarginWidget";

  private static final int ASPECT_BUTTON_BOX_SIZE_RATIO = 6;

  /*
   * Constraints Controls Sizes
   */

  /**
   * size of the empty square in between the constraint controls
   */
  private static final int MIDDLE_SPACE = 6;

  /**
   * Length of a constraint symbol
   */
  private static final int CONSTRAINT_LENGTH = 9;

  /**
   * Width of a constraint symbol
   */
  private static final int CONSTRAINT_WIDTH = 4;

  /**
   * Size of the JComponent painting constraint symbols
   */
  private static final int CONSTRAINT_COMPONENT_WIDTH = 200;

  private static final int CONSTRAINT_COMPONENT_HEIGHT = 30;

  /**
   * Radius for round controls like the connect button
   */
  private static final int CIRCLE_CONTROL_RADIUS = 5;

  /**
   * Radius for kill button
   */
  private static final int KILL_BUTTON_SIZE = 5;

  /**
   * Size of a repeating pattern for wrap and spring constraint
   */
  private static final int CONSTRAINT_PATTERN_SIZE = 2;

  private static final int DROP_DOWN_WIDTH = 66;

  private static final int DROP_DOWN_HEIGHT = 25;

  /**
   * Offset between a dropdown and the box
   */
  private static final int DROPDOWN_OFFSET = 12;

  /**
   * Size of the square representing the widget
   */
  public static final int BOX_SIZE = 60;

  /* Constants for the different states for a constraint */
  public final static int MATCH_CONSTRAINT = 1;
  public final static int WRAP_CONTENT = 2;
  public final static int FIXED = 0;
  public final static int UNCONNECTED = -1;

  private final ColorSet mColorSet;
  private boolean mCacheBaseline;
  private int mCacheWidth;
  private int mCacheHeight;
  private String mRatioString;
  private float mDimensionRatio;
  private int mDimensionRatioSide;
  private int mRatioHeight;
  private int mRatioWidth;
  private int mRatioLock;

  private int mWidth;
  private int mHeight;

  private final WidgetRender mWidgetRender = new WidgetRender();
  private final ArrayList<Graphic> mGraphicList = new ArrayList<>();
  private MarginWidget mTopMargin;
  private MarginWidget mLeftMargin;
  private MarginWidget mRightMargin;
  private MarginWidget mBottomMargin;
  private HConstraintDisplay mHbar1;
  private HConstraintDisplay mHbar2;
  private VConstraintDisplay mVbar1;
  private VConstraintDisplay mVbar2;

  private ConnectButton mTopConnect;
  private ConnectButton mLeftConnect;
  private ConnectButton mRightConnect;
  private ConnectButton mBottomConnect;

  private KillButton mTopKill;
  private KillButton mLeftKill;
  private KillButton mRightKill;
  private KillButton mBottomKill;
  private KillButton mBaselineKill;
  private AspectButton mAspectButton;
  private JLabel mAspectLabel;
  private JTextField mAspectText;
  private WidgetConstraintModel myWidgetModel;
  private boolean myInitialized;

  private final String[] statusString = {"Fixed", "Match Constraints", "Wrap Content"};

  /**
   * Radius of the connect button
   */
  private final static int CONNECT_BUTTON_RADIUS = 7;

  /**
   * Margins between the Box and the bar representing the type of constraint
   */
  public static final int BAR_OUTSIDE_MARGINS = CONNECT_BUTTON_RADIUS + 2;

  public SingleWidgetView(@NotNull ColorSet colorSet, @NotNull WidgetConstraintModel widgetModel) {
    super(null);
    mColorSet = colorSet;
    myWidgetModel = widgetModel;

    mTopMargin = new MarginWidget(TOP_MARGIN_WIDGET, "Top Margin");
    mLeftMargin = new MarginWidget(LEFT_MARGIN_WIDGET, "Left Margin");
    mRightMargin = new MarginWidget(RIGHT_MARGIN_WIDGET, "Right Margin");
    mBottomMargin = new MarginWidget(BOTTOM_MARGIN_WIDGET, "Bottom Margin");
    mHbar1 = new HConstraintDisplay(mColorSet, true);
    mHbar2 = new HConstraintDisplay(mColorSet, false);
    mVbar1 = new VConstraintDisplay(mColorSet, true);
    mVbar2 = new VConstraintDisplay(mColorSet, false);

    mTopKill = new KillButton(mColorSet);
    mLeftKill = new KillButton(mColorSet);
    mRightKill = new KillButton(mColorSet);
    mBottomKill = new KillButton(mColorSet);
    mBaselineKill = new KillButton(mColorSet);
    mTopConnect = new ConnectButton();
    mLeftConnect = new ConnectButton();
    mRightConnect = new ConnectButton();
    mBottomConnect = new ConnectButton();

    mAspectButton = new AspectButton(mColorSet);
    mAspectText = new JTextField();
    mAspectLabel = new JLabel("ratio");

    mTopKill.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.TOP, false, false, false));
    mLeftKill.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.LEFT, false, false ,false));

    mRightKill.setName("deleteRightConstraintButton");
    mRightKill.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.RIGHT, false, false ,false));

    mBottomKill.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.BOTTOM, false, false ,false));
    mBaselineKill.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.BASELINE, false, false ,false));
    mTopConnect.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.TOP, true, false ,false));
    mLeftConnect.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.LEFT, true, false ,false));
    mRightConnect.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.RIGHT, true, false ,false));
    mBottomConnect.setToolTipText(AnchorTarget.createAnchorToolTips(AnchorTarget.Type.BOTTOM, true, false ,false));
    mAspectButton.setToolTipText("Toggle Aspect Ratio Constraint");

    mHbar1.setSister(mHbar2);
    mHbar2.setSister(mHbar1);
    mVbar1.setSister(mVbar2);
    mVbar2.setSister(mVbar1);

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resize();
      }
    });

    add(mTopMargin);
    add(mLeftMargin);
    add(mRightMargin);
    add(mBottomMargin);
    mTopMargin.addActionListener(e -> myWidgetModel.setMargin(CONNECTION_TOP, mTopMargin.getMargin(myWidgetModel.getComponent())));
    mLeftMargin.addActionListener(e -> myWidgetModel.setMargin(CONNECTION_LEFT, mLeftMargin.getMargin(myWidgetModel.getComponent())));
    mRightMargin.addActionListener(e -> myWidgetModel.setMargin(CONNECTION_RIGHT, mRightMargin.getMargin(myWidgetModel.getComponent())));
    mBottomMargin.addActionListener(e -> myWidgetModel.setMargin(CONNECTION_BOTTOM, mBottomMargin.getMargin(myWidgetModel.getComponent())));
    add(mTopKill);
    add(mLeftKill);
    add(mRightKill);
    add(mBottomKill);
    add(mTopConnect);
    add(mLeftConnect);
    add(mRightConnect);
    add(mBottomConnect);
    add(mBaselineKill);
    add(mAspectButton);
    add(mAspectText);
    add(mAspectLabel);

    add(mHbar1);
    add(mHbar2);
    add(mVbar1);
    add(mVbar2);

    mTopKill.addActionListener(e -> topKill());
    mLeftKill.addActionListener(e -> leftKill());
    mRightKill.addActionListener(e -> rightKill());
    mBottomKill.addActionListener(e -> bottomKill());
    mBaselineKill.addActionListener(e -> baselineKill());
    mTopConnect.addActionListener(e -> connectConstraint(CONNECTION_TOP));
    mLeftConnect.addActionListener(e -> connectConstraint(CONNECTION_LEFT));
    mRightConnect.addActionListener(e -> connectConstraint(CONNECTION_RIGHT));
    mBottomConnect.addActionListener(e -> connectConstraint(CONNECTION_BOTTOM));
    mAspectButton.addActionListener(e -> toggleAspect());
    mAspectText.addActionListener(e -> setAspectString());
    mAspectText.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        setAspectString();
      }
    });

    mHbar1.addPropertyChangeListener(TriStateControl.STATE, e -> setHorizontalState(mHbar1));
    mHbar2.addPropertyChangeListener(TriStateControl.STATE, e -> setHorizontalState(mHbar2));
    mVbar1.addPropertyChangeListener(TriStateControl.STATE, e -> setVerticalState(mVbar1));
    mVbar2.addPropertyChangeListener(TriStateControl.STATE, e -> setVerticalState(mVbar2));

    registerAttributeHelp(mTopMargin, () -> myWidgetModel.getMarginAttribute(CONNECTION_TOP));
    registerAttributeHelp(mBottomMargin, () -> myWidgetModel.getMarginAttribute(CONNECTION_BOTTOM));
    registerAttributeHelp(mLeftMargin, () -> myWidgetModel.getMarginAttribute(CONNECTION_LEFT));
    registerAttributeHelp(mRightMargin, () -> myWidgetModel.getMarginAttribute(CONNECTION_RIGHT));

    mGraphicList.add(mWidgetRender);
    myInitialized = true;
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (myInitialized) {  // updateUI() will be called from the JPanel constructor
      // Make sure we adapt the size and position after a LaF change:
      resize();
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return new JBDimension((DROP_DOWN_WIDTH + DROPDOWN_OFFSET) * 2 + BOX_SIZE,
                           (DROP_DOWN_HEIGHT + DROPDOWN_OFFSET) * 2 + BOX_SIZE);
  }

  private void setAspectString() {
    String sideRatioString = "";
    if (mRatioString != null && mRatioString.contains(",")) {
      sideRatioString = mRatioString.substring(0, mRatioString.indexOf(',') + 1);
    }
    mRatioString = sideRatioString + mAspectText.getText();
    myWidgetModel.setAspect(mRatioString);
  }

  private static String getRatioPart(String str) {
    if (str == null) {
      return "1:1";
    }
    int index = str.indexOf(',');
    if (index == -1) {
      return str;
    }
    return str.substring(index + 1);
  }

  private void toggleAspect() {
    int[] order = new int[4];
    int count = 0;
    order[count++] = RATIO_UNLOCK;

    if (mCacheHeight == MATCH_CONSTRAINT && mCacheWidth == MATCH_CONSTRAINT) {
      order[count++] = RATIO_LOCK;
    }
    if (mCacheHeight == MATCH_CONSTRAINT) {
      order[count++] = RATIO_LOCK_HEIGHT;
    }
    if (mCacheWidth == MATCH_CONSTRAINT) {
      order[count++] = RATIO_LOCK_WIDTH;
    }

    int lock = RATIO_UNLOCK;
    for (int i = 0; i < count; i++) {
      if (mRatioLock == order[i]) {
        lock = order[(i + 1) % count];
        break;
      }
    }
    mRatioLock = lock;

    switch (mRatioLock) {
      case RATIO_LOCK_WIDTH:
        mRatioString = "w," + getRatioPart(mRatioString);
        break;
      case RATIO_LOCK:
        mRatioString = getRatioPart(mRatioString);
        break;
      case RATIO_LOCK_HEIGHT:
        mRatioString = "h," + getRatioPart(mRatioString);
        break;
      case RATIO_UNLOCK:
        mRatioString = null;
        break;
    }
    myWidgetModel.setAspect(mRatioString);
  }

  private void setHorizontalState(HConstraintDisplay state) {
    if (state == mHbar1) {
      mHbar2.setState(state.getState());
    }
    else {
      mHbar1.setState(state.getState());
    }
    updateTriangle();
    mHbar1.setToolTipText(statusString[state.getState()]);
    mHbar2.setToolTipText(statusString[state.getState()]);
    myWidgetModel.setHorizontalConstraint(state.getState());
  }

  private void setVerticalState(VConstraintDisplay state) {
    if (state == mVbar1) {
      mVbar2.setState(state.getState());
    }
    else {
      mVbar1.setState(state.getState());
    }
    updateTriangle();
    mVbar1.setToolTipText(statusString[state.getState()]);
    mVbar2.setToolTipText(statusString[state.getState()]);
    myWidgetModel.setVerticalConstraint(state.getState());
  }

  private void updateTriangle() {
    boolean show = mVbar1.getState() == MATCH_CONSTRAINT || mHbar1.getState() == MATCH_CONSTRAINT;
    mWidgetRender.mAspectLock.setShowTriangle(show);
  }

  private void topKill() {
    myWidgetModel.killConstraint(ConstraintAnchorConstants.Type.TOP);
  }

  private void leftKill() {
    myWidgetModel.killConstraint(ConstraintAnchorConstants.Type.LEFT);
  }

  private void rightKill() {
    myWidgetModel.killConstraint(ConstraintAnchorConstants.Type.RIGHT);
  }

  private void bottomKill() {
    myWidgetModel.killConstraint(ConstraintAnchorConstants.Type.BOTTOM);
  }

  private void baselineKill() {
    myWidgetModel.killBaselineConstraint();
    mCacheBaseline = false;
  }

  private void connectConstraint(int connectionType) {
    NlComponent component = myWidgetModel.getComponent();
    if (component != null) {
      boolean rtl = ConstraintUtilities.isInRTL(component);
      Scout.Arrange arrange;
      switch (connectionType) {
        case CONNECTION_LEFT:
          arrange = rtl ? Scout.Arrange.ConnectEnd : Scout.Arrange.ConnectStart;
          break;
        case CONNECTION_RIGHT:
          arrange = rtl ? Scout.Arrange.ConnectStart : Scout.Arrange.ConnectEnd;
          break;
        case CONNECTION_TOP:
          arrange = Scout.Arrange.ConnectTop;
          break;
        case CONNECTION_BOTTOM:
          arrange = Scout.Arrange.ConnectBottom;
          break;
        default:
          return;
      }
      component.clearTransaction();
      Scout.arrangeWidgets(arrange, Collections.singletonList(component), false);
      ComponentModification modification = new ComponentModification(component, "Connect Constraint");
      component.startAttributeTransaction().applyToModification(modification);
      modification.commit();
    }
  }

  static int baselinePos(int height) {
    return (9 * height) / 10;
  }

  private void resize() {
    mWidth = getWidth();
    mHeight = getHeight();
    int mBoxSize = JBUI.scale(BOX_SIZE);

    int boxLeft = (mWidth - mBoxSize) / 2;
    int boxTop = (mHeight - mBoxSize) / 2;
    int boxRight = boxLeft + mBoxSize;

    mWidgetRender.build(boxLeft, boxTop, mBoxSize);

    int dropDownWidth = JBUI.scale(DROP_DOWN_WIDTH);
    int dropDownHeight = JBUI.scale(DROP_DOWN_HEIGHT);
    int dropDownOffset = JBUI.scale(DROPDOWN_OFFSET);
    mTopMargin.setBounds(mWidth / 2 - dropDownWidth / 2, boxTop - dropDownOffset - dropDownHeight, dropDownWidth, dropDownHeight);
    mLeftMargin.setBounds(boxLeft - dropDownOffset - dropDownWidth, (mHeight - dropDownHeight) / 2, dropDownWidth, dropDownHeight);
    mRightMargin.setBounds(boxRight + dropDownOffset, (mHeight - dropDownHeight) / 2, dropDownWidth, dropDownHeight);
    mBottomMargin.setBounds(mWidth / 2 - dropDownWidth / 2, boxTop + mBoxSize + dropDownOffset, dropDownWidth, dropDownHeight);
    int rad = JBUI.scale(KILL_BUTTON_SIZE);
    int size = rad * 2;
    int boxCenter = (int)(mBoxSize / 2f + 0.5);
    int centerX = boxLeft + boxCenter;
    int centerY = boxTop + boxCenter;
    mTopKill.setBounds(centerX - rad, boxTop - rad, size, size);
    mLeftKill.setBounds(boxLeft - rad, centerY - rad, size, size);
    mRightKill.setBounds(boxRight - rad, centerY - rad, size, size);
    mBottomKill.setBounds(centerX - rad, boxTop + mBoxSize - rad, size, size);
    mBaselineKill.setBounds(centerX - rad, boxTop + baselinePos(mBoxSize) - rad, size, size);

    mTopConnect.setLocation(
      centerX - Math.round(mTopConnect.getPreferredSize().width / 2f),
      boxTop - mTopConnect.getPreferredSize().height - dropDownOffset);
    mLeftConnect.setLocation(
      boxLeft - mLeftConnect.getPreferredSize().width - dropDownOffset,
      centerY - Math.round(mLeftConnect.getPreferredSize().height / 2f));
    mRightConnect.setLocation(
      boxRight + dropDownOffset,
      centerY - Math.round(mRightConnect.getPreferredSize().height / 2f));
    mBottomConnect.setLocation(
      centerX - Math.round(mTopConnect.getPreferredSize().width / 2f),
      boxTop + mBoxSize + dropDownOffset);
    mAspectButton.setBounds(boxLeft, boxTop, mBoxSize / ASPECT_BUTTON_BOX_SIZE_RATIO, mBoxSize / ASPECT_BUTTON_BOX_SIZE_RATIO);

    mAspectText.setBounds(mRightMargin.getX(), mBottomConnect.getY(), dropDownWidth, dropDownHeight);
    Dimension labelSize = mAspectLabel.getPreferredSize();
    mAspectLabel.setBounds(boxRight + dropDownOffset, mAspectText.getY() - labelSize.height, labelSize.width, labelSize.height);

    int barMargin = JBUI.scale(BAR_OUTSIDE_MARGINS);
    int barLong = mBoxSize / 2 - barMargin - JBUI.scale(MIDDLE_SPACE);

    int scaledConstraintLength = JBUI.scale(CONSTRAINT_LENGTH);
    centerY = boxTop + (mBoxSize - scaledConstraintLength) / 2;
    centerX = boxLeft + (mBoxSize - scaledConstraintLength) / 2;
    mHbar1.setBounds(boxLeft + barMargin, centerY, barLong, scaledConstraintLength);
    mHbar2.setBounds(boxRight - barLong - barMargin, centerY, barLong, scaledConstraintLength);
    mVbar1.setBounds(centerX, boxTop + barMargin, scaledConstraintLength, barLong);
    if (mCacheBaseline) {
      int top = boxTop + mBoxSize / 2 + scaledConstraintLength;
      int height = boxTop + baselinePos(mBoxSize) - top - 2;
      mVbar2.setBounds(centerX, top, scaledConstraintLength + 1, height);
    }
    else {
      mVbar2.setBounds(centerX, boxTop + mBoxSize - barMargin - barLong, scaledConstraintLength, barLong);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (mWidth != getWidth() || mHeight != getHeight()) {
      resize();
    }
    Graphics2D g2d = (Graphics2D)g;

    for (Graphic graphic : mGraphicList) {
      graphic.paint(g2d, mColorSet);
    }
  }

  /**
   * Buttons that can kill the constraint
   */
  static class AspectButton extends JComponent {
    private boolean mMouseIn;
    private final ColorSet mColorSet;
    private final Color mColor;
    private final int[] mXPoints = new int[3];
    private final int[] mYPoints = new int[3];

    private ActionListener mListener;

    @Override
    public void paint(Graphics g) {
      if (mMouseIn) {
        icon.paintIcon(this, g, 0, 0);
      }
    }

    private AspectButton(ColorSet colorSet) {
      mColorSet = colorSet;
      //noinspection UseJBColor
      mColor = new Color(mColorSet.getInspectorFillColor().getRGB() & 0x88FFFFFF, true);
      setOpaque(false);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent evt) {
          mMouseIn = true;
          repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          mListener.actionPerformed(null);
        }

        @Override
        public void mouseExited(MouseEvent evt) {
          mMouseIn = false;
          repaint();
        }
      });
    }

    @Override
    public void updateUI() {
      super.updateUI();
      Dimension size = new JBDimension(CIRCLE_CONTROL_RADIUS * 2, CIRCLE_CONTROL_RADIUS * 2);
      setPreferredSize(size);
      setSize(size);
    }

    final Icon icon = new Icon() {

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(JBColor.BLUE);
        mXPoints[0] = 1;
        mYPoints[0] = 1;
        mXPoints[1] = getIconWidth();
        mYPoints[1] = 1;
        mXPoints[2] = 1;
        mYPoints[2] = getIconHeight();
        if (mMouseIn) {
          g.setColor(mColor);
          g.fillPolygon(mXPoints, mYPoints, 3);
        }
      }

      @Override
      public int getIconWidth() {
        return getWidth();
      }

      @Override
      public int getIconHeight() {
        return getHeight();
      }
    };

    public void addActionListener(ActionListener listener) {
      mListener = listener;
    }
  }

  /**
   * Connect button
   */
  static class ConnectButton extends JLabel {

    private static final Icon ICON = StudioIcons.LayoutEditor.Properties.ADD_CONNECTION;

    public void addActionListener(ActionListener listener) {
      mListener = listener;
    }

    private ActionListener mListener;

    private ConnectButton() {
      setOpaque(false);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent evt) {
          repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          mListener.actionPerformed(null);
        }

        @Override
        public void mouseExited(MouseEvent evt) {
          repaint();
        }
      });
    }

    @Override
    public void updateUI() {
      super.updateUI();
      // The ICON size may change during a LaF change:
      Dimension size = new Dimension(ICON.getIconWidth(), ICON.getIconHeight());
      setPreferredSize(size);
      setSize(size);
    }

    @Override
    public void paint(Graphics g) {
      ICON.paintIcon(this, g, 0, 0);
    }
  }

  /**
   * Buttons that can kill the constraint
   */
  @SuppressWarnings("SameParameterValue")
  public static class KillButton extends JComponent {
    boolean mMouseIn;
    final ColorSet mColorSet;

    private ActionListener mListener;
    private static final int CROSS_BAR_SIZE = KILL_BUTTON_SIZE * 2 - 1;

    @Override
    public void paint(Graphics g) {
      if (mMouseIn) {
        icon.paintIcon(this, g, 0, 0);
      }
    }

    public KillButton(ColorSet colorSet) {
      mColorSet = colorSet;
      setOpaque(false);

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent evt) {
          mMouseIn = true;
          repaint();
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          mListener.actionPerformed(null);
        }

        @Override
        public void mouseExited(MouseEvent evt) {
          mMouseIn = false;
          repaint();
        }
      });
    }

    @Override
    public void updateUI() {
      super.updateUI();
      // The ICON size may change during a LaF change:
      setPreferredSize(JBUI.size(KILL_BUTTON_SIZE * 2));
    }

    final Icon icon = new Icon() {

      private final BasicStroke myStroke = new BasicStroke(1);

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(JBColor.BLUE);
        if (mMouseIn) {
          drawCircle((Graphics2D)g, x + JBUI.scale(KILL_BUTTON_SIZE), y + JBUI.scale(KILL_BUTTON_SIZE));
        }
      }

      /**
       * Draw a circle representing the connection
       * @param g     graphics context
       * @param x      x coordinate of the circle
       * @param y      y coordinate of the circle
       */
      private void drawCircle(Graphics2D g, int x, int y) {
        g.setColor(mColorSet.getInspectorConstraintColor());
        int radius = JBUI.scale(KILL_BUTTON_SIZE);
        int size = radius * 2;
        g.drawRoundRect(x - radius, y - radius,
                        size, size, size, size);
        g.fillRoundRect(x - radius, y - radius,
                        size, size, size, size);


        g.setColor(mColorSet.getInspectorBackgroundColor());
        g.setStroke(myStroke);

        int crossBarSize = JBUI.scale(CROSS_BAR_SIZE);
        g.drawLine(x - crossBarSize, y - crossBarSize, x + crossBarSize, y + crossBarSize);
        g.drawLine(x - crossBarSize, y + crossBarSize, x + crossBarSize, y - crossBarSize);
      }

      @Override
      public int getIconWidth() {
        return JBUI.scale(KILL_BUTTON_SIZE * 2 + 2);
      }

      @Override
      public int getIconHeight() {
        return JBUI.scale(KILL_BUTTON_SIZE * 2);
      }
    };

    public void addActionListener(ActionListener listener) {
      mListener = listener;
    }
  }

  /**
   * @param bottom      sets the margin -1 = no margin
   * @param top         sets the margin -1 = no margin
   * @param left        sets the margin -1 = no margin
   * @param right       sets the margin -1 = no margin
   * @param baseline    sets the name of baseline connection null = no baseline
   * @param width       the horizontal constraint state 0,1,2 = FIXED, SPRING, WRAP respectively
   * @param height      the vertical constraint state 0,1,2 = FIXED, SPRING, WRAP respectively
   * @param ratioString The side that will be constrained
   */
  public void configureUi(int bottom, int top, int left, int right, boolean baseline, int width, int height, String ratioString) {
    mRatioString = ratioString;
    parseDimensionRatio(ratioString);
    String aspectText = "";
    if (ratioString != null) {
      if (ratioString.contains(",")) {
        aspectText = ratioString.substring(ratioString.indexOf(',') + 1);
        if (Character.toLowerCase(ratioString.charAt(0)) == 'w') {
          mRatioLock = RATIO_LOCK_WIDTH;
        }
        else {
          mRatioLock = RATIO_LOCK_HEIGHT;
        }
      }
      else {
        aspectText = ratioString;
        mRatioLock = RATIO_LOCK;
      }
    }
    else {
      mRatioLock = RATIO_UNLOCK;
    }
    if (mRatioHeight == -1 && mDimensionRatio > 0) { // it is of the form "[WH],float" see if you can get a nice ratio
      int[] split = splitRatio(mDimensionRatio);
      if (split != null) {
        mRatioWidth = split[1];
        mRatioHeight = split[0];
      }
    }
    mAspectText.setText(aspectText);
    configureUi(bottom, top, left, right, baseline, width, height);
  }

  private void configureUi(int bottom, int top, int left, int right, boolean baseline, int width, int height) {
    mCacheBaseline = baseline;
    mCacheWidth = width;
    mCacheHeight = height;
    mTopMargin.setVisible(top != UNCONNECTED);
    mLeftMargin.setVisible(left != UNCONNECTED);
    mRightMargin.setVisible(right != UNCONNECTED);
    mBottomMargin.setVisible(bottom != UNCONNECTED);
    mTopMargin.setMargin(top);
    mLeftMargin.setMargin(left);
    mRightMargin.setMargin(right);
    mBottomMargin.setMargin(bottom);
    mWidgetRender.setConstraints(left, top, right, bottom);
    mWidgetRender.mBaseline = baseline;
    mTopKill.setVisible(top != UNCONNECTED);
    mLeftKill.setVisible(left != UNCONNECTED);
    mRightKill.setVisible(right != UNCONNECTED);
    mBottomKill.setVisible(bottom != UNCONNECTED);
    mTopConnect.setVisible(top == UNCONNECTED);
    mLeftConnect.setVisible(left == UNCONNECTED);
    mRightConnect.setVisible(right == UNCONNECTED);
    mBottomConnect.setVisible(bottom == UNCONNECTED);
    mBaselineKill.setVisible(baseline);
    mAspectButton.setVisible(true);
    mAspectText.setVisible(mRatioString != null);
    mAspectLabel.setVisible(mRatioString != null);

    mHbar1.setState(width);
    mHbar2.setState(width);
    mVbar1.setState(height);
    mVbar2.setState(height);

    mHbar1.setVisible(mDimensionRatioSide != ConstraintWidgetConstants.HORIZONTAL);
    mHbar2.setVisible(mDimensionRatioSide != ConstraintWidgetConstants.HORIZONTAL);
    mVbar1.setVisible(mDimensionRatioSide != ConstraintWidgetConstants.VERTICAL);
    mVbar2.setVisible(mDimensionRatioSide != ConstraintWidgetConstants.VERTICAL);


    mVbar1.setToolTipText(statusString[height]);
    mVbar2.setToolTipText(statusString[height]);
    mHbar1.setToolTipText(statusString[width]);
    mHbar2.setToolTipText(statusString[width]);
    resize();
    repaint();
  }

  /**
   * Set the ratio of the widget from a given string of format [H|V],[float|x:y] or [float|x:y]
   *
   * @param ratio
   */
  private void parseDimensionRatio(String ratio) {
    mRatioHeight = -1;
    mRatioWidth = -1;
    if (ratio == null || ratio.isEmpty()) {
      mDimensionRatio = 0;
      mDimensionRatioSide = ConstraintWidgetConstants.UNKNOWN;

      return;
    }
    int dimensionRatioSide = ConstraintWidgetConstants.UNKNOWN;
    float dimensionRatio = 0;
    int len = ratio.length();
    int commaIndex = ratio.indexOf(',');
    if (commaIndex > 0 && commaIndex < len - 1) {
      String dimension = ratio.substring(0, commaIndex);
      if (dimension.equalsIgnoreCase("W")) {
        dimensionRatioSide = ConstraintWidgetConstants.HORIZONTAL;
      }
      else if (dimension.equalsIgnoreCase("H")) {
        dimensionRatioSide = ConstraintWidgetConstants.VERTICAL;
      }
      commaIndex++;
    }
    else {
      commaIndex = 0;
    }
    int colonIndex = ratio.indexOf(':');

    if (colonIndex >= 0 && colonIndex < len - 1) {
      String nominator = ratio.substring(commaIndex, colonIndex);
      String denominator = ratio.substring(colonIndex + 1);
      if (!nominator.isEmpty() && !denominator.isEmpty()) {
        try {
          float nominatorValue = Float.parseFloat(nominator);
          float denominatorValue = Float.parseFloat(denominator);
          dimensionRatio = Math.abs(nominatorValue / denominatorValue);
          mRatioHeight = (int)nominatorValue;
          mRatioWidth = (int)denominatorValue;
        }
        catch (NumberFormatException e) {
          // Ignore
        }
      }
    }
    else {
      String r = ratio.substring(commaIndex);
      if (!r.isEmpty()) {
        try {
          dimensionRatio = Float.parseFloat(r);
        }
        catch (NumberFormatException e) {
          // Ignore
        }
      }
    }

    if (dimensionRatio > 0) {
      mDimensionRatio = dimensionRatio;
      mDimensionRatioSide = dimensionRatioSide;
    }
  }

  private static final int[][] ratios = {{1, 1}, {4, 3}, {3, 2}, {5, 3}, {16, 9}, {2, 1}, {21, 9}, {5, 2}, {3, 1}, {4, 1}};

  static {
    Arrays.sort(ratios, (a, b) -> Float.compare(a[0] / (float)a[1], b[0] / (float)b[1]));
  }

  // use to split the ratios
  private static int[] splitRatio(float ratio) {
    if (ratio >= 1) {
      for (int[] r : ratios) {
        if (r[0] / (float)r[1] >= ratio) {
          return r;
        }
      }
    }
    else {
      for (int[] r : ratios) {
        if (r[1] / (float)r[0] <= ratio) {
          return r;
        }
      }
    }
    return null;
  }

  /**
   * Interface to widgets drawn on the screen
   */
  interface Graphic {
    void paint(Graphics2D g, ColorSet colorSet);
  }

  static class Box implements Graphic {
    final int mX;
    final int mY;
    final int mWidth;
    final int mHeight;

    Box(int x, int y, int w, int h) {
      mX = x;
      mY = y;
      mHeight = h;
      mWidth = w;
    }

    @Override
    public void paint(Graphics2D g, ColorSet colorSet) {
      g.setColor(colorSet.getInspectorFillColor());
      g.fillRect(mX, mY, mWidth + 1, mHeight + 1);
      g.setColor(colorSet.getInspectorStrokeColor());
      g.drawRect(mX, mY, mWidth, mHeight);
    }
  }

  static class BaseLineBox extends Box {
    final boolean mBaseline;

    BaseLineBox(int x, int y, int w, int h, boolean baseline) {
      super(x, y, w, h);
      mBaseline = baseline;
    }

    @Override
    public void paint(Graphics2D g, ColorSet colorSet) {
      Stroke defaultStroke = g.getStroke();
      g.setColor(colorSet.getInspectorFillColor());
      g.fillRect(mX, mY, mWidth + 1, mHeight + 1);
      g.setColor(colorSet.getInspectorStrokeColor());

      if (mBaseline) {
        g.drawLine(mX, mY, mX, mY + mWidth);
        g.drawLine(mX + mWidth, mY, mX + mWidth, mY + mHeight);

        int y = mY + baselinePos(mHeight);

        g.setStroke(defaultStroke);
        g.drawLine(mX, y, mX + mWidth, y);
      }
      else {
        g.drawRect(mX, mY, mWidth, mHeight);
      }
    }
  }

  static class AspectLock implements Graphic {
    final int mX;
    final int mY;
    final int mWidth;
    final int mHeight;
    final int mLock;
    private final int mRatioHeight;
    private final int mRatioWidth;
    final int[] mXPoints = new int[3];
    final int[] mYPoints = new int[3];
    final BasicStroke mStroke = new BasicStroke(2f);
    private boolean mShowTriangle;

    AspectLock(int x, int y, int w, int h, int lock, int ratioWidth, int ratioHeight) {
      mX = x;
      mY = y;
      mHeight = h;
      mWidth = w;
      mLock = lock;
      mXPoints[0] = mX;
      mYPoints[0] = mY;
      mXPoints[1] = mX + mWidth / 6;
      mYPoints[1] = mY;
      mXPoints[2] = mX;
      mYPoints[2] = mY + mHeight / 6;
      mRatioHeight = ratioHeight;
      mRatioWidth = ratioWidth;
    }

    public void setShowTriangle(boolean show) {
      mShowTriangle = show;
    }

    @Override
    public void paint(Graphics2D g, ColorSet colorSet) {
      if (mShowTriangle) {
        g.setColor(colorSet.getInspectorHighlightsStrokeColor());
        g.drawPolygon(mXPoints, mYPoints, 3);
      }
      if (mLock == RATIO_UNLOCK) {
        return;
      }
      g.setColor(colorSet.getInspectorStrokeColor());
      g.fillPolygon(mXPoints, mYPoints, 3);
      FontMetrics fm = g.getFontMetrics();
      if (mRatioHeight != -1) {
        String str = Integer.toString(mRatioHeight);
        Rectangle2D bounds = fm.getStringBounds(str, g);
        g.drawString(str, mX + mWidth / 12 - (int)(bounds.getWidth() / 2), mY - fm.getDescent());
      }
      if (mRatioWidth != -1) {
        String str = Integer.toString(mRatioWidth);
        Rectangle2D bounds = fm.getStringBounds(str, g);
        g.drawString(str, mX - (int)bounds.getWidth() - 2, mY + fm.getAscent());
      }
      Stroke prevStroke = g.getStroke();
      g.setStroke(mStroke);
      if (mLock == RATIO_LOCK_WIDTH) {
        g.drawLine(mX, mY + 1, mX, mY + mHeight - 1);
        g.drawLine(mX + mWidth, mY + 1, mX + mWidth, mY + mHeight - 1);
        g.drawLine(mX + 1, mY + mHeight / 2, mX + mWidth - 1, mY + mHeight / 2);
      }
      else if (mLock == RATIO_LOCK_HEIGHT) {
        g.drawLine(mX + 1, mY, mX + mWidth - 1, mY);
        g.drawLine(mX + 1, mY + mHeight, mX + mWidth - 1, mY + mHeight);
        g.drawLine(mX + mWidth / 2, mY + 1, mX + mWidth / 2, mY + mHeight - 1);
      }
      g.setStroke(prevStroke);
    }
  }


  static class Line implements Graphic {
    private final int mX1;
    private final int mY1;
    private final int mX2;
    private final int mY2;
    private final boolean mDisplay;

    Line(int x1, int y1, int x2, int y2, boolean display) {
      mX1 = x1;
      mY1 = y1;
      mX2 = x2;
      mY2 = y2;
      mDisplay = display;
    }

    @Override
    public void paint(Graphics2D g, ColorSet colorSet) {
      Stroke stroke = g.getStroke();
      if (mDisplay) {
        drawCircle(g, mX1, mY1);
        g.setStroke(new BasicStroke(JBUI.scale(2f)));
      }
      else {
        Stroke dash = new BasicStroke(JBUI.scale(2f),
                                      BasicStroke.CAP_BUTT,
                                      BasicStroke.JOIN_MITER,
                                      JBUI.scale(2.0f), new float[]{JBUI.scale(2.0f), JBUI.scale(2.0f)}, 0.0f);
        g.setStroke(dash);
      }
      Object antialiazing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      g.drawLine(mX1, mY1, mX2, mY2);
      g.setStroke(stroke);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiazing);
    }
  }

  private static void drawCircle(Graphics2D g, int x, int y) {
    int radius = JBUI.scale(CIRCLE_CONTROL_RADIUS);
    int diameter = radius * 2;
    g.fillRoundRect(x - radius, y - radius, diameter, diameter, diameter, diameter);
  }

  private static class LineArrow implements Graphic {
    final int mX1;
    final int mY1;
    final int mX2;
    final int mY2;
    final boolean mDisplay;
    final int[] mXArrow = new int[3];
    final int[] mYArrow = new int[3];

    LineArrow(int x1, int y1, int x2, int y2, boolean display) {
      mX1 = x1;
      mY1 = y1;
      mX2 = x2;
      mY2 = y2;
      mDisplay = display;
      mXArrow[0] = x2;
      mYArrow[0] = y2;
      mXArrow[1] = x2 - ConnectionDraw.CONNECTION_ARROW_SIZE;
      mYArrow[1] = y2 - ConnectionDraw.ARROW_SIDE;
      mXArrow[2] = x2 + ConnectionDraw.CONNECTION_ARROW_SIZE;
      mYArrow[2] = y2 - ConnectionDraw.ARROW_SIDE;
    }

    @Override
    public void paint(Graphics2D g, ColorSet colorSet) {
      if (mDisplay) {
        int lineTipSpacing = JBUI.scale(2);
        g.drawLine(mX1, mY1, mX2, mY2 - lineTipSpacing);
        g.fillPolygon(mXArrow, mYArrow, 3);
        drawCircle(g, mX1, mY1);
      }
    }
  }

  /**
   * This renders the basic graphic of a Scene
   */
  private class WidgetRender implements Graphic {
    private int mMarginLeft;
    private int mMarginTop;
    private int mMarginRight;
    private int mMarginBottom;
    private boolean mBaseline;
    private Box mWidgetCenter;
    private Line mTopArrow;
    private Line mLeftArrow;
    private Line mRightArrow;
    private Line mBottomArrow;
    private LineArrow mBaselineArrow;
    private AspectLock mAspectLock;

    void setConstraints(int left, int top, int right, int bottom) {
      mMarginTop = top;
      mMarginLeft = left;
      mMarginRight = right;
      mMarginBottom = bottom;
    }

    /**
     * build the widgets used to render the scene
     */
    public void build(int boxLeft, int boxTop, int boxSize) {
      mWidgetCenter = new BaseLineBox(boxLeft, boxTop, boxSize, boxSize, mBaseline);
      mAspectLock = new AspectLock(boxLeft, boxTop, boxSize, boxSize, mRatioLock, mRatioWidth, mRatioHeight);
      int baseArrowX = boxLeft + boxSize / 2;
      mBaselineArrow =
        new LineArrow(baseArrowX, boxTop + baselinePos(boxSize), baseArrowX, boxTop + boxSize / 2, mBaseline);

      int centerY = (int)(boxTop + boxSize / 2.0 + 0.5);
      int centerX = (int)(boxLeft + boxSize / 2.0 + 0.5);
      int dropDownOffset = JBUI.scale(DROPDOWN_OFFSET);
      mTopArrow = new Line(centerX, boxTop, centerX, boxTop - dropDownOffset, (mMarginTop >= 0));
      mLeftArrow = new Line(boxLeft, centerY, boxLeft - dropDownOffset, centerY, (mMarginLeft >= 0));
      mRightArrow = new Line(boxLeft + boxSize, centerY, boxLeft + boxSize + dropDownOffset, centerY, (mMarginRight >= 0));
      mBottomArrow = new Line(centerX, boxTop + boxSize, centerX, boxTop + boxSize + dropDownOffset, (mMarginBottom >= 0));

      updateTriangle();
    }

    @Override
    public void paint(Graphics2D g, ColorSet colorSet) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setColor(mColorSet.getInspectorBackgroundColor());
      g.fillRect(0, 0, JBUI.scale(BOX_SIZE), JBUI.scale(BOX_SIZE));
      g.setColor(mColorSet.getInspectorStrokeColor());
      mWidgetCenter.paint(g, colorSet);
      mAspectLock.paint(g, colorSet);

      mTopArrow.paint(g, colorSet);
      mLeftArrow.paint(g, colorSet);
      mRightArrow.paint(g, colorSet);
      mBottomArrow.paint(g, colorSet);
      mBaselineArrow.paint(g, colorSet);
    }
  }

  /*-----------------------------------------------------------------------*/
  // TriStateControl
  /*-----------------------------------------------------------------------*/
  static class TriStateControl extends JComponent {
    private final static String STATE = "state";
    private boolean mMouseIn;
    int mState;
    private final Color mBackground;
    private final Color mLineColor;
    private final Color mMouseOverColor;
    private TriStateControl mSisterControl;

    TriStateControl(ColorSet colorSet) {
      mBackground = colorSet.getInspectorFillColor();
      mLineColor = colorSet.getInspectorStrokeColor();
      mMouseOverColor = colorSet.getInspectorConstraintColor();

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          int oldValue = mState;
          mState = (mState + 1) % 3;
          firePropertyChange(STATE, oldValue, mState);
          repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          mMouseIn = false;
          if (mSisterControl != null) {
            mSisterControl.mMouseIn = false;
            mSisterControl.repaint();
          }
          repaint();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          mMouseIn = true;
          if (mSisterControl != null) {
            mSisterControl.mMouseIn = true;
            mSisterControl.repaint();
          }
          repaint();
        }
      });
    }

    @Override
    public void updateUI() {
      super.updateUI();
      setPreferredSize(new JBDimension(CONSTRAINT_COMPONENT_WIDTH, CONSTRAINT_COMPONENT_HEIGHT));
    }

    public void setSister(TriStateControl sister) {
      mSisterControl = sister;
    }

    public int getState() {
      return mState;
    }

    public void setState(int state) {
      mState = state;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      int width = getWidth() - 1;
      int height = getHeight() - 1;
      g.setColor(mBackground);
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(mMouseIn ? mMouseOverColor : mLineColor);
      drawState(g, width, height);
    }

    void drawState(Graphics g, int width, int height) {

    }
  }

  /*-----------------------------------------------------------------------*/
  // HConstraintDisplay
  /*-----------------------------------------------------------------------*/

  static class HConstraintDisplay extends TriStateControl {
    final boolean mDirection;

    HConstraintDisplay(ColorSet colorSet, boolean direction) {
      super(colorSet);
      mDirection = direction;
    }

    @Override
    void drawState(Graphics g, int width, int height) {
      int start = 0;
      int pos = height / 2;
      switch (mState) {
        case FIXED:
          drawFixedHorizontalConstraint(g, start, pos, width);
          break;
        case MATCH_CONSTRAINT:
          drawSpringHorizontalConstraint(g, start, pos, width);
          break;
        case WRAP_CONTENT:
          drawWrapHorizontalConstraint(g, start, pos, width, mDirection);
          break;
      }
    }

    /**
     * Utility function to draw an horizontal spring
     *
     * @param g     graphics context
     * @param left  left end
     * @param top   y origin
     * @param right right end
     */

    private static void drawSpringHorizontalConstraint(Graphics g, int left, int top, int right) {
      int width = JBUI.scale(CONSTRAINT_WIDTH);
      int spacing = JBUI.scale(CONSTRAINT_PATTERN_SIZE);
      int length = (right - left);
      int ni = (length / (2 * spacing)) - 1;
      int margin = (length - (ni * 2 * spacing)) / 2;

      g.drawLine(left, top - width, left, top + width);
      g.drawLine(left, top, left + margin, top);
      for (int i = left + margin; i <= right - margin - 2 * spacing; i += 2 * spacing) {
        g.drawLine(i, top, i + spacing, top - spacing);
        g.drawLine(i + spacing, top - spacing, i + spacing, top + spacing);
        g.drawLine(i + spacing, top + spacing, i + 2 * spacing, top);
      }
      g.drawLine(right - margin, top, right, top);
      g.drawLine(right, top - width, right, top + width);
    }

    /**
     * Utility function to draw the wrap horizontal constraint (drawing chevrons)
     *
     * @param g                    graphics context
     * @param left                 left end
     * @param y                    y origin
     * @param right                right end
     * @param directionLeftToRight indicates the direction of the chevrons
     */
    private static void drawWrapHorizontalConstraint(Graphics g, int left, int y, int right,
                                                     boolean directionLeftToRight) {
      int width = JBUI.scale(CONSTRAINT_WIDTH);
      int spacing = width + JBUI.scale(CONSTRAINT_PATTERN_SIZE);
      Graphics2D g2 = (Graphics2D)g;

      if (directionLeftToRight) {
        for (int x = left; x <= right - width; x += spacing) {
          g2.drawLine(x, y - width, x + width, y);
          g2.drawLine(x + width, y, x, y + width);
        }
      }
      else {
        for (int x = right; x >= left + width; x -= spacing) {
          g2.drawLine(x, y - width, x - width, y);
          g2.drawLine(x - width, y, x, y + width);
        }
      }
    }

    /**
     * Utility function to draw a fixed horizontal constraint
     *
     * @param g     graphics context
     * @param left  left end
     * @param y     y origin
     * @param right right end
     */
    private static void drawFixedHorizontalConstraint(Graphics g, int left, int y, int right) {
      int scaledConstraintWidth = JBUI.scale(CONSTRAINT_WIDTH);
      g.drawLine(left, y - scaledConstraintWidth, left, y + scaledConstraintWidth);
      g.drawLine(left, y, right, y);
      g.drawLine(right, y - scaledConstraintWidth, right, y + scaledConstraintWidth);
    }
  }

  /*-----------------------------------------------------------------------*/
  // VConstraintDisplay
  /*-----------------------------------------------------------------------*/

  private static class VConstraintDisplay extends TriStateControl {
    final boolean mDirection;

    VConstraintDisplay(ColorSet colorSet, boolean direction) {
      super(colorSet);
      mDirection = direction;
    }

    @Override
    public void updateUI() {
      super.updateUI();
      // The vertical have the parameters swapped on purpose:
      //noinspection SuspiciousNameCombination
      setPreferredSize(new JBDimension(CONSTRAINT_COMPONENT_HEIGHT, CONSTRAINT_COMPONENT_WIDTH));
    }

    @Override
    void drawState(Graphics g, int width, int height) {
      int start = 0;
      int pos = width / 2;
      switch (mState) {
        case FIXED:
          drawFixedVerticalConstraint(g, start, pos, height);
          break;
        case MATCH_CONSTRAINT:
          drawSpringVerticalConstraint(g, start, pos, height);
          break;
        case WRAP_CONTENT:
          drawWrapVerticalConstraint(g, start, pos, height, mDirection);
          break;
      }
    }

    /**
     * Utility function to draw a vertical spring
     *
     * @param g      graphics context
     * @param top    top end
     * @param x      x origin
     * @param bottom bottom end
     */
    private static void drawSpringVerticalConstraint(Graphics g, int top, int x, int bottom) {
      int width = JBUI.scale(CONSTRAINT_WIDTH);
      int spacing = JBUI.scale(CONSTRAINT_PATTERN_SIZE);
      int h = (bottom - top);
      int ni = (h / (2 * spacing)) - 1;
      int margin = (h - (ni * 2 * spacing)) / 2;

      g.drawLine(x - width, top, x + width, top);
      g.drawLine(x, top, x, top + margin);
      for (int i = top + margin; i <= bottom - margin - 2 * spacing; i += 2 * spacing) {
        g.drawLine(x, i, x + spacing, i + spacing);
        g.drawLine(x + spacing, i + spacing, x - spacing, i + spacing);
        g.drawLine(x - spacing, i + spacing, x, i + 2 * spacing);
      }
      g.drawLine(x, bottom - margin, x, bottom);
      g.drawLine(x - width, bottom, x + width, bottom);
    }

    /**
     * Utility function to draw a vertical constraint
     *
     * @param g      graphics context
     * @param top    top end
     * @param x      x origin
     * @param bottom bottom end
     */
    private static void drawFixedVerticalConstraint(Graphics g, int top, int x, int bottom) {
      int width = JBUI.scale(CONSTRAINT_WIDTH);
      g.drawLine(x - width, top, x + width, top);
      g.drawLine(x, top, x, bottom);
      g.drawLine(x - width, bottom, x + width, bottom);
    }

    /**
     * Utility function to draw the wrap vertical constraint (drawing chevrons)
     *
     * @param g           graphics context
     * @param top         top end
     * @param x           x origin
     * @param bottom      bottom end
     * @param topToBottom indicates the direction of the chevrons
     */
    private static void drawWrapVerticalConstraint(Graphics g, int top, int x, int bottom,
                                                   boolean topToBottom) {
      int width = JBUI.scale(CONSTRAINT_WIDTH);
      int spacing = width + JBUI.scale(CONSTRAINT_PATTERN_SIZE);

      if (topToBottom) {
        for (int y = top; y <= bottom - width; y += spacing) {
          g.drawLine(x - width, y, x, y + width);
          g.drawLine(x + width, y, x, y + width);
        }
      }
      else {
        for (int y = bottom; y >= top + width; y -= spacing) {
          g.drawLine(x - width, y, x, y - width);
          g.drawLine(x + width, y, x, y - width);
        }
      }
    }
  }
}
