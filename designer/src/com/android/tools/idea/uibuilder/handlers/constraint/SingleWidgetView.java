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

import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.TextWidget;
import com.android.tools.sherpa.interaction.MouseInteraction;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.interaction.WidgetMotion;
import com.android.tools.sherpa.interaction.WidgetResize;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.ConstraintWidgetContainer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;

/**
 * Uses a SceneDraw to render an iconic form of the widget
 */
public class SingleWidgetView extends JPanel {
  static Color mLinesColor = new Color(100, 152, 199);
  static Color sStrokeColor = mLinesColor;
  WidgetConstraintPanel mWidgetConstraintPanel;
  ColorSet mColorSet = new InspectorColorSet();

  static class InspectorColorSet extends BlueprintColorSet {
    public InspectorColorSet() {
      mDrawBackground = false;
      mDrawWidgetInfos = true;
    }
  }

  int mWidth;
  int mHeight;
  int mBoxSize;

  WidgetRender mWidgetRender = new WidgetRender();
  ArrayList<Graphic> mGraphicList = new ArrayList<>();
  MarginWidget mTopMargin = new MarginWidget(SwingConstants.LEFT);
  MarginWidget mLeftMargin = new MarginWidget(SwingConstants.CENTER);
  MarginWidget mRightMargin = new MarginWidget(SwingConstants.CENTER);
  MarginWidget mBottomMargin = new MarginWidget(SwingConstants.LEFT);

