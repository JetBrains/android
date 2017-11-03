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

import com.android.SdkConstants;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.uibuilder.error.IssuePanel;
import com.android.utils.SdkUtils;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.ui.HintHint;
import com.intellij.ui.JBColor;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.android.tools.idea.common.scene.SceneManager.SUPPORTS_LOCKING;

/**
 * Handler class responsible for drawing badges for each
 * row and handling a click on those badges
 */
public class NlTreeBadgeHandler {

  private static final int BADGE_MARGIN = 5;
  private static final int MESSAGE_WIDTH = 50;
  private static final int BADGE_ICON = 0;
  private static final int LOCK_ICON = 1;

  private static final String TOGGLE_LOCK_MESSAGE = "Toggle Tool Locking";

  private final BadgeMouseMotionListener myBadgeMouseMotionListener = new BadgeMouseMotionListener();
  private final JBLabel myHintLabel = new JBLabel();
  private final Point myHintLocation = new Point();
  @Nullable private LightweightHint myTooltipHint;
  @Nullable private TreePath myHoveredPath;
  private int myHoveredIcon = BADGE_ICON;
  private NlComponent myHoveredComponent;

  @Nullable private NlModel myNlModel;
  private int myBadgeX;
  private int myLockIconX;
  @Nullable private IssuePanel myIssuePanel;

  public void setNlModel(@Nullable NlModel nlModel) {
    myNlModel = nlModel;
  }

  public void setIssuePanel(@Nullable IssuePanel issuePanel) {
    myIssuePanel = issuePanel;
  }

  public void paintBadges(@NotNull Graphics2D g, @NotNull JTree tree) {
    myBadgeX = Integer.MAX_VALUE;
    if (myNlModel == null) {
      return;
    }
    LintAnnotationsModel lintAnnotationsModel = myNlModel.getLintAnnotationsModel();

    for (int i = 0; i < tree.getRowCount(); i++) {
      TreePath path = tree.getPathForRow(i);
      Object last = path.getLastPathComponent();
      if (!(last instanceof NlComponent)) {
        continue;
      }
      NlComponent component = (NlComponent)last;
      Rectangle pathBounds = tree.getPathBounds(path);
      if(pathBounds != null) {
        int y = pathBounds.y + pathBounds.height / 2;
        Icon firstIcon;
        if (lintAnnotationsModel != null) {
          firstIcon = lintAnnotationsModel.getIssueIcon(component, false, tree.isRowSelected(i) && tree.hasFocus());
          if (firstIcon != null) {
            int x = tree.getWidth() - firstIcon.getIconWidth() - BADGE_MARGIN;
            int iy = y - firstIcon.getIconHeight() / 2;
            firstIcon.paintIcon(tree, g, x, iy);
            myBadgeX = Math.min(x, myBadgeX);
          }
        }
        if (SUPPORTS_LOCKING) {
          Icon unlockIcon = AndroidIcons.SherpaIcons.UnlockConstraints;
          Icon lockIcon = AndroidIcons.SherpaIcons.LockConstraints;
          if (firstIcon != null) {
            myLockIconX = myBadgeX - BADGE_MARGIN - lockIcon.getIconWidth();
          }
          else {
            myLockIconX = tree.getWidth() - BADGE_MARGIN - lockIcon.getIconWidth();
          }
          boolean isLocked = SceneManager.isComponentLocked(component);
          boolean isOver = myHoveredComponent == component && myHoveredIcon == LOCK_ICON;
          if (!isOver && !isLocked) {
            // if we are not over the icon, we only draw it when the component is invisible
            // (as visible is the default state)
            continue;
          }
          Icon lockingIcon = isLocked ? lockIcon : unlockIcon;
          if (isOver) {
            // in this case, show instead the icon of the state we would end up if we click on it
            lockingIcon = isLocked ? unlockIcon : lockIcon;
          }
          lockingIcon.paintIcon(tree, g, myLockIconX, y - lockingIcon.getIconHeight() / 2);
        }
      }
    }
  }

  /**
   * Shows a tooltip containing the message at the right of the given path.
   *
   * @param tree    The tree containing the path
   * @param path    The path containing the badge
   * @param message The message to show in the tooltip
   */
  private void showErrorTooltip(@NotNull JTree tree, @NotNull TreePath path, @NotNull String message) {
    Window activeFrame = UIUtil.getWindow(tree);
    if (activeFrame == null) {
      return;
    }
    Rectangle bounds = tree.getPathBounds(path);
    if (bounds != null) {
      HintHint hint = createHint(tree);
      message = SdkUtils.wrap(message, MESSAGE_WIDTH, null);
      message = HintUtil.prepareHintText(message, hint);
      myHintLabel.setText(message);
      myHintLocation.x = tree.getWidth() - BADGE_MARGIN;
      myHintLocation.y = bounds.y + bounds.height / 2;

      hint = ensureHintCanBeDisplayed(hint, message, tree, activeFrame);
      if (hint == null) {
        // The window is too small to display the hint so we don't show it
        return;
      }
      if (myTooltipHint == null) {
        myTooltipHint = new LightweightHint(myHintLabel);
      }
      myTooltipHint.show(tree, myHintLocation.x, myHintLocation.y, tree, hint);
    }
  }

