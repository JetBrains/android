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

import static com.android.tools.idea.uibuilder.api.actions.ViewActionUtils.getToggleSizeActions;
import static com.android.tools.idea.uibuilder.api.actions.ViewActionUtils.getViewOptionsAction;

import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TargetProvider;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.api.actions.DirectViewAction;
import com.android.tools.idea.uibuilder.api.actions.ToggleViewAction;
import com.android.tools.idea.uibuilder.api.actions.ToggleViewActionGroup;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.api.actions.ViewActionMenu;
import com.android.tools.idea.uibuilder.api.actions.ViewActionPresentation;
import com.android.tools.idea.uibuilder.model.FillPolicy;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A view handler is a tool handler for a given Android view class
 */
public class ViewHandler extends StructurePaneComponentHandler implements TargetProvider {
  /**
   * Returns whether the given component accepts the given parent layout as a potential container
   *
   * @param layout   the layout being inserted into (which does not yet contain the
   *                 newly created node in its child list)
   * @param newChild the newly created component
   * @return true if the proposed parent is accepted
   */
  public boolean acceptsParent(@NotNull NlComponent layout,
                               @NotNull NlComponent newChild) {
    return true;
  }

  /**
   * Called when a view for this handler is being created. This allows for the handler to
   * customize the newly created object. Note that this method is called not just when a
   * view is created from a palette drag, but when views are constructed via a drag-move
   * (where views are created in the destination and then deleted from the source), and
   * even when views are constructed programmatically from other view handlers. The
   * {@link InsertType} parameter can be used to distinguish the context for the
   * insertion. For example, the <code>DialerFilterHandler</code> can insert EditText children
   * when a DialerFilter is first created, but not during a copy/paste or a move.
   *
   * @param parent     the parent of the node, if any (which may not yet contain the newly created
   *                   node in its child list)
   * @param newChild   the newly created node (which will always be a View that applies to
   *                   this {@linkplain ViewHandler})
   * @param insertType whether this node was created as part of a newly created view, or
   * @return true, or false if the view handler wants to cancel this component
   * creation. This typically happens if for example the view handler tries
   * to customize the component by for example asking the user for a specific
   * resource (via {@link ViewEditor#displayResourceInput(NlModel, String, EnumSet)}),
   * and then the user cancels that dialog. At that point we don't want an
   * unconfigured component lingering around, so the component create handler
   * cancels the drop instead by returning false.
   */
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    return true;
  }

  public FillPolicy getFillPolicy() {
    return FillPolicy.WIDTH_IN_VERTICAL;
  }

  /**
   * Adds relevant toolbar view actions that apply for views of this type to the
   * given list.
   * <p>
   * They do not need to be in sorted order; actions from multiple sources will
   * all be merged and sorted before display by the IDE. Note that this method
   * may only be called once, so the set of actions should not depend on specific
   * current circumstances; instead, actions which do not apply should be made
   * disabled or invisible by calling the right methods from
   * {@link ViewAction#updatePresentation(ViewActionPresentation, ViewEditor, ViewHandler, NlComponent, List, int)}
   *
   * @param actions a list of view actions, such as a
   *                {@link DirectViewAction}, {@link ToggleViewAction}, {@link ToggleViewActionGroup}, etc.
   */
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    actions.add(getViewOptionsAction());
    actions.addAll(getToggleSizeActions());
    // TODO: Gravity, etc
  }

  /**
   * Adds relevant context menu view actions that apply for views of this type
   * to the given list.
   * <p>
   * They do not need to be in sorted order; actions from multiple sources will
   * all be merged and sorted before display by the IDE. Note that this method
   * may only be called once, so the set of actions should not depend on specific
   * current circumstances; instead, actions which do not apply should be made
   * disabled or invisible by calling the right methods from
   * {@link ViewAction#updatePresentation(ViewActionPresentation, ViewEditor, ViewHandler, NlComponent, List, int)}
   * @param component the component clicked on
   * @param actions a list of view actions, such as a
   *                {@link DirectViewAction}, {@link ToggleViewAction}, {@link ToggleViewActionGroup}, etc.
   * @return true if the actions should be cached, false otherwise
   */
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    return true;
  }

  /**
   * Utility method which exposes the toolbar actions in a submenu
   */
  protected void addToolbarActionsToMenu(@NotNull String label, @NotNull List<ViewAction> actions) {
    List<ViewAction> nestedActions = new ArrayList<>();
    addToolbarActions(nestedActions);
    actions.add(new ViewActionMenu(label, null, nestedActions));
  }

  /**
   * Handles a double click on the component in the component tree
   */
  public void onActivateInComponentTree(@NotNull NlComponent component) {
    // Do nothing
  }

  /**
   * Handles a double click on the component in the design surface
   *
   * @param x the x coordinate of the double click converted to pixels in the Android coordinate system
   * @param y the y coordinate of the double click converted to pixels in the Android coordinate system
   */
  public void onActivateInDesignSurface(@NotNull NlComponent component, @AndroidCoordinate int x, @AndroidCoordinate int y) {
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
    return ImmutableList.of();
  }

  /**
   * Get the associated {@link Placeholder}s of the given {@link SceneComponent}.
   *
   * @param component        The {@link SceneComponent} which associates to this ViewGroupHandler
   * @param draggedComponents The {@link SceneComponent}s which are dragging.
   */
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component, @NotNull List<SceneComponent> draggedComponents) {
    return ImmutableList.of();
  }

  public List<ViewAction> getPropertyActions(@NotNull List<NlComponent> components) {
    return ImmutableList.of();
  }

  @NotNull
  public String generateBaseId(@NotNull NlComponent component) {
    return component.getTagName();
  }
}