  KillButton mTopKill = new KillButton(mColorSet);
  KillButton mLeftKill = new KillButton(mColorSet);
  KillButton mRightKill = new KillButton(mColorSet);
  KillButton mBottomKill = new KillButton(mColorSet);
  public SingleWidgetView(WidgetConstraintPanel constraintPanel) {
    super(null);
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
    //mTopKill.addActionListener(al);
    //mLeftKill.addActionListener(al);
    //mRightKill.addActionListener(al);
    //mBottomKill.addActionListener(al);


    setBackground(null);
    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        mTopMargin.showUI(mTopMargin.getBounds().contains(e.getPoint()));
        mLeftMargin.showUI(mLeftMargin.getBounds().contains(e.getPoint()));
        mRightMargin.showUI(mRightMargin.getBounds().contains(e.getPoint()));
        mBottomMargin.showUI(mBottomMargin.getBounds().contains(e.getPoint()));
      }
    });

    mGraphicList.add(mWidgetRender);
  }

  void resize() {
    mWidth = getWidth();
    mHeight = getHeight();
    mWidgetRender.build(mWidth, mHeight);
    mBoxSize = Math.min(mWidth, mHeight) / 2;

    int vgap = 8;
    int hgap = 4;
    int cw = 50;
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
    mTopKill.setBounds(boxLeft + mBoxSize / 2 - rad, boxTop - rad, rad*2, rad*2);
    mLeftKill.setBounds(boxLeft - 5, boxTop + mBoxSize / 2 - rad, rad*2, rad*2);
    mRightKill.setBounds(boxLeft + mBoxSize - rad, boxTop + mBoxSize / 2 - rad, rad*2, rad*2);
    mBottomKill.setBounds(boxLeft + mBoxSize / 2 - rad, boxTop + mBoxSize - rad, rad*2, rad*2);
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
      redraw |= graphic.paint(g2d);
    }
    if (redraw) {
      repaint();
    }

  }

  static class KillButton extends JButton {
    boolean mMouseIn;
    ColorSet mColorSet;
    private static int sCircleRadius = 10;

    static Dimension size = new Dimension(sCircleRadius*2,sCircleRadius*2);
    Icon icon = new Icon() {

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(Color.BLUE);
        if (mMouseIn || hasFocus()) {
          g.fillRoundRect(x, y, size.width, size.height, size.width, size.height);
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
        Graphics2D g = (Graphics2D) g2.create();
        Ellipse2D.Float circle = new Ellipse2D.Float(x - radius, y - radius,
                                                     radius * 2, radius * 2);
        g.setColor(mColorSet.getInspectorStroke());
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
          g.setColor(mColorSet.getInspectorStroke());
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
        return sCircleRadius*2;
      }

      @Override
      public int getIconHeight() {
        return sCircleRadius*2;
      }
    };




    public KillButton(ColorSet colorSet) {
      mColorSet = colorSet;
      setIcon(icon);
      setPreferredSize(size);
      setSize(size);
      //setFocusPainted(false);
      setOpaque(false);
      setContentAreaFilled(false);
      setBorderPainted(false);
      addMouseListener(new java.awt.event.MouseAdapter() {
        @Override
        public void mouseEntered(java.awt.event.MouseEvent evt) {
          mMouseIn = true;
           repaint();
        }

        @Override
        public void mouseExited(java.awt.event.MouseEvent evt) {
          mMouseIn = false;
          repaint();
        }
      });
    }
  }



  /**
   * This configures the display
   *
   * @param name   sets the id of the widget
   * @param bottom sets the margin -1 = no margin
   * @param top    sets the margin -1 = no margin
   * @param left   sets the margin -1 = no margin
   * @param right  sets the margin -1 = no margin
   */
  public void configureUi(String name, int bottom, int top, int left, int right) {
    mTopMargin.setVisible(top >= 0);
    mLeftMargin.setVisible(left >= 0);
    mRightMargin.setVisible(right >= 0);
    mBottomMargin.setVisible(bottom >= 0);
    mTopMargin.setMargin(top);
    mLeftMargin.setMargin(left);
    mRightMargin.setMargin(right);
    mBottomMargin.setMargin(bottom);
    mWidgetRender.mWidgetName = name;
    mWidgetRender.mMarginBottom = bottom;
    mWidgetRender.mMarginTop = top;
    mWidgetRender.mMarginLeft = left;
    mWidgetRender.mMarginRight = right;
  }

  /**
   * Interface to widgets drawn on the screen
   */
  interface Graphic {
    boolean paint(Graphics2D g);

    boolean click(Point p);
  }

  /**
   * This renders the basic graphic of a Scene
   */
  class WidgetRender implements Graphic {
    private MouseInteraction mMouseInteraction;
    private SceneDraw mSceneDraw;
    ViewTransform mViewTransform = new ViewTransform();
    WidgetsScene mWidgetsScene;
    ConstraintWidgetContainer mRoot;
    int mMarginLeft;
    int mMarginTop;
    int mMarginRight;
    int mMarginBottom;
    String mWidgetName = "";
    ConstraintWidget mWidgetCenter;
    ConstraintWidget mWidgetLeft;
    ConstraintWidget mWidgetRight;
    ConstraintWidget mWidgetTop;
    ConstraintWidget mWidgetBottom;

    void setConstraints(int left, int top, int right, int bottom) {
      mMarginLeft = left;
      mMarginTop = top;
      mMarginRight = right;
      mMarginBottom = bottom;
      connect();
    }

    void connect() {
      mWidgetCenter.resetAnchors();
      if (mMarginLeft >= 0) {
        mWidgetCenter.connect(ConstraintAnchor.Type.LEFT, mWidgetLeft, ConstraintAnchor.Type.RIGHT, mMarginLeft);
      }
      if (mMarginTop >= 0) {
        mWidgetCenter.connect(ConstraintAnchor.Type.TOP, mWidgetTop, ConstraintAnchor.Type.BOTTOM, mMarginTop);
      }
      if (mMarginRight >= 0) {
        mWidgetCenter.connect(ConstraintAnchor.Type.RIGHT, mWidgetRight, ConstraintAnchor.Type.LEFT, mMarginRight);
      }
      if (mMarginBottom >= 0) {
        mWidgetCenter.connect(ConstraintAnchor.Type.BOTTOM, mWidgetBottom, ConstraintAnchor.Type.TOP, mMarginBottom);
      }
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

      mWidgetCenter = addWidget(mWidgetName, boxLeft, boxTop, mBoxSize, mBoxSize);

      mWidgetRight = addWidget(" ", width - inset, boxTop, mBoxSize, mBoxSize);
      mWidgetBottom = addWidget("  ", boxLeft, height - inset, mBoxSize, mBoxSize);
      mWidgetLeft = addWidget("   ", inset - mBoxSize, boxTop, mBoxSize, mBoxSize);
      mWidgetTop = addWidget("    ", boxLeft, inset - mBoxSize, mBoxSize, mBoxSize);


      Selection mSelection = new Selection(null);
      WidgetMotion mWidgetMotion = new WidgetMotion(mWidgetsScene, mSelection);
      WidgetResize mWidgetResize = new WidgetResize();
      mSceneDraw = new SceneDraw(mColorSet, mWidgetsScene, mSelection,
                                 mWidgetMotion, mWidgetResize);
      mSceneDraw.setApplyConstraints(false);
      mSceneDraw.setDrawOutsideShade(true);
      mSceneDraw.setDrawResizeHandle(true);
      mMouseInteraction = new MouseInteraction(mViewTransform,
                                               mWidgetsScene, mSelection,
                                               mWidgetMotion, mWidgetResize,
                                               mSceneDraw, mMouseInteraction);
      connect();
    }

    /**
     * Utility used to build a single widget in the scene
     *
     * @param str
     * @param x
     * @param y
     * @param w
     * @param h
     * @return
     */
    ConstraintWidget addWidget(String str, int x, int y, int w, int h) {
      ConstraintWidget widget = new ConstraintWidget();
      widget.setType("TextView");
      widget.setDebugName(str);
      widget.setOrigin(x, y);
      widget.setWidth(w);
      widget.setHeight(h);
      widget.forceUpdateDrawPosition();
      TextWidget decorator = new TextWidget(widget, str);
      WidgetCompanion companion = new WidgetCompanion();
      companion.addDecorator(decorator);
      companion.setWidgetInteractionTargets(new WidgetInteractionTargets(widget));
      widget.setCompanionWidget(companion);
      decorator.setTextSize(h * 1.2f);
      decorator.applyDimensionBehaviour();
      widget.setWidth(w);
      widget.setHeight(h);
      mRoot.add(widget);
      mWidgetsScene.addWidget(widget);
      return widget;
    }

    @Override
    public boolean paint(Graphics2D g) {

      ViewTransform mViewTransform = new ViewTransform();
      mViewTransform.setTranslate(0, 0);
      mViewTransform.setScale(1);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

      int rootMargin = 0;
      boolean redraw = mSceneDraw.drawBackground(mViewTransform, g,
                                          rootMargin, getWidth(), getHeight());

      redraw |= mSceneDraw.paintWidgets(getWidth(), getHeight(),
                                        mViewTransform, g, true, mMouseInteraction);
      return redraw;
    }

    @Override
    public boolean click(Point p) {
      return false;
    }
  }
}
