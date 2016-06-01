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

import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.AndroidColorSet;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.MouseInteraction;
import com.android.tools.sherpa.interaction.WidgetMotion;
import com.android.tools.sherpa.interaction.WidgetResize;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetsScene;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;

/**
 * Utility class holding the necessary objects to draw a ConstraintModel
 */
class DrawConstraintModel {
  private final ConstraintModel myConstraintModel;

  private final ViewTransform myViewTransform = new ViewTransform();

  private final SceneDraw mySceneDraw;
  private final MouseInteraction myMouseInteraction;

  private boolean mShowFakeUI = true;

  /**
   * Simple helper class to avoid reallocation
   */
  private static class RepaintSurface implements SceneDraw.Repaintable {
    ScreenView myScreenView;

    @Override
    public void repaint() {
      if (myScreenView != null) {
        myScreenView.getSurface().repaint();
      }
    }

    @Override
    public void repaint(int x, int y, int w, int h) {
      myScreenView.getSurface().repaint(x, y, w, h);
    }
  }

  /**
   * Constructor for the DrawConstraintModel
   * @param screenView the ScreenView we are associated with
   * @param constraintModel the ConstraintModel we are associated with
   */
  public DrawConstraintModel(ScreenView screenView, ConstraintModel constraintModel) {
    myConstraintModel = constraintModel;
    WidgetsScene widgetsScene = constraintModel.getScene();
    Selection selection = constraintModel.getSelection();
    RepaintSurface repaintSurface = new RepaintSurface();
    repaintSurface.myScreenView = screenView;

    WidgetMotion widgetMotion = new WidgetMotion(widgetsScene, selection);
    WidgetResize widgetResize = new WidgetResize();
    mySceneDraw = new SceneDraw(new BlueprintColorSet(), widgetsScene, selection,
                                widgetMotion, widgetResize);
    mySceneDraw.setRepaintableSurface(repaintSurface);
    myMouseInteraction = new MouseInteraction(myViewTransform,
                                              widgetsScene, selection,
                                              widgetMotion, widgetResize,
                                              mySceneDraw, null);

    if (screenView.getScreenViewType() == ScreenView.ScreenViewType.NORMAL) {
      mySceneDraw.setColorSet(new AndroidColorSet());
    }
    else {
      mySceneDraw.setColorSet(new BlueprintColorSet());
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Interaction
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Call repaint on the scene draw
   */
  public void repaint() {
    mySceneDraw.repaint();
  }

  /**
   * Handles mouse press in the user interaction with our model
   *
   * @param x x mouse coordinate
   * @param y y mouse coordinate
   */
  public void mousePressed(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    myConstraintModel.allowsUpdate(false);
    if (myMouseInteraction != null) {
      myMouseInteraction.mousePressed(pxToDp(x), pxToDp(y), false);
      myMouseInteraction.setAutoConnect(ConstraintModel.isAutoConnect());
    }
  }

  /**
   * Handles mouse drag in the user interaction with our model
   *
   * @param x x mouse coordinate
   * @param y y mouse coordinate
   */
  public void mouseDragged(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (myMouseInteraction != null) {
      myMouseInteraction.mouseDragged(pxToDp(x), pxToDp(y));
    }
  }

  /**
   * Handles mouse release in the user interaction with our model
   *
   * @param x x mouse coordinate
   * @param y y mouse coordinate
   */
  public void mouseReleased(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (myMouseInteraction != null) {
      myMouseInteraction.mouseReleased(pxToDp(x), pxToDp(y));
    }
    myConstraintModel.renderInLayoutLib();
    myConstraintModel.allowsUpdate(true);
  }

  /**
   * Handles mouse move interactions
   *
   * @param x x mouse coordinate
   * @param y y mouse coordinate
   * @return true if we need to repaint
   */
  public boolean mouseMoved(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (myMouseInteraction != null) {
      myMouseInteraction.mouseMoved(pxToDp(x), pxToDp(y));
    }
    if (mySceneDraw.getCurrentUnderneathAnchor() != null) {
      return true;
    }
    return false;
  }

  /**
   * Update the key modifiers mask
   *
   * @param modifiers new mask
   */
  public void updateModifiers(int modifiers) {
    myMouseInteraction.setIsControlDown(((modifiers & InputEvent.CTRL_DOWN_MASK) != 0)
                                      || ((modifiers & InputEvent.CTRL_MASK) != 0));
    myMouseInteraction.setIsShiftDown(((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0)
                                      || ((modifiers & InputEvent.SHIFT_MASK) != 0));
    myMouseInteraction.setIsAltDown(((modifiers & InputEvent.ALT_DOWN_MASK) != 0)
                                      || ((modifiers & InputEvent.ALT_MASK) != 0));
  }

  public MouseInteraction getMouseInteraction() {
    return myMouseInteraction;
  }

  //////////////////////////////////////////////////////////////////////////////
  // Painting
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Paint ourselves and our children
   *
   * @param gc                 the graphic context
   * @param width              width of the canvas
   * @param height             height of the canvas
   * @param showAllConstraints flag to show or not all the existing constraints
   */
  public boolean paint(@NotNull Graphics2D gc,
                       int width,
                       int height,
                       boolean showAllConstraints) {
    Graphics2D g = (Graphics2D)gc.create();
    WidgetDecorator.setShowFakeUI(mShowFakeUI);
    if (mySceneDraw.getCurrentStyle() == WidgetDecorator.BLUEPRINT_STYLE) {
      mySceneDraw.drawBackground(myViewTransform, g, width, height);
    }
    if (myConstraintModel.getNeedsAnimateConstraints() != -1) {
      mySceneDraw.animateConstraints(myConstraintModel.getNeedsAnimateConstraints());
      myConstraintModel.setNeedsAnimateConstraints(-1);
    }
    boolean ret = mySceneDraw.paintWidgets(width, height, myViewTransform, g, showAllConstraints, myMouseInteraction);
    g.dispose();
    return ret;
  }

  //////////////////////////////////////////////////////////////////////////////
  // Utilities
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Transform android pixels into Dp
   *
   * @param px android pixels
   * @return Dp values
   */
  public int pxToDp(@AndroidCoordinate int px) {
    return myConstraintModel.pxToDp(px);
  }

  /**
   * Accessor for the view transform
   *
   * @return the current view transform
   */
  @NotNull
  public ViewTransform getViewTransform() {
    return myViewTransform;
  }

  /**
   * Accessor for our associated constraint model
   *
   * @return our constraint model
   */
  public ConstraintModel getConstraintModel() {
    return myConstraintModel;
  }

}
