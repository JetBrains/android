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
package com.android.tools.idea.uibuilder.api;

import com.android.SdkConstants;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.ComponentProvider;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TargetProvider;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.uibuilder.model.FillPolicy;
import com.android.tools.idea.uibuilder.model.SegmentType;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.xml.XmlBuilder;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.dnd.DropTargetDropEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Handler for views that are layout managers.
 */
@SuppressWarnings("UnusedParameters")
public class ViewGroupHandler extends ViewHandler implements TargetProvider {
  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_MATCH_PARENT)
          .androidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_MATCH_PARENT)
          .endTag(tagName)
          .toString();
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        // Most layout managers will use their palette icon for previewing.
        // Make that the default here.
        return NO_PREVIEW;
      default:
        throw new AssertionError(xmlType);
    }
  }

  /**
   * Returns whether the given layout accepts the given proposed child.
   *
   * @param layout   the layout being inserted into (which does not yet contain the
   *                 newly created node in its child list)
   * @param newChild the newly created component
   * @return true if the proposed child is accepted
   */
  public boolean acceptsChild(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild) {
    return true;
  }

  /**
   * Returns true if the parent accepts the new child. Called by the editor when a user drags a component into the design surface.
   *
   * @param x the x coordinate of the drag in the Android coordinate system
   * @param y the y coordinate of the drag in the Android coordinate system
   */
  public boolean acceptsChild(@NotNull SceneComponent parent,
                              @NotNull NlComponent newChild,
                              @AndroidCoordinate int x,
                              @AndroidCoordinate int y) {
    return acceptsChild(parent.getNlComponent(), newChild);
  }

  /**
   * Called when one or more children are about to be deleted by the user.
   *
   * @param parent  the parent of the deleted children (which still contains
   *                the children since this method is called before the deletion
   *                is performed)
   * @param deleted a nonempty list of children about to be deleted
   * @return true if the children have been fully deleted by this participant; false if normal deletion should resume. Note that even though
   * an implementation may return false from this method, that does not mean it did not perform any work. For example, a RelativeLayout
   * handler could remove constraints pointing to now deleted components, but leave the overall deletion of the elements to the core
   * designer.
   */
  public boolean deleteChildren(@NotNull NlComponent parent, @NotNull Collection<NlComponent> deleted) {
    return false;
  }

  /**
   * Creates a new complete interaction for this view
   *
   * @param screenView the associated screen view
   * @param layout     the layout creating the interaction
   * @return a new interaction, or null if this view does not handle full interactions and use other Handlers
   */
  @Nullable
  public Interaction createInteraction(@NotNull ScreenView screenView, @NotNull NlComponent layout) {
    return null;
  }

  /**
   * Creates a new drag handler for this view, if the view accepts children or allows them to be reconfigured.
   *
   * @param editor     the associated IDE editor
   * @param layout     the layout being dragged over/into
   * @param components the components being dragged
   * @param type       the <b>initial</b> type of drag, which can change along the way
   * @return a new drag handler, or null if this view does not accept children or does not allow them to be reconfigured
   */
  @Nullable
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return null;
  }

  /**
   * Creates a new resize handler for the given resizable component child of the given layout
   *
   * @param editor             the associated IDE editor
   * @param component          the component being resized
   * @param horizontalEdgeType the horizontal (top or bottom) edge being resized, if any
   * @param verticalEdgeType   the vertical (left or right) edge being resized, if any
   * @return a new resize handler, or null if the layout does not allow the child to be resized or if the child is not resizable
   */
  @Nullable
  public ResizeHandler createResizeHandler(@NotNull ViewEditor editor,
                                           @NotNull NlComponent component,
                                           @Nullable SegmentType horizontalEdgeType,
                                           @Nullable SegmentType verticalEdgeType) {
    return null;
  }

  @Nullable
  public ScrollHandler createScrollHandler(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    return null;
  }

  /**
   * Called when a child for this view has been created and is being inserted into the
   * view parent for which this {@linkplain ViewHandler} applies. Allows the parent to perform
   * customizations of the object. As with {@link ViewHandler#onCreate}, the {@link InsertType}
   * parameter can be used to handle new creation versus moves versus copy/paste
   * operations differently.
   *
   * @param editor     the associated IDE editor
   * @param layout     the layout being inserted into (which may not yet contain the
   *                   newly created node in its child list)
   * @param newChild   the newly created component
   * @param insertType whether this node was created as part of a newly created view, or
   *                   as a copy, or as a move, etc.
   */
  public void onChildInserted(@NotNull ViewEditor editor,
                              @NotNull NlComponent layout,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType insertType) {
  }

  @Override
  public FillPolicy getFillPolicy() {
    return FillPolicy.BOTH;
  }

  /**
   * Returns true to handles painting the component
   *
   * @return true if the ViewGroupHandler want to be in charge of painting
   */
  public boolean handlesPainting() {
    return false;
  }

  /**
   * Paint the component and its children on the given context
   *
   * @param gc         graphics context
   * @param screenView the current screen view
   * @param component  the component to draw
   * @return true to indicate that we will need to be repainted
   */
  public boolean drawGroup(@NotNull Graphics2D gc, @NotNull ScreenView screenView,
                           @NotNull NlComponent component) {
    // do nothing here, subclasses need to override this and handlesPainting() to be called
    return false;
  }

  /**
   * Give a chance to the ViewGroup to add targets to the {@linkplain SceneComponent}
   *
   * @param sceneComponent The component we'll add the targets on
   * @return The list of created target to add the the component. This list can be empty.
   */
  @Override
  @NotNull
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    return new ArrayList<>();
  }

  public void cleanUpAttributes(@NotNull NlComponent child) {
  }

  /**
   * Let the ViewGroupHandler handle clearing attributes on a given component
   *
   * @param component
   */
  public void clearAttributes(@NotNull NlComponent component) {
    // do nothing
  }

  /**
   * Returns a component provider instance
   *
   * @return the component provider
   */
  public ComponentProvider getComponentProvider(@NotNull SceneComponent component) {
    return null;
  }

  /**
   * Gives a chance to the ViewGroupHandler to handle drop on elements that are not ViewGroup.
   */
  public void performDrop(@NotNull NlModel model,
                          @NotNull DropTargetDropEvent event,
                          @NotNull NlComponent receiver,
                          @NotNull List<NlComponent> dragged,
                          @Nullable NlComponent before,
                          @NotNull InsertType type) {
    // do nothing
  }

  /**
   * Returns the number of children displayed in the component tree for this component
   *
   * @param component the component tree element we are checking
   * @return number of children displayed in the component tree
   */
  public int getComponentTreeChildCount(@NotNull Object component) {
    return ((NlComponent)component).getChildCount();
  }

  /**
   * Returns the child at position i in the given component
   *
   * @param component
   * @param i
   * @return
   */
  public Object getComponentTreeChild(@NotNull Object component, int i) {
    return ((NlComponent)component).getChild(i);
  }
}
