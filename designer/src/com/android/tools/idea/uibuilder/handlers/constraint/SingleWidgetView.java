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
import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.TextWidget;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.MouseInteraction;
import com.android.tools.sherpa.interaction.WidgetMotion;
import com.android.tools.sherpa.interaction.WidgetResize;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.tnt.solver.widgets.Animator;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import com.google.tnt.solver.widgets.ConstraintWidget;
import com.google.tnt.solver.widgets.ConstraintWidgetContainer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;

/**
 * Uses a SceneDraw to render an iconic form of the widget
 */
public class SingleWidgetView extends JPanel {
  Color mBackgroundColor = new Color(47, 75, 126);
  static Color mLinesColor = new Color(100, 152, 199);
  static Color sStrokeColor = mLinesColor;

  int mWidth;
  int mHeight;
  int mBoxSize;

  WidgetRender mWidgetRender = new WidgetRender();
  ArrayList<Graphic> mGraphicList = new ArrayList<Graphic>();

  public SingleWidgetView() {
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resize();
      }
    });
    mGraphicList.add(mWidgetRender);
  }

  void resize() {
    mWidth = getWidth();
    mHeight = getHeight();
    mWidgetRender.build(mWidth, mHeight);
    mBoxSize = Math.min(mWidth, mHeight) / 2;
  }

  @Override
  protected void paintComponent(Graphics g) {
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

  /**
   * This configures the display
   * @param name sets the id of the widget
   * @param bottom sets the margin -1 = no margin
   * @param top sets the margin -1 = no margin
   * @param left sets the margin -1 = no margin
   * @param right sets the margin -1 = no margin
   */
  public void configureUi(String name, int bottom, int top, int left, int right) {
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
     * @param width
     * @param height
     */
    public void build(int width, int height) {
      boolean previousAnimationValue = Animator.isAnimationEnabled();
      Animator.setAnimationEnabled(false);

      mRoot = new ConstraintWidgetContainer();
      mRoot.setCompanionWidget(new WidgetDecorator(mRoot));
      mRoot.setHorizontalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
      mRoot.setVerticalDimensionBehaviour(ConstraintWidget.DimensionBehaviour.ANY);
      mRoot.setOrigin(-1, -1);
      mRoot.setDebugName("       ");
      mRoot.setWidth(width + 1);
      mRoot.setHeight(height + 1);
      mRoot.updateDrawPosition();

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
      mSceneDraw = new SceneDraw(new BlueprintColorSet(), mWidgetsScene, mSelection,
                                 mWidgetMotion, mWidgetResize);
      mSceneDraw.setApplyConstraints(false);
      mSceneDraw.setDrawOutsideShade(true);
      mSceneDraw.setDrawResizeHandle(true);
      mMouseInteraction = new MouseInteraction(mViewTransform,
                                               mWidgetsScene, mSelection,
                                               mWidgetMotion, mWidgetResize,
                                               mSceneDraw);
      connect();
      Animator.setAnimationEnabled(previousAnimationValue);
    }

    /**
     * Utility used to build a single widget in the scene
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
      widget.updateDrawPosition();
      TextWidget decorator = new TextWidget(widget, str);
      widget.setCompanionWidget(decorator);
      decorator.setTextSize(h * 1.2f);
      decorator.applyDimensionBehaviour();
      decorator.setShowFakeUI(false);
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
      boolean redraw = false;
      redraw |= mSceneDraw.drawBackground(mViewTransform, g,
                                          rootMargin, getWidth(), getHeight(), null);

      redraw |= mSceneDraw.paintWidgets(getWidth(), getHeight(),
                                        mViewTransform, g, true, mMouseInteraction, false);
      return redraw;
    }

    @Override
    public boolean click(Point p) {
      return false;
    }
  }
}
