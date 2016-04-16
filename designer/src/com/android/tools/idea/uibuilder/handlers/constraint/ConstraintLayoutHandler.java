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

import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.Interaction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.interaction.ResizeHandle;
import com.google.tnt.solver.widgets.ConstraintAnchor;
import icons.AndroidIcons;

import javax.swing.*;
import java.awt.*;

/**
 * Handles interactions for the ConstraintLayout viewgroups
 */
public class ConstraintLayoutHandler extends ViewGroupHandler {

  private static final boolean UPDATE_CURSOR = false;
  private final Cursor myUnlinkAnchorCursor;
  private final Cursor myLeftAnchorCursor;
  private final Cursor myTopAnchorCursor;
  private final Cursor myRightAnchorCursor;
  private final Cursor myBottomAnchorCursor;

  /**
   * Base constructor
   */
  public ConstraintLayoutHandler() {
    myUnlinkAnchorCursor = createCursor(AndroidIcons.SherpaIcons.UnlinkConstraintCursor, 8, 8, "unlink constraint");
    myLeftAnchorCursor = createCursor(AndroidIcons.SherpaIcons.LeftConstraintCursor, 18, 12, "left constraint");
    myTopAnchorCursor = createCursor(AndroidIcons.SherpaIcons.TopConstraintCursor, 12, 18, "top constraint");
    myRightAnchorCursor = createCursor(AndroidIcons.SherpaIcons.RightConstraintCursor, 6, 12, "right constraint");
    myBottomAnchorCursor = createCursor(AndroidIcons.SherpaIcons.BottomConstraintCursor, 12, 6, "bottom constraint");
  }

  /**
   * Return a new ConstraintInteraction instance to handle a mouse interaction
   *
   * @param screenView the associated screen view
   * @param component  the component we belong to
   * @return a new instance of ConstraintInteraction
   */
  @Override
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent component) {
    return new ConstraintInteraction(screenView, component);
  }

  /**
   * Return a drag handle to handle drag and drop interaction
   *
   * @param editor     the associated IDE editor
   * @param layout     the layout being dragged over/into
   * @param components the components being dragged
   * @param type       the <b>initial</b> type of drag, which can change along the way
   * @return instance of a ConstraintDragHandler
   */
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull NlComponent layout,
                                       @NotNull java.util.List<NlComponent> components,
                                       @NotNull DragType type) {
    return new ConstraintDragHandler(editor, this, layout, components, type);
  }


  /**
   * Update the mouse cursor if the (x, y) coordinates hit a resize handle or constraint handle
   *
   * @param designSurface the surface we are working on
   * @param x             the current x mouse coordinate
   * @param y             the current y mouse coordinate
   * @return true if we modified the cursor
   */
  @Override
  public boolean updateCursor(@NotNull DesignSurface designSurface,
                              @AndroidCoordinate int x, @AndroidCoordinate int y) {
    ConstraintModel model = ConstraintModel.getModel();
    if (UPDATE_CURSOR) {
      int ax = model.pxToDp(x);
      int ay = model.pxToDp(y);

      Cursor newCursor = null;

      ViewTransform transform = model.getViewTransform();
      // First check for anchors
      ConstraintAnchor constraintAnchor = model.getScene().findAnchor(ax, ay, false, false, transform);
      if (constraintAnchor != null) {
        if (constraintAnchor.isConnected()) {
          newCursor = myUnlinkAnchorCursor;
        }
        else {
          switch (constraintAnchor.getType()) {
            case LEFT: {
              newCursor = myLeftAnchorCursor;
            }
            break;
            case TOP: {
              newCursor = myTopAnchorCursor;
            }
            break;
            case RIGHT: {
              newCursor = myRightAnchorCursor;
            }
            break;
            case BOTTOM: {
              newCursor = myBottomAnchorCursor;
            }
            break;
          }
        }
      }

      // Then for resize handles
      if (newCursor == null) {
        ResizeHandle resizeHandle = model.getScene().findResizeHandle(ax, ay, transform);
        if (resizeHandle != null) {
          switch (resizeHandle.getType()) {
            case LEFT_TOP: {
              newCursor = Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            }
            break;
            case LEFT_SIDE: {
              newCursor = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            }
            break;
            case LEFT_BOTTOM: {
              newCursor = Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
            }
            break;
            case RIGHT_TOP: {
              newCursor = Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            }
            break;
            case RIGHT_SIDE: {
              newCursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            }
            break;
            case RIGHT_BOTTOM: {
              newCursor = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            }
            break;
            case TOP_SIDE: {
              newCursor = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            }
            break;
            case BOTTOM_SIDE: {
              newCursor = Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
            }
            break;
          }
        }
      }

      // Set the mouse cursor
      // TODO: we should only update if we are above a component we manage, not simply all component that
      // is a child of this viewgroup
      designSurface.setCursor(newCursor);
      return true;
    } else if (model.mouseMoved(x, y)) {
      designSurface.repaint();
    }

    return true;
  }

  /**
   * Utility function to get a mouse Cursor from an Icon
   *
   * @param icon the icon we want to use
   * @param x    the x offset in the icon
   * @param y    the y offset in the icon
   * @param text the alternate text for this cursor
   * @return a new custom cursor
   */
  @NotNull
  private Cursor createCursor(@NotNull Icon icon, int x, int y, @NotNull String text) {
    Toolkit toolkit = Toolkit.getDefaultToolkit();
    Image image = ((ImageIcon)icon).getImage();
    return toolkit.createCustomCursor(image, new Point(x, y), text);
  }

  /**
   * Return true to be in charge of the painting
   *
   * @return true
   */
  @Override
  public boolean handlesPainting() {
    return true;
  }

  /**
   * Paint the component and its children on the given context
   *
   * @param gc graphics context
   * @param screenView the current screenview
   * @param width width of the surface
   * @param height height of the surface
   * @param component the component to draw
   * @param transparent if true, it will only draw the widget decorations
   *
   * @return true to indicate that we will need to be repainted
   */
  @Override
  public boolean drawGroup(@NotNull Graphics2D gc, @NotNull ScreenView screenView,
                           int width, int height, @NotNull NlComponent component,
                           boolean transparent) {
    boolean needsRepaint = false;
    int dpi = screenView.getConfiguration().getDensity().getDpiValue();
    int baseDpi = ConstraintModel.DEFAULT_DENSITY;
    float dpiFactor = dpi / (float)baseDpi;

    if (screenView.getModel() == null) {
      needsRepaint = true;
    }
    ConstraintModel.useNewModel(screenView.getModel());
    ConstraintModel constraintModel = ConstraintModel.getModel();

    ViewTransform transform = constraintModel.getViewTransform();
    transform.setScale((float)(screenView.getScale() * dpiFactor));
    transform.setTranslate(screenView.getX(), screenView.getY());

    if (false) {
      // TODO: fix the selection coming from the model
      SelectionModel selectionModel = screenView.getSelectionModel();
      for (NlComponent selection : selectionModel.getSelection()) {
        constraintModel.selectComponent(selection);
      }
    }

    boolean showAllConstraints = false;
    needsRepaint |= constraintModel.paint(gc, screenView, width, height, showAllConstraints, transparent);
    return needsRepaint;
  }
}
