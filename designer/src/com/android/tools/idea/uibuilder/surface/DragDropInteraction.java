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

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DragEnterEvent;
import com.android.tools.idea.common.surface.DragOverEvent;
import com.android.tools.idea.common.surface.DropEvent;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.InteractionEvent;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.common.CommonDragHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutGuidelineHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlDropEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final DesignSurface<?> myDesignSurface;

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
  private boolean myDoesAcceptDropAtLastPosition = true;

  public DragDropInteraction(@NotNull DesignSurface<?> designSurface,
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
  public void begin(@NotNull InteractionEvent event) {
    assert event instanceof DragEnterEvent : "The instance of event should be DragEnterEvent but it is " + event.getClass() +
                                             "; The dragged component is " + myDraggedComponents +
                                             "; The SceneView is " + mySceneView +
                                             ", start (x, y) = " + myStartX + ", " + myStartY + ", start mask is " + myStartMask;

    DropTargetDragEvent dropEvent = ((DragEnterEvent)event).getEventObject();
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    begin(dropEvent.getLocation().x, dropEvent.getLocation().y, event.getInfo().getModifiersEx());
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
    super.begin(x, y, modifiersEx);
    moveTo(x, y, modifiersEx, false);
    myDesignSurface.startDragDropInteraction();
  }

  @Override
  public void update(@NotNull InteractionEvent event) {
    if (event instanceof DragOverEvent) {
      DropTargetDragEvent dragEvent = ((DragOverEvent)event).getEventObject();
      Point location = dragEvent.getLocation();
      NlDropEvent nlDropEvent = new NlDropEvent(dragEvent);

      SceneView sceneView = myDesignSurface.getSceneViewAtOrPrimary(location.x, location.y);
      if (sceneView == null) {
        nlDropEvent.reject();
        return;
      }

      //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
      update(location.x, location.y, event.getInfo().getModifiersEx());

      if (acceptsDrop()) {
        DragType dragType = dragEvent.getDropAction() == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
        setType(dragType);
        NlModel model = sceneView.getSceneManager().getModel();
        InsertType insertType = model.determineInsertType(dragType, getTransferItem(), true /* preview */);

        // This determines the icon presented to the user while dragging.
        // If we are dragging a component from the palette then use the icon for a copy, otherwise show the icon
        // that reflects the users choice i.e. controlled by the modifier key.
        nlDropEvent.accept(insertType);
      }
      else {
        nlDropEvent.reject();
      }
    }
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
    moveTo(x, y, modifiersEx, false);
  }

  /**
   * Returns true if the component being dragged can be dropped at the last position set by begin() or update()
   * @return true if the drop is accepted
   */
  public boolean acceptsDrop() { return myDoesAcceptDropAtLastPosition; }

  @Override
  public void commit(@NotNull InteractionEvent event) {
    if (!(event instanceof DropEvent)) {
      String errorMessage = "The instance of event should be DropEvent but it is " + event.getClass() +
                            "; The dragged component is " + myDraggedComponents +
                            "; The SceneView is " + mySceneView +
                            ", start (x, y) = " + myStartX + ", " + myStartY + ", start mask is " + myStartMask;
      Logger.getInstance(getClass()).info("Unexpected event type: " + errorMessage);
      cancel(event);
      return;
    }

    DropTargetDropEvent dropEvent = ((DropEvent)event).getEventObject();
    NlDropEvent nlDropEvent = new NlDropEvent(dropEvent);
    Point location = dropEvent.getLocation();

    InsertType insertType = finishDropInteraction(location.x, location.y, dropEvent.getDropAction(), dropEvent.getTransferable());
    if (insertType != null) {
      //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
      end(dropEvent.getLocation().x, dropEvent.getLocation().y, event.getInfo().getModifiersEx());
      nlDropEvent.accept(insertType);
      nlDropEvent.complete();
    }
    else {
      cancel(event);
      nlDropEvent.reject();
    }
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
    moveTo(x, y, modifiersEx, true);
    boolean hasDragHandler = myDragHandler != null;
    mySceneView = myDesignSurface.getSceneViewAtOrPrimary(x, y);
    if (mySceneView != null && myDragReceiver != null && hasDragHandler) {
      mySceneView.getSceneManager().getModel().notifyModified(NlModel.ChangeType.DND_END);

      // We need to clear the selection otherwise the targets for the newly component are not added until
      // another component is selected and then this one reselected
      mySceneView.getSelectionModel().clear();
      // Update the scene hierarchy to add the new targets
      mySceneView.getSceneManager().update();
      myDragReceiver.updateTargets();
      // Do not select the dragged components here
      // These components are either already selected, or they are being created will be selected later
    }
    myDesignSurface.stopDragDropInteraction();
  }

  @Override
  public void cancel(@NotNull InteractionEvent event) {
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    cancel(event.getInfo().getX(), event.getInfo().getY(), event.getInfo().getModifiersEx());
    if (event instanceof DropEvent) {
      NlDropEvent nlDropEvent = new NlDropEvent(((DropEvent)event).getEventObject());
      nlDropEvent.reject();
    }
  }

  @Override
  public void cancel(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
    moveTo(x, y, modifiersEx, false);
    mySceneView = myDesignSurface.getSceneViewAtOrPrimary(x, y);
    if (myDragHandler != null) {
      myDragHandler.cancel();
    }
    myDesignSurface.stopDragDropInteraction();
  }

  @Nullable
  @Override
  public Cursor getCursor() {
    return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
  }

  private void moveTo(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask final int modifiers, boolean commit) {
    mySceneView = myDesignSurface.getSceneViewAtOrPrimary(x, y);
    if (mySceneView == null) {
      return;
    }
    myDoesAcceptDropAtLastPosition = true;
    myDesignSurface.getLayeredPane().scrollRectToVisible(
      new Rectangle(x - NlConstants.DEFAULT_SCREEN_OFFSET_X, y - NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                    2 * NlConstants.DEFAULT_SCREEN_OFFSET_X, 2 * NlConstants.DEFAULT_SCREEN_OFFSET_Y));
    @AndroidCoordinate final int ax = Coordinates.getAndroidX(mySceneView, x);
    @AndroidCoordinate final int ay = Coordinates.getAndroidY(mySceneView, y);

    Project project = mySceneView.getSceneManager().getModel().getProject();
    ViewGroupHandler handler = findViewGroupHandlerAt(x, y);
    SceneContext sceneContext = SceneContext.get(mySceneView);
    SceneComponent viewgroup =
      mySceneView.getScene().findComponent(sceneContext,
                                           Coordinates.getAndroidXDip(mySceneView, x),
                                           Coordinates.getAndroidYDip(mySceneView, y));

    while (viewgroup != null && !NlComponentHelperKt.isOrHasSuperclass(viewgroup.getNlComponent(), SdkConstants.CLASS_VIEWGROUP)) {
      viewgroup = viewgroup.getParent();
    }

    // No need to change Handler when using CommonDragHandler.
    if (!(myDragHandler instanceof CommonDragHandler) && (handler != myCurrentHandler || myCurrentViewgroup != viewgroup)) {
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
          boolean constraintHelper =
            AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_GUIDELINE.isEquals(component.getTagName()) ||
            AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER.isEquals(component.getTagName()) ||
            AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_FLOW.isEquals(component.getTagName()) ||
            AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_GROUP.isEquals(component.getTagName()) ||
            AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_LAYER.isEquals(component.getTagName()) ||
            AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_IMAGE_FILTER_VIEW.isEquals(component.getTagName()) ||
            AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_IMAGE_FILTER_BUTTON.isEquals(component.getTagName()) ||
            AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_MOCK_VIEW.isEquals(component.getTagName());
          boolean acceptableHandler =
              (myCurrentHandler instanceof ConstraintLayoutHandler) ||
              (myCurrentHandler instanceof MotionLayoutHandler);
          if (constraintHelper && !acceptableHandler) {
            error = String.format(
              "<%1$s> does not accept <%2$s> as a child", myDragReceiver.getNlComponent().getTagName(), component.getTagName());
            myDoesAcceptDropAtLastPosition = false;
            break;
          }
          if (!myCurrentHandler.acceptsChild(myDragReceiver, component, ax, ay)) {
            error = String.format(
              "<%1$s> does not accept <%2$s> as a child", myDragReceiver.getNlComponent().getTagName(), component.getTagName());
            myDoesAcceptDropAtLastPosition = false;
            break;
          }
          ViewHandler viewHandler = viewHandlerManager.getHandler(component);
          if (viewHandler != null && !viewHandler.acceptsParent(myDragReceiver.getNlComponent(), component)) {
            error = String.format(
              "<%1$s> does not accept <%2$s> as a parent", component.getTagName(), myDragReceiver.getNlComponent().getTagName());
            myDoesAcceptDropAtLastPosition = false;
            break;
          }
        }
        if (error == null) {
          ViewEditorImpl editorImpl = new ViewEditorImpl(mySceneView);
          if (StudioFlags.NELE_DRAG_PLACEHOLDER.get() && CommonDragHandler.isSupportCommonDragHandler(myCurrentHandler)) {
            myDragHandler = new CommonDragHandler(editorImpl, myCurrentHandler, myDragReceiver, myDraggedComponents, myType);
          }
          else {
            myDragHandler = myCurrentHandler.createDragHandler(editorImpl, myDragReceiver, myDraggedComponents, myType);
          }
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

    if ((myDragHandler instanceof CommonDragHandler) || (myDragHandler != null && myCurrentHandler != null)) {
      String error = myDragHandler.update(Coordinates.pxToDp(mySceneView, ax), Coordinates.pxToDp(mySceneView, ay), modifiers, sceneContext);
      final List<NlComponent> added = new ArrayList<>();
      if (commit && error == null) {
        added.addAll(myDraggedComponents);
        final NlModel model = mySceneView.getSceneManager().getModel();
        InsertType insertType = model.determineInsertType(myType, myTransferItem, false /* not for preview */);

        // TODO: Run this *after* making a copy
        myDragHandler.commit(ax, ay, modifiers, insertType);
        model.notifyModified(NlModel.ChangeType.DND_COMMIT);

        // Do not select the created component at this point see b/124231532.
        // The commit above is executed asynchronously and may not have completed yet.
        // The selection should not be moved before the components are properly created and placed under its new parent.
        // Other tools like the attributes panel may rely on the component being fully completed.

        // Move the focus to the design area in case this component is created from the palette see b/69394814
        myDesignSurface.getLayeredPane().requestFocus();
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
    final SceneView sceneView = myDesignSurface.getSceneViewAtOrPrimary(x, y);
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

    ViewHandlerManager handlerManager = ViewHandlerManager.get(sceneView.getSceneManager().getModel().getFacet());
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
    SceneView view = myDesignSurface.getSceneViewAtOrPrimary(x, y);
    assert view != null;

    ViewHandlerManager manager = ViewHandlerManager.get(view.getSceneManager().getModel().getFacet());

    Predicate<NlComponent> acceptsChild =
      child -> parentHandler.acceptsChild(parent, child, Coordinates.getAndroidX(view, x), Coordinates.getAndroidY(view, y));

    Predicate<NlComponent> acceptsParent = child -> {
      ViewHandler childHandler = manager.getHandler(child);
      return childHandler != null && childHandler.acceptsParent(parent.getNlComponent(), child);
    };

    return myDraggedComponents.stream().allMatch(acceptsChild.and(acceptsParent));
  }

  @NotNull
  @Override
  public List<Layer> createOverlays() {
    return Collections.singletonList(new DragLayer());
  }

  @NotNull
  public List<NlComponent> getDraggedComponents() {
    return myDraggedComponents;
  }

  // TODO: make it private after StudioFlags.NELE_NEW_INTERACTION_INTERFACE is removed, and avoid to return InsertType.
  @Nullable
  public InsertType finishDropInteraction(int mouseX, int mouseY, int dropAction, @Nullable Transferable transferable) {
    if (transferable == null) {
      return null;
    }
    DnDTransferItem item = DnDTransferItem.getTransferItem(transferable, false /* no placeholders */);
    if (item == null) {
      return null;
    }
    SceneView sceneView = myDesignSurface.getSceneViewAtOrPrimary(mouseX, mouseY);
    if (sceneView == null) {
      return null;
    }

    NlModel model = sceneView.getSceneManager().getModel();
    DragType dragType = dropAction == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
    InsertType insertType = model.determineInsertType(dragType, item, false /* not for preview */);

    setType(dragType);
    setTransferItem(item);

    List<NlComponent> dragged = getDraggedComponents();
    List<NlComponent> components;
    if (insertType == InsertType.MOVE) {
      components = myDesignSurface.getSelectionModel().getSelection();
    }
    else {
      components = model.createComponents(item, insertType);

      if (components.isEmpty()) {
        return null;  // User cancelled
      }
    }
    if (dragged.size() != components.size()) {
      throw new AssertionError(
        String.format("Problem with drop: dragged.size(%1$d) != components.size(%2$d)", dragged.size(), components.size()));
    }
    for (int index = 0; index < dragged.size(); index++) {
      if (!NlComponentHelperKt.getHasNlComponentInfo(components.get(index)) ||
          !NlComponentHelperKt.getHasNlComponentInfo(dragged.get(index))) {
        continue;
      }
      NlComponentHelperKt.setX(components.get(index), NlComponentHelperKt.getX(dragged.get(index)));
      NlComponentHelperKt.setY(components.get(index), NlComponentHelperKt.getY(dragged.get(index)));
    }

    logFinishDropInteraction(components);

    dragged.clear();
    dragged.addAll(components);
    return insertType;
  }

  private void logFinishDropInteraction(@NotNull List<NlComponent> components) {
    DesignSurface<?> surface = myDesignSurface;
    if (!(surface instanceof NlDesignSurface)) {
      return;
    }

    NlAnalyticsManager manager = (NlAnalyticsManager)surface.getAnalyticsManager();
    components.forEach( component -> {
      if (AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_GUIDELINE.isEquals(component.getTagName())) {
        if (ConstraintLayoutGuidelineHandler.isVertical(component)) {
          manager.trackAddVerticalGuideline();
        }
        else {
          manager.trackAddHorizontalGuideline();
        }
      }
    });
  }

  /**
   * An {@link Layer} for the {@link DragDropInteraction}; paints feedback from
   * the current drag handler, if any
   */
  private class DragLayer extends Layer {
    @Override
    public void paint(@NotNull Graphics2D gc) {
      if (myDragHandler != null) {
        myDragHandler.paint(new NlGraphics(gc, mySceneView));
      }
    }
  }
}
