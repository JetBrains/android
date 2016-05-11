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

import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.ConstraintWidgetContainer;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.ConnectionDraw;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;

/**
 * Uses a SceneDraw to render an iconic form of the widget
 */
public class SingleWidgetView extends JPanel {
  WidgetConstraintPanel mWidgetConstraintPanel;
  public final static int ANY = 1;
  public final static int WRAP_CONTENT = 2;
  public final static int FIXED = 0;
  public static final int UNCONNECTED = -1;
  private final ColorSet mColorSet;
  private int mCacheBottom;
  private int mCacheTop;
  private int mCacheLeft;
  private int mCacheRight;
  private boolean mCacheBaseline;
  private int mCacheWidth;
  private int mCacheHeight;

  int mWidth;
  int mHeight;
  int mBoxSize;

  WidgetRender mWidgetRender = new WidgetRender();
  ArrayList<Graphic> mGraphicList = new ArrayList<>();
  MarginWidget mTopMargin;
  MarginWidget mLeftMargin;
  MarginWidget mRightMargin;
  MarginWidget mBottomMargin;
  HConstraintDisplay mHbar1;
  HConstraintDisplay mHbar2;
  VConstraintDisplay mVbar1;
  VConstraintDisplay mVbar2;

  KillButton mTopKill;
  KillButton mLeftKill;
  KillButton mRightKill;
  KillButton mBottomKill;
  KillButton mBaselineKill;

  private String[] statusString = {"Fixed", "Any Size", "Wrap Content"};

