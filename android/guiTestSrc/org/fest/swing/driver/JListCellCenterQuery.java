/*
 * Created on Jun 22, 2009
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Copyright @2009-2013 the original author or authors.
 */
package org.fest.swing.driver;

import static org.fest.swing.awt.AWT.centerOf;
import static org.fest.swing.awt.AWT.centerOfVisibleRect;
import static org.fest.swing.awt.AWT.translate;
import static org.fest.util.Preconditions.checkNotNull;

import java.awt.Point;
import java.awt.Rectangle;

import javax.annotation.Nonnull;
import javax.swing.JList;

import org.fest.swing.annotation.RunsInCurrentThread;

/**
 * Calculates the center of a cell in a {@code JList}.
 *
 * @author Alex Ruiz
 */
final class JListCellCenterQuery {
  /*
   * Sometimes the cell can be a lot longer than the JList (e.g. when a list item has long text and the JList is in a
   * JScrollPane.) In this case, we return the center of visible rectangle of the JList (issue FEST-65.)
   */
  @RunsInCurrentThread
  static @Nonnull Point cellCenter(@Nonnull JList list, @Nonnull Rectangle cellBounds) {
    Point cellCenter = centerOf(cellBounds);
    // REPLACED FEST CODE:
    //Point translated = checkNotNull(translate(list, cellCenter.x, cellCenter.y));
    //int listVisibleWidth = list.getVisibleRect().width;
    //if (translated.x < listVisibleWidth) {
    //  return cellCenter;
    //}

    // NEW CODE: "translated" is screen coordinates relative, while cellBounds is List coordinates relative
    int listVisibleWidth = list.getVisibleRect().width;
    if (cellCenter.x < listVisibleWidth) {
      return cellCenter;
    }
    // END REPLACEMENT

    Point listCenter = centerOfVisibleRect(list);
    return new Point(listCenter.x, cellCenter.y);
  }

  private JListCellCenterQuery() {}
}
