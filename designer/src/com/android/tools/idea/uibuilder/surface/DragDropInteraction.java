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

import com.android.SdkConstants;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Interaction where you insert a new component into a parent layout (which can vary
 * during the interaction -- as you drag across the canvas, different layout parents
 * become eligible based on the mouse pointer).
 * <p>
 * There are multiple types of insert modes:
 * <ul>
 * <li>Copy. This is typically the interaction used when dragging from the palette;
 * a new copy of the components are created. This can also be achieved when
 * dragging with a modifier key.</li>
 * <li>Move. This is typically done by dragging one or more widgets around in
 * the canvas; when moving the widget within a single parent, it may only
 * translate into some updated layout parameters or widget reordering, whereas
 * when moving from one parent to another widgets are moved in the hierarchy
 * as well.</li>
 * <li>A paste is similar to a copy. It typically tries to preserve internal
 * relationships and id's when possible. If you for example select 3 widgets and
 * cut them, if you paste them the widgets will come back in the exact same place
 * with the same id's. If you paste a second time, the widgets will now all have
 * new unique id's (and any internal references to each other are also updated.)</li>
 * </ul>
 */
public class DragDropInteraction extends Interaction {

  /**
   * The surface associated with this interaction.
   */
  private final DesignSurface myDesignSurface;

  /**
   * The components being dragged
   */
  private final List<NlComponent> myDraggedComponents;

  /**
   * The current view group handler, if any. This is the layout widget we're dragging over (or the
   * nearest layout widget containing the non-layout views we're dragging over
   */
  private ViewGroupHandler myCurrentHandler;

  /**
   * The drag handler for the layout view, if it supports drags
   */
  private DragHandler myDragHandler;

  /**
   * The view group we're dragging over/into
   */
  private SceneComponent myDragReceiver;

  /**
   * Whether we're copying or moving
   */
  private DragType myType = DragType.MOVE;

  /**
   * The last accessed screen view.
   */
  private SceneView mySceneView;

  /**
   * The transfer item for this drag if any
   */
  private DnDTransferItem myTransferItem;

  /**
   * The current viewgroup found for handling the dnd
   */
  SceneComponent myCurrentViewgroup = null;

  public DragDropInteraction(@NotNull DesignSurface designSurface,
                             @NotNull List<NlComponent> dragged) {
    myDesignSurface = designSurface;
    myDraggedComponents = dragged;
  }

  public void setType(DragType type) {
    myType = type;
    if (myDragHandler != null) {
      myDragHandler.setDragType(type);
    }
  }

  public void setTransferItem(@NotNull DnDTransferItem item) {
    myTransferItem = item;
  }

  @Nullable
  public DnDTransferItem getTransferItem() {
    return myTransferItem;
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers) {
    super.begin(x, y, modifiers);
    moveTo(x, y, modifiers, false);
    myDesignSurface.startDragDropInteraction();
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
    mySceneView = myDesignSurface.getSceneView(x, y);
    if (mySceneView != null && myDragReceiver != null && !canceled) {
      mySceneView.getModel().notifyModified(NlModel.ChangeType.DND_END);

      // We need to clear the selection otherwise the targets for the newly component are not added until
      // another component is selected and then this one reselected
      mySceneView.getSelectionModel().clear();
      // Update the scene hierarchy to add the new targets
      mySceneView.getSceneManager().update();
      myDragReceiver.updateTargets(true);
      // Select the dragged components
      mySceneView.getSelectionModel().setSelection(myDraggedComponents);
    }
    if (canceled && myDragHandler != null) {
      myDragHandler.cancel();
    }
    myDesignSurface.stopDragDropInteraction();
  }

  private void moveTo(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask final int modifiers, boolean commit) {
    mySceneView = myDesignSurface.getSceneView(x, y);
    if (mySceneView == null) {
      return;
    }
    myDesignSurface.getLayeredPane().scrollRectToVisible(
      new Rectangle(x - NlConstants.DEFAULT_SCREEN_OFFSET_X, y - NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                    2 * NlConstants.DEFAULT_SCREEN_OFFSET_X, 2 * NlConstants.DEFAULT_SCREEN_OFFSET_Y));
    @AndroidCoordinate final int ax = Coordinates.getAndroidX(mySceneView, x);
    @AndroidCoordinate final int ay = Coordinates.getAndroidY(mySceneView, y);

    Project project = mySceneView.getModel().getProject();
    ViewGroupHandler handler = findViewGroupHandlerAt(x, y);
    SceneComponent viewgroup =
      mySceneView.getScene().findComponent(SceneContext.get(mySceneView),
                                           Coordinates.getAndroidXDip(mySceneView, x),
                                           Coordinates.getAndroidYDip(mySceneView, y));

    while (viewgroup != null && !NlComponentHelperKt.isOrHasSuperclass(viewgroup.getNlComponent(), SdkConstants.CLASS_VIEWGROUP)) {
      viewgroup = viewgroup.getParent();
    }

    if (handler != myCurrentHandler || myCurrentViewgroup != viewgroup) {
      if (myCurrentViewgroup != null) {
        myCurrentViewgroup.setDrawState(SceneComponent.DrawState.NORMAL);
      }
      myCurrentViewgroup = viewgroup;
      if (myCurrentViewgroup != null) {
        myCurrentViewgroup.setDrawState(SceneComponent.DrawState.DRAG);
      }

      if (myDragHandler != null) {
        myDragHandler.cancel();
        myDragHandler = null;
        mySceneView.getSurface().repaint();
      }

      myCurrentHandler = handler;
      if (myCurrentHandler != null) {
        assert myDragReceiver != null;

        String error = null;
        ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(project);
        for (NlComponent component : myDraggedComponents) {
          if (!myCurrentHandler.acceptsChild(myDragReceiver, component, ax, ay)) {
            error = String.format(
              "<%1$s> does not accept <%2$s> as a child", myDragReceiver.getNlComponent().getTagName(), component.getTagName());
            break;
          }
          ViewHandler viewHandler = viewHandlerManager.getHandler(component);
          if (viewHandler != null && !viewHandler.acceptsParent(myDragReceiver.getNlComponent(), component)) {
            error = String.format(
              "<%1$s> does not accept <%2$s> as a parent", component.getTagName(), myDragReceiver.getNlComponent().getTagName());
            break;
          }
        }
        if (error == null) {
          myDragHandler = myCurrentHandler.createDragHandler(new ViewEditorImpl(mySceneView), myDragReceiver, myDraggedComponents, myType);
          if (myDragHandler != null) {
            myDragHandler
              .start(Coordinates.getAndroidXDip(mySceneView, myStartX), Coordinates.getAndroidYDip(mySceneView, myStartY), myStartMask);
          }
        }
        else {
          myCurrentHandler = null;
        }
      }
    }

    if (myDragHandler != null && myCurrentHandler != null) {
      String error = myDragHandler.update(Coordinates.pxToDp(mySceneView, ax), Coordinates.pxToDp(mySceneView, ay), modifiers);
      final List<NlComponent> added = Lists.newArrayList();
      if (commit && error == null) {
        added.addAll(myDraggedComponents);
        final NlModel model = mySceneView.getModel();
        InsertType insertType = model.determineInsertType(myType, myTransferItem, false /* not for preview */);
        // TODO: Run this *after* making a copy
        myDragHandler.commit(ax, ay, modifiers, insertType);
        model.notifyModified(NlModel.ChangeType.DND_COMMIT);
        // Select newly dropped components
        model.getSelectionModel().setSelection(added);
      }
      mySceneView.getSurface().repaint();
    }
  }

