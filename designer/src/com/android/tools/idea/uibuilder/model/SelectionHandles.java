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
package com.android.tools.idea.uibuilder.model;

import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.model.SelectionHandle.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The {@link SelectionHandles} of a {@link NlComponent} are the set of
 * {@link SelectionHandle} objects (possibly empty, for non-resizable objects) the user
 * can manipulate to resize a widget.
 */
public class SelectionHandles implements Iterable<SelectionHandle> {
  private final NlComponent myComponent;
  private List<SelectionHandle> myHandles;

  /**
   * Constructs a new {@link SelectionHandles} object for the given {link
   * {@link NlComponent}
   *
   * @param component the item to create {@link SelectionHandles} for
   */
  public SelectionHandles(@NotNull NlComponent component) {
    myComponent = component;
    createHandles();
  }

  /**
   * Find a specific {@link SelectionHandle} from this set of {@link SelectionHandles},
   * which is within the given distance (in layout coordinates) from the center of the
   * {@link SelectionHandle}.
   *
   * @param x        the x mouse position (in Android coordinates) to test
   * @param y        the y mouse position (in Android coordinates) to test
   * @param distance the maximum distance from the handle center to accept
   * @return a {@link SelectionHandle} under the point, or null if not found
   */
  public SelectionHandle findHandle(@AndroidCoordinate int x, @AndroidCoordinate int y,
                                    @AndroidCoordinate int distance) {
    for (SelectionHandle handle : myHandles) {
      if (handle.contains(x, y, distance)) {
        return handle;
      }
    }

    return null;
  }

  /**
   * Create the {@link SelectionHandle} objects for the selection item, according to its
   * {@link ResizePolicy}.
   */
  private void createHandles() {
    ResizePolicy resizability = ResizePolicy.getResizePolicy(myComponent);
    if (resizability.isResizable()) {
      myHandles = new ArrayList<SelectionHandle>(8);
      boolean left = resizability.leftAllowed();
      boolean right = resizability.rightAllowed();
      boolean top = resizability.topAllowed();
      boolean bottom = resizability.bottomAllowed();
      if (left) {
        myHandles.add(new SelectionHandle(myComponent, Position.LEFT_MIDDLE));
        if (top) {
          myHandles.add(new SelectionHandle(myComponent, Position.TOP_LEFT));
        }
        if (bottom) {
          myHandles.add(new SelectionHandle(myComponent, Position.BOTTOM_LEFT));
        }
      }
      if (right) {
        myHandles.add(new SelectionHandle(myComponent, Position.RIGHT_MIDDLE));
        if (top) {
          myHandles.add(new SelectionHandle(myComponent, Position.TOP_RIGHT));
        }
        if (bottom) {
          myHandles.add(new SelectionHandle(myComponent, Position.BOTTOM_RIGHT));
        }
      }
      if (top) {
        myHandles.add(new SelectionHandle(myComponent, Position.TOP_MIDDLE));
      }
      if (bottom) {
        myHandles.add(new SelectionHandle(myComponent, Position.BOTTOM_MIDDLE));
      }
    } else {
      myHandles = Collections.emptyList();
    }
  }

  // Implements Iterable<SelectionHandle>
  @Override
  public Iterator<SelectionHandle> iterator() {
    return myHandles.iterator();
  }
}