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

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.ScreenView;

import java.awt.*;
import java.util.List;

/** A view handler is a tool handler for a given Android view class */
public class ViewHandler {
  /**
   * Called when one or more children are about to be deleted by the user.
   *
   * @param parent  the parent of the deleted children (which still contains
   *                the children since this method is called before the deletion
   *                is performed)
   * @param deleted a nonempty list of children about to be deleted
   * @return true if the children have been fully deleted by this participant; false
   *         if normal deletion should resume. Note that even though an implementation may return
   *         false from this method, that does not mean it did not perform any work. For example,
   *         a RelativeLayout handler could remove constraints pointing to now deleted components,
   *         but leave the overall deletion of the elements to the core designer.
   */
  public boolean deleteChildren(@NonNull NlComponent parent, @NonNull List<NlComponent> deleted) throws Exception {
    return false;
  }

  /**
   * Paints the constraints for this component. If it returns true, it has handled
   * the children as well.
   *
   * @param graphics  the graphics to paint into
   * @param component the component whose constraints we want to paint
   * @return true if we're done with this component <b>and</b> it's children
   */
  public boolean paintConstraints(@NonNull ScreenView screenView, @NonNull Graphics2D graphics, @NonNull NlComponent component) {
    return false;
  }
}
