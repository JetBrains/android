/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

import com.android.tools.adtui.common.StudioColorsKt;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.ListExpandableItemsHandler;
import com.intellij.ui.components.JBList;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

/**
 * A list control that supports expansion of labels that could be truncated and
 * have ellipses added. In addition the list may have a margin to the right of
 * the labels which may hold arbitrary data.
 *
 * The list is given a specialized {@link ExpandableItemsHandler} which is able
 * to expand the label on top of the right margin or even outside of the right
 * edge of the list control.
 */
public abstract class ListWithMargin<E> extends JBList<E> {

  public ListWithMargin() {
    super();
    setSelectionMode(SINGLE_SELECTION);
    setBackground(StudioColorsKt.getSecondaryPanelBackground());
  }

  /**
   * Returns the width of the margin to the right of the label in a list cell.
   */
  protected abstract int getRightMarginWidth();

  /**
   * Customized {@link ExpandableItemsHandler} that is built on top of a standard
   * {@link ListWithMarginExpandableItemsHandler} to enable expansion of labels
   * that fit inside the list cell but expands into the given right margin.
   */
  @Override
  @NotNull
  protected ExpandableItemsHandler<Integer> createExpandableItemsHandler() {
    return new ListWithMarginExpandableItemsHandler(this);
  }

  private static class ListWithMarginExpandableItemsHandler extends ListExpandableItemsHandler {
    private Integer myExpansionIndex;

    protected ListWithMarginExpandableItemsHandler(@NotNull ListWithMargin list) {
      super(list);
      myComponent.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseExited(@NotNull MouseEvent event) {
          hideExpansion();
        }
      });
    }

    private int getRightMarginWidth() {
      return ((ListWithMargin)myComponent).getRightMarginWidth();
    }

    @NotNull
    @Override
    public Collection<Integer> getExpandedItems() {
      Collection<Integer> popup = super.getExpandedItems();
      if (!popup.isEmpty()) {
        return popup;
      }
      return myExpansionIndex == null ? Collections.emptyList() : Collections.singleton(myExpansionIndex);
    }

    /**
     * This method is called by a standard {@link ListWithMarginExpandableItemsHandler} to determine if
     * the cell should be expanded with a popup control that shows the entire label bleeding outside of the list control.
     *
     * Here we first check if label is overlapping the right margin without bleeding outside of the list control.
     * If that is the case we register this index as the current expanded row in the list and force a repaint of that row.
     * The list is then supposed to draw the full label without the content in the right margin.
     *
     * If this is not the case simply delegate to the standard {@link ListWithMarginExpandableItemsHandler} that has code
     * for displaying a popup with the full label.
     */
    @Override
    protected Pair<Component, Rectangle> getCellRendererAndBounds(@NotNull Integer index) {
      Pair<Component, Rectangle> rendererAndBounds = super.getCellRendererAndBounds(index);
      if (rendererAndBounds == null) {
        hideExpansion();
        return null;
      }
      Rectangle bounds = rendererAndBounds.second;
      if (bounds.width > myComponent.getWidth() - getRightMarginWidth() && bounds.width < myComponent.getWidth()) {
        myExpansionIndex = index;
        Rectangle cellBounds = myComponent.getCellBounds(myExpansionIndex, myExpansionIndex);
        if (cellBounds != null) {
          myComponent.repaint(cellBounds);
        }
        return null;
      }
      hideExpansion();
      return rendererAndBounds;
    }

    @Override
    protected void onFocusLost() {
      super.onFocusLost();
      hideExpansion();
    }

    private void hideExpansion() {
      if (myExpansionIndex != null) {
        Rectangle bounds = myComponent.getCellBounds(myExpansionIndex, myExpansionIndex);
        if (bounds != null) {
          // The bounds may be null (seen on Linux during tearDown a test)
          myComponent.repaint(bounds);
        }
      }
      myExpansionIndex = null;
    }
  }
}
