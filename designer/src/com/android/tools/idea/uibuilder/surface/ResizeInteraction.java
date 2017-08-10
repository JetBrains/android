/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.api.ResizeHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.ResizePolicy;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.uibuilder.model.SelectionHandle;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/** A resizing operation */
public class ResizeInteraction extends Interaction {

  /** The surface associated with this interaction. */
  private final SceneView mySceneView;

  /** The component being resized */
  private final NlComponent myComponent;

  /** The top or bottom edge being resized, or null if resizing left or right edges horizontally only */
  private final SegmentType myHorizontalEdge;

  /** The left or right edge being resized, or null if resizing top or bottom edges vertically only */
  private final SegmentType myVerticalEdge;

  /** The resize handler for the layout view */
  private ResizeHandler myResizeHandler;

  public ResizeInteraction(@NotNull SceneView sceneView, @NotNull SceneComponent component, @NotNull SelectionHandle handle) {
    mySceneView = sceneView;
    myComponent = component.getNlComponent();
    myHorizontalEdge = handle.getHorizontalEdge();
    myVerticalEdge = handle.getVerticalEdge();
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int startMask) {
    super.begin(x, y, startMask);
    NlComponent parent = myComponent.getParent();

    if (parent != null) {
      ViewGroupHandler viewGroupHandler = ViewHandlerManager.get(mySceneView.getModel().getFacet()).findLayoutHandler(parent, false);

      if (viewGroupHandler != null) {
        ViewEditor editor = new ViewEditorImpl(mySceneView);
        myResizeHandler = viewGroupHandler.createResizeHandler(editor, myComponent, myHorizontalEdge, myVerticalEdge);

        if (myResizeHandler != null) {
          int androidX = Coordinates.getAndroidX(mySceneView, myStartX);
          int androidY = Coordinates.getAndroidY(mySceneView, myStartY);

          myResizeHandler.start(androidX, androidY, startMask);
        }
      }
    }
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers) {
    super.update(x, y, modifiers);
    moveTo(x, y, modifiers, false);
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);
    moveTo(x, y, modifiers, !canceled);
    if (!canceled) {
      mySceneView.getModel().notifyModified(NlModel.ChangeType.RESIZE_END);
    }
  }

  private void moveTo(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask final int modifiers, boolean commit) {
    if (myResizeHandler == null) {
      return;
    }

    final int ax = Coordinates.getAndroidX(mySceneView, x);
    final int ay = Coordinates.getAndroidY(mySceneView, y);
    final int deltaX = Coordinates.getAndroidDimension(mySceneView, x - myStartX);
    final int deltaY = Coordinates.getAndroidDimension(mySceneView, y - myStartY);

    final Rectangle newBounds = getNewBounds(
      new Rectangle(NlComponentHelperKt.getX(myComponent), NlComponentHelperKt.getY(myComponent), NlComponentHelperKt.getW(myComponent),
                    NlComponentHelperKt.getH(myComponent)), deltaX, deltaY);
    myResizeHandler.update(ax, ay, modifiers, newBounds);
    if (commit) {
      NlWriteCommandAction.run(myComponent, "Resize", () -> myResizeHandler.commit(ax, ay, modifiers, newBounds));
      mySceneView.getModel().notifyModified(NlModel.ChangeType.RESIZE_COMMIT);
    }
    mySceneView.getSurface().repaint();
  }

  /**
   * For the new mouse position, compute the resized bounds (the bounding rectangle that
   * the view should be resized to). This is not just a width or height, since in some
   * cases resizing will change the x/y position of the view as well (for example, in
   * RelativeLayout or in AbsoluteLayout).
   */
  private Rectangle getNewBounds(@AndroidCoordinate Rectangle b, @AndroidCoordinate int deltaX, @AndroidCoordinate int deltaY) {
    int x = b.x;
    int y = b.y;
    int w = b.width;
    int h = b.height;

    if (deltaX == 0 && deltaY == 0) {
      // No move - just use the existing bounds
      return b;
    }

    ResizePolicy mResizePolicy = ResizePolicy.full(); // TODO

    boolean isLeft = myVerticalEdge == SegmentType.LEFT || myVerticalEdge == SegmentType.START;
    boolean isRight = myVerticalEdge == SegmentType.RIGHT || myVerticalEdge == SegmentType.END;
    boolean isTop = myHorizontalEdge == SegmentType.TOP;
    boolean isBottom = myHorizontalEdge == SegmentType.BOTTOM;

    if (mResizePolicy.isAspectPreserving() && w != 0 && h != 0) {
      double aspectRatio = w / (double) h;
      int newW = Math.abs(b.width + (isLeft ? -deltaX : deltaX));
      int newH = Math.abs(b.height + (isTop ? -deltaY : deltaY));
      double newAspectRatio = newW / (double) newH;
      if (newH == 0 || newAspectRatio > aspectRatio) {
        deltaY = (int) (deltaX / aspectRatio);
      } else {
        deltaX = (int) (deltaY * aspectRatio);
      }
    }
    if (isLeft) {
      // The user is dragging the left edge, so the position is anchored on the
      // right.
      int x2 = b.x + b.width;
      int nx1 = b.x + deltaX;
      if (nx1 <= x2) {
        x = nx1;
        w = x2 - x;
      }
      else {
        w = 0;
        x = x2;
      }
    } else if (isRight) {
      // The user is dragging the right edge, so the position is anchored on the
      // left.
      int nx2 = b.x + b.width + deltaX;
      if (nx2 >= b.x) {
        w = nx2 - b.x;
      } else {
        w = 0;
      }
    } else {
      assert myVerticalEdge == null : myVerticalEdge;
    }

    if (isTop) {
      // The user is dragging the top edge, so the position is anchored on the
      // bottom.
      int y2 = b.y + b.height;
      int ny1 = b.y + deltaY;
      if (ny1 < y2) {
        y = ny1;
        h = y2 - y;
      } else {
        h = 0;
        y = y2;
      }
    } else if (isBottom) {
      // The user is dragging the bottom edge, so the position is anchored on the
      // top.
      int ny2 = b.y + b.height + deltaY;
      if (ny2 >= b.y) {
        h = ny2 - b.y;
      } else {
        h = 0;
      }
    } else {
      assert myHorizontalEdge == null : myHorizontalEdge;
    }

    return new Rectangle(x, y, w, h);
  }

  @Override
  public List<Layer> createOverlays() {
    return Collections.singletonList(new ResizeLayer());
  }

  /**
   * An {@link Layer} for the {@link ResizeInteraction}; paints feedback from
   * the current resize handler, if any
   */
  private class ResizeLayer extends Layer {
    public ResizeLayer() {
    }

    @Override
    public void create() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void paint(@NotNull Graphics2D gc) {
      if (myResizeHandler != null) {
        myResizeHandler.paint(new NlGraphics(gc, mySceneView));
      }
    }
  }
}