  /**
   * Check if the provided hint containing the provided message can be displayed as is
   * in the provided window.
   * <p>
   * If the window is large enough to display the hint at its current position,
   * the provided hint is returned as is.
   * <p>
   * If the window is large enough but the hint needs to be moved to ensure that it fit inside,
   * a the hint is moved to the left and will be shown below the badge.
   * <p>
   * If the window too small to display the hint, the method return null so no hint should be displayed.
   *
   * @param hint        The hint that will be displayed
   * @param message     The message showing in the hint. Used to measure the hint width.
   * @param component   The component hosting the hint
   * @param activeFrame The window of the component
   * @return The provided hint, modified if needed, if it can be shown or null if the window is too small.
   */
  @Nullable
  private HintHint ensureHintCanBeDisplayed(@NotNull HintHint hint,
                                            @NotNull String message,
                                            @NotNull Component component,
                                            @NotNull Window activeFrame) {
    int labelWidth = myHintLabel.getFontMetrics(myHintLabel.getFont())
      .stringWidth(message.substring(0, Math.min(message.length(), MESSAGE_WIDTH)));
    int labelRight = component.getLocationOnScreen().x + component.getWidth() + labelWidth;
    int frameRight = activeFrame.getLocationOnScreen().x + activeFrame.getWidth();

    if (activeFrame.getWidth() < labelWidth) {
      return null;
    }

    if (frameRight < labelRight) {
      myHintLocation.x = BADGE_MARGIN;
      hint.setPreferredPosition(Balloon.Position.below);
    }
    return hint;
  }

  /**
   * Create a hint in owned by the given component
   */
  private HintHint createHint(@NotNull Component component) {
    return new HintHint(component, myHintLocation)
      .setPreferredPosition(Balloon.Position.atRight)
      .setBorderColor(JBColor.border())
      .setAwtTooltip(true)
      .setTextBg(HintUtil.getInformationColor())
      .setShowImmediately(true)
      .setExplicitClose(true)
      .setAnimationEnabled(true);
  }

  /**
   * If the given path's {@link NlComponent} has an issue, return the issue message.
   *
   * @param path The path to get the issue message from
   * @return The issue message or null if no issue
   */
  @Nullable
  private static String getIssueMessage(@NotNull TreePath path) {
    Object last = path.getLastPathComponent();
    if (!(last instanceof NlComponent)) {
      return null;
    }
    NlComponent component = (NlComponent)last;
    LintAnnotationsModel lintAnnotationsModel = component.getModel().getLintAnnotationsModel();
    if (lintAnnotationsModel != null) {
      return lintAnnotationsModel.getIssueMessage(component, false);
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

  private class BadgeMouseMotionListener extends MouseAdapter {

    @Override
    public void mouseExited(MouseEvent event) {
      if (myTooltipHint != null) {
        myTooltipHint.hide(true);
        myTooltipHint = null;
      }
      myHoveredComponent = null;
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
      if (SUPPORTS_LOCKING) {
        limit = myLockIconX;
      }
      if (event.getX() < limit || myIssuePanel == null) {
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
        myIssuePanel.showIssueForComponent(component, true);
      } else {
        if (SUPPORTS_LOCKING) {
          toggleLocking(component);
        }
      }
    }

    private void toggleLocking(@NotNull NlComponent component) {
      boolean isLocked = SceneManager.isComponentLocked(component);
      if (component.getParent() == null) {
        // Don't do anything for the root component
        return;
      }

      NlWriteCommandAction.run(component, "", () ->
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LOCKED, isLocked ? null : SdkConstants.VALUE_TRUE));
    }

    private void handleShowBadge(@NotNull MouseEvent event, @NotNull JTree tree) {
      if (event.getX() < myLockIconX) {
        // We only show the tooltip if the mouse id hovering the badge
        myHoveredPath = null;
        myHoveredComponent = null;
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
        myHoveredComponent = null;
        hideTooltip();
        return;
      }

      Object last = path.getLastPathComponent();
      if (!(last instanceof NlComponent)) {
        return;
      }
      myHoveredComponent = (NlComponent)last;

      boolean badgeIcon = true;
      if (SUPPORTS_LOCKING) {
        if (event.getX() < myBadgeX) {
          badgeIcon = false;
        }
      }

      // Don't do anything if the path under the mouse is still the same
      if (myHoveredPath == path) {
        if (badgeIcon && myHoveredIcon == BADGE_ICON) {
          return;
        }
        if (!badgeIcon && myHoveredIcon == LOCK_ICON) {
          return;
        }
      }
      myHoveredPath = path;

      String message = null;
      if (badgeIcon) {
        message = getIssueMessage(myHoveredPath);
        myHoveredIcon = BADGE_ICON;
      } else if (SUPPORTS_LOCKING) {
        if (myHoveredComponent.getParent() != null) {
          message = TOGGLE_LOCK_MESSAGE;
          myHoveredIcon = LOCK_ICON;
        }
      }
      if (message != null) {
        showErrorTooltip(tree, myHoveredPath, message);
      }
    }

    private void hideTooltip() {
      if (myTooltipHint != null) {
        myTooltipHint.hide(true);
      }
    }
  }
}