  /**
   * Cached handler for the most recent call to {@link #findViewGroupHandlerAt}, this
   * corresponds to the result found for {@link #myCachedComponent} (which may not be
   * the component corresponding to the view handler. E.g. if you have a LinearLayout
   * with a button inside, the view handler will always return the view handler for the
   * LinearLayout, even when pointing at the button.)
   */
  private ViewGroupHandler myCachedHandler;

  /**
   * Cached handler for the most recent call to {@link #findViewGroupHandlerAt}
   */
  private SceneComponent myCachedComponent;

  @Nullable
  private ViewGroupHandler findViewGroupHandlerAt(@SwingCoordinate int x, @SwingCoordinate int y) {
    final SceneView sceneView = myDesignSurface.getSceneView(x, y);
    if (sceneView == null) {
      return null;
    }
    SceneComponent component =
      sceneView.getScene().findComponent(SceneContext.get(sceneView),
                                         Coordinates.getAndroidXDip(sceneView, x),
                                         Coordinates.getAndroidYDip(sceneView, y));

    if (component == null) {
      component = sceneView.getScene().getRoot();
    }
    component = excludeDraggedComponents(component);
    if (component == myCachedComponent && myCachedHandler != null) {
      return myCachedHandler;
    }

    myCachedComponent = component;
    myCachedHandler = null;

    ViewHandlerManager handlerManager = ViewHandlerManager.get(sceneView.getModel().getFacet());
    while (component != null) {
      Object handler = handlerManager.getHandler(component.getNlComponent());

      if (handler instanceof ViewGroupHandler && acceptsDrop(component, (ViewGroupHandler)handler, x, y)) {
        myCachedHandler = (ViewGroupHandler)handlerManager.getHandler(component.getNlComponent());
        myDragReceiver = component; // HACK: This method should not side-effect set this; instead the method should compute it!
        return myCachedHandler;
      }

      component = component.getParent();
    }

    return null;
  }

  @Nullable
  private SceneComponent excludeDraggedComponents(@Nullable SceneComponent component) {
    SceneComponent receiver = component;
    while (component != null) {
      if (myDraggedComponents.contains(component.getNlComponent())) {
        receiver = component.getParent();
      }
      component = component.getParent();
    }
    return receiver;
  }

  private boolean acceptsDrop(@NotNull SceneComponent parent,
                              @NotNull ViewGroupHandler parentHandler,
                              @SwingCoordinate int x,
                              @SwingCoordinate int y) {
    SceneView view = myDesignSurface.getSceneView(x, y);
    assert view != null;

    ViewHandlerManager manager = ViewHandlerManager.get(view.getModel().getFacet());

    Predicate<NlComponent> acceptsChild =
      child -> parentHandler.acceptsChild(parent, child, Coordinates.getAndroidX(view, x), Coordinates.getAndroidY(view, y));

    Predicate<NlComponent> acceptsParent = child -> {
      ViewHandler childHandler = manager.getHandler(child);
      return childHandler != null && childHandler.acceptsParent(parent.getNlComponent(), child);
    };

    return myDraggedComponents.stream().allMatch(acceptsChild.and(acceptsParent));
  }

  @Override
  public List<Layer> createOverlays() {
    return Collections.singletonList(new DragLayer());
  }

  @NotNull
  public List<NlComponent> getDraggedComponents() {
    return myDraggedComponents;
  }

  /**
   * An {@link Layer} for the {@link DragDropInteraction}; paints feedback from
   * the current drag handler, if any
   */
  private class DragLayer extends Layer {

    /**
     * Constructs a new {@link DragLayer}.
     */
    public DragLayer() {
    }

    @Override
    public void create() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void paint(@NotNull Graphics2D gc) {
      if (myDragHandler != null) {
        myDragHandler.paint(new NlGraphics(gc, mySceneView));
      }
    }
  }
}