  public SingleWidgetView(WidgetConstraintPanel constraintPanel, ColorSet colorSet) {
    super(null);
    mColorSet = colorSet;

    mTopMargin = new MarginWidget(SwingConstants.LEFT, mColorSet);
    mLeftMargin = new MarginWidget(SwingConstants.LEFT, mColorSet);
    mRightMargin = new MarginWidget(SwingConstants.LEFT, mColorSet);
    mBottomMargin = new MarginWidget(SwingConstants.LEFT, mColorSet);
    mTopMargin.setToolTipText("Top Margin");
    mLeftMargin.setToolTipText("Left Margin");
    mRightMargin.setToolTipText("Right Margin");
    mBottomMargin.setToolTipText("Bottom Margin");

    mHbar1 = new HConstraintDisplay(mColorSet, true);
    mHbar2 = new HConstraintDisplay(mColorSet, false);
    mVbar1 = new VConstraintDisplay(mColorSet, true);
    mVbar2 = new VConstraintDisplay(mColorSet, false);

    mTopKill = new KillButton(mColorSet);
    mLeftKill = new KillButton(mColorSet);
    mRightKill = new KillButton(mColorSet);
    mBottomKill = new KillButton(mColorSet);
    mBaselineKill = new KillButton(mColorSet);

    mTopKill.setToolTipText("Kill Top Constraint");
    mLeftKill.setToolTipText("Kill Left Constraint");
    mRightKill.setToolTipText("Kill Right Constraint");
    mBottomKill.setToolTipText("Kill Bottom Constraint");
    mBaselineKill.setToolTipText("Kill Baseline Constraint");

    mHbar1.setSister(mHbar2);
    mHbar2.setSister(mHbar1);
    mVbar1.setSister(mVbar2);
    mVbar2.setSister(mVbar1);

    mWidgetConstraintPanel = constraintPanel;
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
    mTopMargin.addActionListener(e -> mWidgetConstraintPanel.setTopMargin(mTopMargin.getMargin()));
    mLeftMargin.addActionListener(e -> mWidgetConstraintPanel.setLeftMargin(mLeftMargin.getMargin()));
    mRightMargin.addActionListener(e -> mWidgetConstraintPanel.setRightMargin(mRightMargin.getMargin()));
    mBottomMargin.addActionListener(e -> mWidgetConstraintPanel.setBottomMargin(mBottomMargin.getMargin()));
    add(mTopKill);
    add(mLeftKill);
    add(mRightKill);
    add(mBottomKill);
    add(mBaselineKill);
    add(mHbar1);
    add(mHbar2);
    add(mVbar1);
    add(mVbar2);

    mTopKill.addActionListener(e -> topKill());
    mLeftKill.addActionListener(e -> leftKill());
    mRightKill.addActionListener(e -> rightKill());
    mBottomKill.addActionListener(e -> bottomKill());
    mBaselineKill.addActionListener(e -> baselineKill());

    mHbar1.addPropertyChangeListener(TriStateControl.STATE, e -> setHorizontalState(mHbar1));
    mHbar2.addPropertyChangeListener(TriStateControl.STATE, e -> setHorizontalState(mHbar2));
    mVbar1.addPropertyChangeListener(TriStateControl.STATE, e -> setVerticalState(mVbar1));
    mVbar2.addPropertyChangeListener(TriStateControl.STATE, e -> setVerticalState(mVbar2));

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        mTopMargin.showUI(mTopMargin.getBounds().contains(e.getPoint()) ? MarginWidget.Show.IN_WIDGET : MarginWidget.Show.OUT_WIDGET);
        mLeftMargin.showUI(mLeftMargin.getBounds().contains(e.getPoint()) ? MarginWidget.Show.IN_WIDGET : MarginWidget.Show.OUT_WIDGET);
        mRightMargin.showUI(mRightMargin.getBounds().contains(e.getPoint()) ? MarginWidget.Show.IN_WIDGET : MarginWidget.Show.OUT_WIDGET);
        mBottomMargin.showUI(mBottomMargin.getBounds().contains(e.getPoint()) ? MarginWidget.Show.IN_WIDGET : MarginWidget.Show.OUT_WIDGET);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        if (getBounds().contains(e.getPoint())) {
          return;
        }
        mTopMargin.showUI(MarginWidget.Show.OUT_PANEL);
        mLeftMargin.showUI(MarginWidget.Show.OUT_PANEL);
        mRightMargin.showUI(MarginWidget.Show.OUT_PANEL);
        mBottomMargin.showUI(MarginWidget.Show.OUT_PANEL);
      }
    });
    mGraphicList.add(mWidgetRender);
  }

  private void setHorizontalState(HConstraintDisplay state) {
    if (state == mHbar1) {
      mHbar2.setState(state.getState());
    }
    else {
      mHbar1.setState(state.getState());
    }
    mHbar1.setToolTipText(statusString[state.getState()]);
    mHbar2.setToolTipText(statusString[state.getState()]);
    mWidgetConstraintPanel.setHorizontalConstraint(state.getState());
  }

  private void setVerticalState(VConstraintDisplay state) {
    if (state == mVbar1) {
      mVbar2.setState(state.getState());
    }
    else {
      mVbar1.setState(state.getState());
    }

    mVbar1.setToolTipText(statusString[state.getState()]);
    mVbar2.setToolTipText(statusString[state.getState()]);
    mWidgetConstraintPanel.setVerticalConstraint(state.getState());
  }

  private void topKill() {
    mWidgetConstraintPanel.killTopConstraint();
    mCacheTop = UNCONNECTED;
    update();
  }

  private void leftKill() {
    mWidgetConstraintPanel.killLeftConstraint();
    mCacheLeft = UNCONNECTED;
    update();
  }

  private void rightKill() {
    mWidgetConstraintPanel.killRightConstraint();
    mCacheRight = UNCONNECTED;
    update();
  }

  private void bottomKill() {
    mWidgetConstraintPanel.killBottomConstraint();
    mCacheBottom = UNCONNECTED;
    update();
  }

  private void baselineKill() {
    mWidgetConstraintPanel.killBaselineConstraint();
    mCacheBaseline = false;
    update();
  }

  static int baselinePos(int height) {
    return (9 * height) / 10;
  }

  private void update() {
    configureUi(mCacheBottom, mCacheTop, mCacheLeft, mCacheRight, mCacheBaseline, mCacheWidth, mCacheHeight);
  }

  void resize() {
    mWidth = getWidth();
    mHeight = getHeight();
    mWidgetRender.build(mWidth, mHeight);
    mBoxSize = Math.min(mWidth, mHeight) / 2;

    int vgap = 8;
    int hgap = 4;
    int cw = 38;
    int ch = 30;
    int inset = 5 + mWidth / 100;
    int boxLeft = (mWidth - mBoxSize) / 2;
    int boxTop = (mHeight - mBoxSize) / 2;
    int vSpace = (mHeight - mBoxSize - inset * 2) / 2;
    int hSpace = (mWidth - mBoxSize - inset * 2) / 2;

    mTopMargin.setBounds(hgap + mWidth / 2, inset + (vSpace - ch) / 2, cw, ch);
    mLeftMargin.setBounds((boxLeft + inset - cw) / 2, vgap + (mHeight - ch) / 2, cw, ch);
    mRightMargin.setBounds(boxLeft + mBoxSize + (hSpace - cw) / 2, vgap + (mHeight - ch) / 2, cw, ch);
    mBottomMargin.setBounds(hgap + mWidth / 2, boxTop + mBoxSize + (vSpace - ch) / 2, cw, ch);
    int rad = 10;
    int size = rad * 2 + 1;
    mTopKill.setBounds(boxLeft + mBoxSize / 2 - rad, boxTop - rad, size + 2, size);
    mLeftKill.setBounds(boxLeft - rad, boxTop + mBoxSize / 2 - rad + 1, size + 2, size);
    mRightKill.setBounds(boxLeft + mBoxSize - rad, boxTop + mBoxSize / 2 - rad + 1, size + 2, size);
    mBottomKill.setBounds(boxLeft + mBoxSize / 2 - rad, boxTop + mBoxSize - rad, size + 2, size);
    mBaselineKill.setBounds(boxLeft + mBoxSize / 2 - rad, boxTop + baselinePos(mBoxSize) - rad, size + 2, size);
    int barSize = 10;
    int barLong = mBoxSize / 2 - barSize - 1;

    mHbar1.setBounds(1 + boxLeft, boxTop + mBoxSize / 2 - barSize / 2 + 1, barLong, barSize);
    mHbar2.setBounds(boxLeft + mBoxSize / 2 + barSize, boxTop + mBoxSize / 2 - barSize / 2 + 1, barLong, barSize);
    mVbar1.setBounds(boxLeft + mBoxSize / 2 - barSize / 2, 1 + boxTop, barSize, barLong);
    if (mCacheBaseline) {
      int left = boxLeft + mBoxSize / 2 - barSize / 2;
      int top = boxTop + mBoxSize / 2 + barSize;
      int height = boxTop + baselinePos(mBoxSize) - top;
      mVbar2.setBounds(left, top, barSize, height);
    }
    else {
      mVbar2.setBounds(boxLeft + mBoxSize / 2 - barSize / 2, boxTop + mBoxSize / 2 + barSize, barSize, barLong);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (mWidth != getWidth() || mHeight != getHeight()) {
      resize();
    }
    Graphics2D g2d = (Graphics2D)g;

    boolean redraw = false;
    for (Graphic graphic : mGraphicList) {
      redraw |= graphic.paint(g2d, mColorSet);
    }
    if (redraw) {
      repaint();
    }
  }

  /**
   * Buttons that can kill the constraint
   */
  static class KillButton extends JComponent {
    boolean mMouseIn;
    boolean mShow = true;
    ColorSet mColorSet;

    private static int sCircleRadius = 10;
    private ActionListener mListener;

    @Override
    public void paint(Graphics g) {
      if (mMouseIn && mShow) {
        icon.paintIcon(this, g, 0, 0);
      }
    }

    public void setShown(boolean show) {
      mShow = show;
    }

    public KillButton(ColorSet colorSet) {
      mColorSet = colorSet;
      setPreferredSize(size);
      setSize(size);
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

    static Dimension size = new Dimension(sCircleRadius * 2, sCircleRadius * 2);
    Icon icon = new Icon() {

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(Color.BLUE);
        if (mMouseIn) {
          drawCircle((Graphics2D)g, x + sCircleRadius, y + sCircleRadius, sCircleRadius, true);
        }
      }

      /**
       * Draw a circle representing the connection
       *
       * @param g2     graphics context
       * @param x      x coordinate of the circle
       * @param y      y coordinate of the circle
       * @param radius radius of the circle
       * @param full   if the circle is full or not
       */
      private void drawCircle(Graphics2D g2, int x, int y, int radius, boolean full) {
        Graphics2D g = (Graphics2D)g2.create();
        Ellipse2D.Float circle = new Ellipse2D.Float(x - radius, y - radius,
                                                     radius * 2, radius * 2);
        g.setColor(mColorSet.getInspectorStrokeColor());
        g.draw(circle);
        g.fill(circle);
        radius -= 1;
        Ellipse2D.Float emptyCircle = new Ellipse2D.Float(x - radius, y - radius,
                                                          radius * 2, radius * 2);
        g.setColor(mColorSet.getInspectorBackgroundColor());
        g.draw(emptyCircle);
        g.fill(emptyCircle);
        if (full) {
          radius -= 2;
          Ellipse2D.Float innerCircle = new Ellipse2D.Float(x - radius, y - radius,
                                                            radius * 2, radius * 2);
          g.setColor(mColorSet.getInspectorStrokeColor());
          g.fill(innerCircle);
          g.draw(innerCircle);
          g.setColor(mColorSet.getInspectorBackgroundColor());
          g.setStroke(new BasicStroke(2));
          radius = 4;
          g.drawLine(x - radius, y - radius, x + radius, y + radius);
          g.drawLine(x - radius, y + radius, x + radius, y - radius);
        }
        g.dispose();
      }

      @Override
      public int getIconWidth() {
        return sCircleRadius * 2 + 2;
      }

      @Override
      public int getIconHeight() {
        return sCircleRadius * 2;
      }
    };

    public void addActionListener(ActionListener listener) {
      mListener = listener;
    }
  }

  /**
   * @param name
   * @param bottom   sets the margin -1 = no margin
   * @param top      sets the margin -1 = no margin
   * @param left     sets the margin -1 = no margin
   * @param right    sets the margin -1 = no margin
   * @param baseline sets the name of baseline connection null = no baseline
   * @param width    the horizontal constraint state 0,1,2 = FIXED, SPRING, WRAP respectively
   * @param height   the vertical constraint state 0,1,2 = FIXED, SPRING, WRAP respectively
   */
  public void configureUi(int bottom, int top, int left, int right, boolean baseline, int width, int height) {
    mCacheBottom = bottom;
    mCacheTop = top;
    mCacheLeft = left;
    mCacheRight = right;
    mCacheBaseline = baseline;
    mCacheWidth = width;
    mCacheHeight = height;
    mTopMargin.setVisible(top != UNCONNECTED);
    mLeftMargin.setVisible(left != UNCONNECTED);
    mRightMargin.setVisible(right != UNCONNECTED);
    mBottomMargin.setVisible(bottom!= UNCONNECTED);
    mTopMargin.setMargin(top);
    mLeftMargin.setMargin(left);
    mRightMargin.setMargin(right);
    mBottomMargin.setMargin(bottom);
    mWidgetRender.mMarginBottom = bottom;
    mWidgetRender.mMarginTop = top;
    mWidgetRender.mMarginLeft = left;
    mWidgetRender.mMarginRight = right;
    mWidgetRender.mBaseline = baseline;
    mTopKill.setVisible(top != UNCONNECTED);
    mLeftKill.setVisible(left != UNCONNECTED);
    mRightKill.setVisible(right != UNCONNECTED);
    mBottomKill.setVisible(bottom != UNCONNECTED);
    mBaselineKill.setVisible(baseline);
    mHbar1.setState(width);
    mHbar2.setState(width);
    mVbar1.setState(height);
    mVbar2.setState(height);
    mVbar1.setToolTipText(statusString[height]);
    mVbar2.setToolTipText(statusString[height]);
    mHbar1.setToolTipText(statusString[width]);
    mHbar2.setToolTipText(statusString[width]);
    mWidgetRender.build(getWidth(), getHeight());
    repaint();
  }

  /**
   * Interface to widgets drawn on the screen
   */
  interface Graphic {
    boolean paint(Graphics2D g, ColorSet colorSet);
  }

  static class Box implements Graphic {
    int mX, mY, mWidth, mHeight;
    int mEdges;
    public final static int TOP = 1;
    public final static int BOTTOM = 2;
    public final static int LEFT = 4;
    public final static int RIGHT = 8;
    public final static int ALL = TOP | BOTTOM | LEFT | RIGHT;

    Box(int x, int y, int w, int h, int edges) {
      mX = x;
      mY = y;
      mHeight = h;
      mWidth = w;
      mEdges = edges;
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      if (mEdges == 0) {
        return false;
      }
      g.setColor(colorSet.getInspectorFillColor());
      g.fillRect(mX, mY, mWidth + 1, mHeight + 1);
      g.setColor(colorSet.getInspectorStrokeColor());
      if (mEdges == ALL) {
        g.drawRect(mX, mY, mWidth, mHeight);
      }
      else {
        if ((mEdges & TOP) != 0) {
          g.drawLine(mX, mY, mX + mWidth, mY);
        }
        if ((mEdges & BOTTOM) != 0) {
          g.drawLine(mX, mY + mHeight, mX + mWidth, mY + mHeight);
        }
        if ((mEdges & LEFT) != 0) {
          g.drawLine(mX, mY, mX, mY + mWidth);
        }
        if ((mEdges & RIGHT) != 0) {
          g.drawLine(mX + mWidth, mY, mX + mWidth, mY + mHeight);
        }

      }
      return false;
    }
  }

  static class BaseLineBox extends Box {
    String mTitle = null;
    boolean mBaseline;
    boolean mDisplay;

    BaseLineBox(String title, int x, int y, int w, int h, boolean baseline, boolean display) {
      super(x, y, w, h, display ? ALL : 0);
      mTitle = title;
      mBaseline = baseline;
      mDisplay = display;
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {

      if (mDisplay) {
        Stroke defaultStroke = g.getStroke();
        g.setColor(colorSet.getInspectorFillColor());
        g.fillRect(mX, mY, mWidth + 1, mHeight + 1);
        g.setColor(colorSet.getInspectorStrokeColor());

        if (mBaseline) {
          g.drawLine(mX, mY, mX, mY + mWidth);
          g.drawLine(mX + mWidth, mY, mX + mWidth, mY + mHeight);
          //g.setStroke(DASHED_STROKE);
          //g.drawLine(mX,mY,mX+mWidth,mY);
          //g.drawLine(mX,mY+mHeight,mX+mWidth,mY+mHeight);

          int y = mY + baselinePos(mHeight);

          g.setStroke(defaultStroke);
          g.drawLine(mX, y, mX + mWidth, y);

        }
        else {
          g.drawRect(mX, mY, mWidth, mHeight);
        }

        if (mTitle != null) {
          int decent = g.getFontMetrics().getDescent();
          g.drawString(mTitle, mX + 2, mY + mHeight - decent);
        }
      }
      return false;
    }

  }

  static class Line implements Graphic {
    int mX1, mY1, mX2, mY2;
    boolean mDisplay;

    Line(int x1, int y1, int x2, int y2, boolean display) {
      mX1 = x1;
      mY1 = y1;
      mX2 = x2;
      mY2 = y2;
      mDisplay = display;
    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      if (mDisplay) {
        g.drawLine(mX1, mY1, mX2, mY2);
      }
      return false;
    }

  }

  static class LineArrow implements Graphic {
    int mX1, mY1, mX2, mY2;
    boolean mDisplay;
    int[] mXArrow = new int[3];
    int[] mYArrow = new int[3];

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
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      if (mDisplay) {
        g.drawLine(mX1, mY1, mX2, mY2 - 2);
        g.fillPolygon(mXArrow, mYArrow, 3);
      }
      return false;
    }

  }

  /**
   * This renders the basic graphic of a Scene
   */
  class WidgetRender implements Graphic {
    WidgetsScene mWidgetsScene;
    ConstraintWidgetContainer mRoot;
    int mMarginLeft;
    int mMarginTop;
    int mMarginRight;
    int mMarginBottom;
    boolean mBaseline;
    Box mWidgetCenter;
    Box mWidgetLeft;
    Box mWidgetRight;
    Box mWidgetTop;
    Box mWidgetBottom;
    Line mTopArrow;
    Line mLeftArrow;
    Line mRightArrow;
    Line mBottomArrow;
    LineArrow mBaselineArrow;

    void setConstraints(int left, int top, int right, int bottom) {
      mMarginTop = top;
      mMarginLeft = left;
      mMarginRight = right;
      mMarginBottom = bottom;
    }

    /**
     * build the widgets used to render the scene
     *
     * @param width
     * @param height
     */
    public void build(int width, int height) {
      mRoot = new ConstraintWidgetContainer();
      mRoot.setCompanionWidget(WidgetCompanion.create(mRoot));
      mRoot.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
      mRoot.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
      mRoot.setOrigin(-1, -1);
      mRoot.setDebugName("       ");
      mRoot.setWidth(width + 1);
      mRoot.setHeight(height + 1);
      mRoot.forceUpdateDrawPosition();
      mWidgetsScene = new WidgetsScene();
      mWidgetsScene.setRoot(mRoot);
      mBoxSize = Math.min(width, height) / 2;

      int inset = 5 + width / 100;
      int boxLeft = (width - mBoxSize) / 2;
      int boxTop = (height - mBoxSize) / 2;

      mWidgetCenter = new BaseLineBox(null, boxLeft, boxTop, mBoxSize, mBoxSize, mBaseline, true);
      mWidgetBottom = new Box(boxLeft, height - inset, mBoxSize, mBoxSize, Box.TOP);
      mWidgetRight = new Box(width - inset, boxTop, mBoxSize, mBoxSize, Box.LEFT);
      mWidgetLeft = new Box(inset - mBoxSize, boxTop, mBoxSize, mBoxSize, Box.RIGHT);
      mWidgetTop = new Box(boxLeft, inset - mBoxSize, mBoxSize, mBoxSize, Box.BOTTOM);

      int baseArrowX = boxLeft + mBoxSize / 2;
      mBaselineArrow =
        new LineArrow(baseArrowX, boxTop + baselinePos(mBoxSize), baseArrowX, height - inset, mBaseline);

      mTopArrow = new Line(width / 2, boxTop, width / 2, inset, (mMarginTop >= 0));
      mLeftArrow = new Line(boxLeft, height / 2, inset, height / 2, (mMarginLeft >= 0));
      mRightArrow = new Line(boxLeft + mBoxSize, height / 2, width - inset, height / 2, (mMarginRight >= 0));
      mBottomArrow = new Line(width / 2, boxTop + mBoxSize, width / 2, height - inset, (mMarginBottom >= 0));

    }

    @Override
    public boolean paint(Graphics2D g, ColorSet colorSet) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setColor(mColorSet.getInspectorBackgroundColor());
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(mColorSet.getInspectorStrokeColor());
      mWidgetCenter.paint(g, colorSet);
      mWidgetLeft.paint(g, colorSet);
      mWidgetRight.paint(g, colorSet);
      mWidgetTop.paint(g, colorSet);
      mWidgetBottom.paint(g, colorSet);

      mTopArrow.paint(g, colorSet);
      mLeftArrow.paint(g, colorSet);
      mRightArrow.paint(g, colorSet);
      mBottomArrow.paint(g, colorSet);
      mBaselineArrow.paint(g, colorSet);

      return false;
    }
  }

  /*-----------------------------------------------------------------------*/
  // TriStateControl
  /*-----------------------------------------------------------------------*/

  static class TriStateControl extends JComponent {
    boolean mMouseIn;
    int mState;
    Color mBackground;
    Color mLineColor;
    Color mMouseOverColor;
    TriStateControl mSisterControl;
    public final static String STATE = "state";

    TriStateControl(ColorSet colorSet) {
      mBackground = colorSet.getInspectorFillColor();
      mLineColor = colorSet.getInspectorStrokeColor();
      mMouseOverColor = colorSet.getInspectorHighlightsStrokeColor();

      setPreferredSize(new Dimension(200, 30));

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
            mSisterControl.mMouseIn = mMouseIn;
            mSisterControl.repaint();
          }
          repaint();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          mMouseIn = true;
          if (mSisterControl != null) {
            mSisterControl.mMouseIn = mMouseIn;
            mSisterControl.repaint();
          }
          repaint();
        }
      });
      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          resize();
        }
      });
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

    void resize() {
    }

    @Override
    protected void paintComponent(Graphics g) {
      int width = getWidth();
      int height = getHeight();
      g.setColor(mBackground);
      g.fillRect(0, 0, getWidth(), getHeight());
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
    boolean mDirection;

    HConstraintDisplay(ColorSet colorSet, boolean direction) {
      super(colorSet);
      mDirection = direction;
      setPreferredSize(new Dimension(200, 30));
    }

    @Override
    void resize() {

    }

    @Override
    void drawState(Graphics g, int width, int height) {
      int start = 5;
      int end = width - 5;
      int pos = height / 2;
      switch (mState) {
        case FIXED:
          drawFixedHorizontalConstraint(g, start, pos, end);
          break;
        case ANY:
          drawSpringHorizontalConstraint(g, start, pos, end);
          break;
        case WRAP_CONTENT:
          drawWrapHorizontalConstraint(g, start, pos, end, mDirection);
          break;
      }
    }

    /**
     * Utility function to draw an horizontal spring
     *
     * @param g graphics context
     * @param l left end
     * @param y y origin
     * @param r right end
     */

    private static void drawSpringHorizontalConstraint(Graphics g, int l, int y, int r) {
      int m = 7;
      int d = 3;
      int w = (r - l);
      int ni = (w / (2 * d)) - 1;
      int margin = (w - (ni * 2 * d)) / 2;

      g.drawLine(l, y - m, l, y + m);
      g.drawLine(l, y, l + margin, y);
      for (int i = l + margin; i <= r - margin - 2 * d; i += 2 * d) {
        g.drawLine(i, y, i + d, y - d);
        g.drawLine(i + d, y - d, i + d, y + d);
        g.drawLine(i + d, y + d, i + 2 * d, y);
      }
      g.drawLine(r - margin, y, r, y);
      g.drawLine(r, y - m, r, y + m);
    }

    /**
     * Utility function to draw the wrap horizontal constraint (drawing chevrons)
     *
     * @param g                    graphics context
     * @param l                    left end
     * @param y                    y origin
     * @param r                    right end
     * @param directionLeftToRight indicates the direction of the chevrons
     */
    private static void drawWrapHorizontalConstraint(Graphics g, int l, int y, int r,
                                                     boolean directionLeftToRight) {
      int d = 4;
      int w = (r - l);
      int ni = (w / (2 * d)) - 1;
      int margin = (w - (ni * 2 * d)) / 2;

      Graphics2D g2 = (Graphics2D)g;
      if (directionLeftToRight) {
        for (int i = l + margin; i <= r - margin; i += 2 * d) {
          g2.drawLine(i, y - d, i + d, y);
          g2.drawLine(i + d, y, i, y + d);
        }
      }
      else {
        for (int i = l + margin; i <= r - margin; i += 2 * d) {
          g2.drawLine(i, y - d, i - d, y);
          g2.drawLine(i - d, y, i, y + d);
        }
      }
    }

    /**
     * Utility function to draw a fixed horizontal constraint
     *
     * @param g graphics context
     * @param l left end
     * @param y y origin
     * @param r right end
     */
    private static void drawFixedHorizontalConstraint(Graphics g, int l, int y, int r) {
      int m = 7;
      g.drawLine(l, y - m, l, y + m);
      g.drawLine(l, y, r, y);
      g.drawLine(r, y - m, r, y + m);
    }

  }

  /*-----------------------------------------------------------------------*/
  // VConstraintDisplay
   /*-----------------------------------------------------------------------*/

  static class VConstraintDisplay extends TriStateControl {
    boolean mDirection;

    VConstraintDisplay(ColorSet colorSet, boolean direction) {
      super(colorSet);
      mDirection = direction;
      setPreferredSize(new Dimension(30, 200));
    }

    @Override
    void drawState(Graphics g, int width, int height) {
      int start = 5;
      int end = height - 5;
      int pos = width / 2;
      switch (mState) {
        case FIXED:
          drawFixedVerticalConstraint(g, start, pos, end);
          break;
        case ANY:
          drawSpringVerticalConstraint(g, start, pos, end);
          break;
        case WRAP_CONTENT:
          drawWrapVerticalConstraint(g, start, pos, end, mDirection);
          break;
      }
    }

    /**
     * Utility function to draw a vertical spring
     *
     * @param g graphics context
     * @param t top end
     * @param x x origin
     * @param b bottom end
     */
    private static void drawSpringVerticalConstraint(Graphics g, int t, int x, int b) {
      int m = 7;
      int d = 3;
      int h = (b - t);
      int ni = (h / (2 * d)) - 1;
      int margin = (h - (ni * 2 * d)) / 2;

      g.drawLine(x - m, t, x + m, t);
      g.drawLine(x, t, x, t + margin);
      for (int i = t + margin; i <= b - margin - 2 * d; i += 2 * d) {
        g.drawLine(x, i, x + d, i + d);
        g.drawLine(x + d, i + d, x - d, i + d);
        g.drawLine(x - d, i + d, x, i + 2 * d);
      }
      g.drawLine(x, b - margin, x, b);
      g.drawLine(x - m, b, x + m, b);
    }

    /**
     * Utility function to draw a vertical constraint
     *
     * @param g graphics context
     * @param t top end
     * @param x x origin
     * @param b bottom end
     */
    private static void drawFixedVerticalConstraint(Graphics g, int t, int x, int b) {
      int m = 7;
      g.drawLine(x - m, t, x + m, t);
      g.drawLine(x, t, x, b);
      g.drawLine(x - m, b, x + m, b);
    }

    /**
     * Utility function to draw the wrap vertical constraint (drawing chevrons)
     *
     * @param g           graphics context
     * @param t           top end
     * @param x           x origin
     * @param b           bottom end
     * @param topToBottom indicates the direction of the chevrons
     */
    private static void drawWrapVerticalConstraint(Graphics g, int t, int x, int b,
                                                   boolean topToBottom) {
      int d = 4;
      int h = (b - t);
      int ni = (h / (2 * d)) - 1;
      int margin = (h - (ni * 2 * d)) / 2;

      if (topToBottom) {
        for (int i = t + margin; i <= b - margin; i += 2 * d) {
          g.drawLine(x - d, i, x, i + d);
          g.drawLine(x, i + d, x + d, i);
        }
      }
      else {
        for (int i = t + margin; i <= b - margin; i += 2 * d) {
          g.drawLine(x - d, i + d, x, i);
          g.drawLine(x, i, x + d, i + d);
        }
      }
    }

  }

}
