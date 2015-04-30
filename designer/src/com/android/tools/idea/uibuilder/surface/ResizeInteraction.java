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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.ResizeHandler;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/** A resizing operation */
public class ResizeInteraction extends Interaction {

  /** The surface associated with this interaction. */
  private final ScreenView myScreenView;

  /** The component being resized */
  private final NlComponent myComponent;

  /** The top or bottom edge being resized, or null if resizing left or right edges horizontally only */
  private final SegmentType myHorizontalEdge;

  /** The left or right edge being resized, or null if resizing top or bottom edges vertically only */
  private final SegmentType myVerticalEdge;

  /** The resize handler for the layout view */
  private ResizeHandler myResizeHandler;

  public ResizeInteraction(@NonNull ScreenView screenView, @NonNull NlComponent component, @NonNull SelectionHandle handle) {
    myScreenView = screenView;
    myComponent = component;
    myHorizontalEdge = handle.getHorizontalEdge();
    myVerticalEdge = handle.getVerticalEdge();
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, int startMask) {
    super.begin(x, y, startMask);

    final int ax = Coordinates.getAndroidX(myScreenView, x);
    final int ay = Coordinates.getAndroidY(myScreenView, y);

    /* The parent layout's view group handler */
    ViewGroupHandler viewGroupHandler = findViewGroupHandlerAt(ax, ay);
    if (viewGroupHandler != null) {
      ViewEditorImpl editor = new ViewEditorImpl(myScreenView);
      myResizeHandler = viewGroupHandler.createResizeHandler(editor, myComponent, myHorizontalEdge, myVerticalEdge);
      if (myResizeHandler != null) {
        myResizeHandler.start(Coordinates.getAndroidX(myScreenView, myStartX), Coordinates.getAndroidY(myScreenView, myStartY));
      }
    }
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y) {
    super.update(x, y);
    moveTo(x, y, false);
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, boolean canceled) {
    super.end(x, y, canceled);
    moveTo(x, y, !canceled);
  }

  private void moveTo(@SwingCoordinate int x, @SwingCoordinate int y, boolean commit) {
    if (myResizeHandler == null) {
      return;
    }
    final int ax = Coordinates.getAndroidX(myScreenView, x);
    final int ay = Coordinates.getAndroidY(myScreenView, y);

    myResizeHandler.update(ax, ay);
    if (commit) {
      NlModel model = myScreenView.getModel();
      Project project = model.getFacet().getModule().getProject();
      XmlFile file = model.getFile();
      String label = "Resize";
      WriteCommandAction action = new WriteCommandAction(project, label, file) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          myResizeHandler.commit(ax, ay);
        }
      };
      action.execute();
    }
    myScreenView.getSurface().repaint();
  }

  @Nullable
  private ViewGroupHandler findViewGroupHandlerAt(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    NlModel model = myScreenView.getModel();
    NlComponent component = model.findLeafAt(x, y, true);
    ViewHandlerManager handlerManager = ViewHandlerManager.get(model.getFacet());
    return component != null ? handlerManager.findLayoutHandler(component, false) : null;
  }

  @Override
  public List<Layer> createOverlays() {
    return Collections.<Layer>singletonList(new ResizeLayer());
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
    public void paint(@NonNull Graphics2D gc) {
      if (myResizeHandler != null) {
        myResizeHandler.paint(myScreenView, gc);
      }
    }
  }
}
