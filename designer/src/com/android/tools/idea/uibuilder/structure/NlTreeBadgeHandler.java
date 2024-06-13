/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.common.error.Issue;
import com.android.tools.idea.common.error.IssueModel;
import com.android.tools.idea.common.error.IssuePanelService;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.utils.SparseIntArray;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


/**
 * Handler class responsible for drawing badges for each
 * row and handling a click on those badges
 */
public class NlTreeBadgeHandler {

  private static final int BADGE_MARGIN = 5;

  private final BadgeMouseMotionListener myBadgeMouseMotionListener = new BadgeMouseMotionListener();
  @Nullable private LightweightHint myTooltipHint;
  @Nullable private TreePath myHoveredPath;

  @Nullable private NlModel myNlModel;
  private int myBadgeX;
  @Nullable private DesignSurface<?> mySurface;

  /**
   * Save the width occupied by the badges at a given row.
   */
  private final SparseIntArray myBadgeWidthForRows = new SparseIntArray();


  public void setNlModel(@Nullable NlModel nlModel) {
    myNlModel = nlModel;
  }

  public void setSurface(@Nullable DesignSurface<?> surface) {
    mySurface = surface;
  }

  public void paintBadges(@NotNull Graphics2D g, @NotNull NlComponentTree tree) {
    myBadgeX = Integer.MAX_VALUE;
    myBadgeWidthForRows.clear();
    if (myNlModel == null) {
      return;
    }

    for (int i = 0; i < tree.getRowCount(); i++) {
      TreePath path = tree.getPathForRow(i);
      Object last = path.getLastPathComponent();
      if (!(last instanceof NlComponent)) {
        continue;
      }
      NlComponent component = (NlComponent)last;
      Rectangle pathBounds = tree.getPathBounds(path);
      Issue issue = null;
      if (mySurface != null) {
        issue = mySurface.getIssueModel().getHighestSeverityIssue(component);
      }
      if (pathBounds != null) {
        int y = pathBounds.y + pathBounds.height / 2;
        if (issue != null) {
          Icon firstIcon = IssueModel.getIssueIcon(issue.getSeverity(), tree.isRowSelected(i) && tree.hasFocus());
          if (firstIcon != null) {
            int x = tree.getWidth() - firstIcon.getIconWidth() - BADGE_MARGIN;
            int iy = y - firstIcon.getIconHeight() / 2;
            firstIcon.paintIcon(tree, g, x, iy);
            myBadgeWidthForRows.put(i, firstIcon.getIconWidth());
            myBadgeX = Math.min(x, myBadgeX);
          }
        }
      }
    }
  }

  /**
   * If the given path's {@link NlComponent} has an issue, return the issue message.
   *
   * @param path The path to get the issue message from
   * @return The issue message or null if no issue
   */
  @Nullable
  private String getIssueMessage(@NotNull TreePath path) {
    Object last = path.getLastPathComponent();
    if (!(last instanceof NlComponent)) {
      return null;
    }
    NlComponent component = (NlComponent)last;
    Issue max = null;
    IssueModel issueModel = mySurface != null ? mySurface.getIssueModel() : null;
    if (issueModel != null) {
      max = issueModel.getHighestSeverityIssue(component);
    }
    if (max != null) {
      return "<html>" + max.getSummary() + "<br>Click the badge for detail.</html>";
    }
    return null;
  }

  /**
   * @return A mouse adapter handling mouse event on the error badges
   */
  @NotNull
  public MouseAdapter getBadgeMouseAdapter() {
    return myBadgeMouseMotionListener;
  }

  public int getTotalBadgeWidth(int row) {
    return myBadgeWidthForRows.get(row, 0) + BADGE_MARGIN;
  }

  private class BadgeMouseMotionListener extends MouseAdapter {

    @Override
    public void mouseExited(MouseEvent event) {
      if (myTooltipHint != null) {
        myTooltipHint.hide(true);
        myTooltipHint = null;
      }
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent event) {
      JTree tree = (JTree)event.getSource();
      handleShowBadge(event, tree);
    }

    @Override
    public void mouseClicked(MouseEvent event) {
      handleMouseClicked(event, ((JTree)event.getSource()));
    }

    private void handleMouseClicked(MouseEvent event, JTree tree) {
      int limit = myBadgeX;
      IssueModel issueModel = mySurface != null ? mySurface.getIssueModel() : null;
      if (event.getX() < limit || issueModel == null) {
        // We only show the tooltip if the mouse id hovering the badge
        return;
      }
      TreePath path = tree.getClosestPathForLocation(event.getX(), event.getY());
      Rectangle bounds;

      if (path == null ||
          (bounds = tree.getPathBounds(path)) == null ||
          // Ensure the tooltip is not displayed if the mouse is below the last row
          event.getY() < bounds.y || event.getY() > bounds.y + bounds.height) {
        return;
      }
      Object last = path.getLastPathComponent();
      if (!(last instanceof NlComponent)) {
        return;
      }
      NlComponent component = (NlComponent)last;
      if (event.getX() > myBadgeX) {
        IssuePanelService.getInstance(component.getModel().getProject()).showIssueForComponent(mySurface, component);
      }

    }

    private void handleShowBadge(@NotNull MouseEvent event, @NotNull JTree tree) {
      if (event.getX() < myBadgeX) {
        // We only show the tooltip if the mouse id hovering the badge
        myHoveredPath = null;
        hideTooltip();
        return;
      }
      TreePath path = tree.getClosestPathForLocation(event.getX(), event.getY());
      Rectangle bounds;

      if (path == null ||
          (bounds = tree.getPathBounds(path)) == null ||
          // Ensure the tooltip is not displayed if the mouse is below the last row
          event.getY() < bounds.y || event.getY() > bounds.y + bounds.height) {
        myHoveredPath = null;
        hideTooltip();
        return;
      }

      Object last = path.getLastPathComponent();
      if (!(last instanceof NlComponent)) {
        return;
      }

      // Don't do anything if the path under the mouse is still the same
      if (myHoveredPath == path) {
        return;
      }
      myHoveredPath = path;

      String message = getIssueMessage(myHoveredPath);
      if (message != null) {
        tree.setToolTipText(message);
      }
    }

    private void hideTooltip() {
      if (myTooltipHint != null) {
        myTooltipHint.hide(true);
      }
    }
  }
}
