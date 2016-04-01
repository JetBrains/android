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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.FillPolicy;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.ScreenView;

import java.awt.*;
import java.util.EnumSet;

/** A view handler is a tool handler for a given Android view class */
public class ViewHandler extends StructurePaneComponentHandler {
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
   * @param editor     the associated editor
   * @param parent     the parent of the node, if any (which may not yet contain the newly created
   *                   node in its child list)
   * @param newChild   the newly created node (which will always be a View that applies to
   *                   this {@linkplain ViewHandler})
   * @param insertType whether this node was created as part of a newly created view, or
   * @return true, or false if the view handler wants to cancel this component
   * creation. This typically happens if for example the view handler tries
   * to customize the component by for example asking the user for a specific
   * resource (via {@link ViewEditor#displayResourceInput(EnumSet, String)}),
   * and then the user cancels that dialog. At that point we don't want an
   * unconfigured component lingering around, so the component create handler
   * cancels the drop instead by returning false.
   */
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    return true;
  }

  /**
   * Paints the constraints for this component. If it returns true, it has handled
   * the children as well.
   *
   * @param graphics  the graphics to paint into
   * @param component the component whose constraints we want to paint
   * @return true if we're done with this component <b>and</b> it's children
   */
  public boolean paintConstraints(@NotNull ScreenView screenView, @NotNull Graphics2D graphics, @NotNull NlComponent component) {
    return false;
  }

  public FillPolicy getFillPolicy() {
    return FillPolicy.WIDTH_IN_VERTICAL;
  }
}
