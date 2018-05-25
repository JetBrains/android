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
package com.android.tools.adtui;

import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

public final class TooltipComponent extends AnimatedComponent {
  private static final int TOOLTIP_BORDER_SIZE = 10;

  @NotNull
  private final Component myTooltipContent;

  @NotNull
  private final Component myOwner;

  @NotNull
  private final JLayeredPane myParent;

  @NotNull
  private final Producer<Boolean> myIsOwnerDisplayable;

  // Minimum size of the tooltip between mouse enter/exit events.
  // This prevents the tooltip component from flapping due to the content size changing constantly.
  @NotNull
  private final Dimension myExpandedSize = new Dimension(0, 0);

  private final boolean myAnchoredToOwner;

  private final boolean myShowDropShadow;

  @Nullable
  private Point myLastPoint;

  private final ComponentListener myParentListener;

  private TooltipComponent(@NotNull Builder builder) {
    myTooltipContent = builder.myTooltipContent;
    myOwner = builder.myOwner;
    myParent = builder.myParent;
    myIsOwnerDisplayable = builder.myIsOwnerDisplayable;
    myAnchoredToOwner = builder.myAnchoredToOwner;
    myShowDropShadow = builder.myShowDropShadow;

    myParentListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resetBounds();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        resetBounds();
      }
    };

    add(myTooltipContent);
    setVisible(false);
    resetBounds();

    // Note: invokeLater here is important in order to avoid modifying the hierarchy during a
    // hierarchy event.
    // We usually handle tooltip removal on mouseExit, but we add this here for possible edge
    // cases.
    myOwner.addHierarchyListener(event -> SwingUtilities.invokeLater(() -> {
      if (!myIsOwnerDisplayable.produce()) {
        removeFromParent();
      }
    }));
  }

  private void removeFromParent() {
    setVisible(false);
    if (getParent() != null) {
      getParent().removeComponentListener(myParentListener);
      getParent().remove(this);
    }
  }

  private void resetBounds() {
    setBounds(0, 0, myParent.getWidth(), myParent.getHeight());
  }

  @NotNull
  private static Dimension max(@NotNull Dimension a, @NotNull Dimension b) {
    return new Dimension(Math.max(a.width, b.width), Math.max(a.height, b.height));
  }

  @Override
  public void doLayout() {
    Dimension size = getPreferredSize();
    myExpandedSize.setSize(size.width, 0); // Always let height vary.
    myTooltipContent.setSize(size);
    super.doLayout();
  }

  public void registerListenersOn(@NotNull Component component) {
    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        myExpandedSize.setSize(0, 0);
        myTooltipContent.setSize(0, 0);
        myParent.addComponentListener(myParentListener);
        myParent.add(TooltipComponent.this, JLayeredPane.POPUP_LAYER);
        myLastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TooltipComponent.this);
        setVisible(true);
        revalidate();
        opaqueRepaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myExpandedSize.setSize(0, 0);
        myTooltipContent.setSize(0, 0);
        removeFromParent();
        setVisible(false);
        myLastPoint = null;
        opaqueRepaint();
      }

      private void handleMove(MouseEvent e) {
        myLastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TooltipComponent.this);
        if (!isVisible()) {
          // If we turn invisible, then reset the anti-flap size.
          // This won't work in all cases, since the content could set itself to invisible.
          myExpandedSize.setSize(0, 0);
        }
        if (!myTooltipContent.getPreferredSize().equals(myTooltipContent.getBounds().getSize())) {
          revalidate();
        }
        opaqueRepaint();
      }
    };
    component.addMouseMotionListener(adapter);
    component.addMouseListener(adapter);
  }

  @Override
  public Dimension getPreferredSize() {
    return max(max(myTooltipContent.getPreferredSize(), myTooltipContent.getMinimumSize()), myExpandedSize);
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (!isVisible()) {
      return; // We shouldn't draw the tooltip if its content is not supposed to be visible
    }
    assert myLastPoint != null; // If we're visible, myLastPoint is not null

    // Translate the bounds to clamp it wholly within the parent's drawable region.
    Dimension preferredSize = getPreferredSize();
    Point anchorPoint;
    if (myAnchoredToOwner) {
      anchorPoint = SwingUtilities.convertPoint(myOwner, new Point(0, 0), this);
    }
    else {
      anchorPoint = new Point(myLastPoint);
    }

    int padding = myShowDropShadow ? TOOLTIP_BORDER_SIZE : 0;
    // TODO Investigate if this works for multiple monitors, especially on Macs?
    int x = Math.max(Math.min(anchorPoint.x + padding, dim.width - preferredSize.width - padding), padding);
    int y = Math.max(Math.min(anchorPoint.y + padding, dim.height - preferredSize.height - padding), padding);
    myTooltipContent.setLocation(x, y);

    g.setColor(Color.WHITE);
    g.fillRect(x, y, preferredSize.width, preferredSize.height);
    g.setStroke(new BasicStroke(1.0f));

    if (myShowDropShadow) {
      int lines = 4;
      int[] alphas = new int[]{40, 30, 20, 10};
      RoundRectangle2D.Float rect = new RoundRectangle2D.Float();
      for (int i = 0; i < lines; i++) {
        g.setColor(new Color(0, 0, 0, alphas[i]));
        rect.setRoundRect(x - 1 - i, y - 1 - i, preferredSize.width + 1 + i * 2, preferredSize.height + 1 + i * 2, i * 2 + 2, i * 2 + 2);
        g.draw(rect);
      }
    }
    myTooltipContent.repaint();
  }

  public static class Builder {
    @NotNull private final JComponent myTooltipContent;
    @NotNull private final JComponent myOwner;
    @NotNull private final JLayeredPane myParent;
    @NotNull private Producer<Boolean> myIsOwnerDisplayable;
    private boolean myAnchoredToOwner;
    private boolean myShowDropShadow = true;

    /**
     * Construct a tooltip component to show for a particular {@code owner}. After
     * construction, you should also use {@link TooltipComponent#registerListenersOn(Component)} to ensure the
     * tooltip will show up on mouse movement.
     *
     * @param tooltipContent The content that will be rendered as contents of the tooltip.
     * @param owner          The suggested owner for this tooltip. The tooltip will walk up the
     *                       tree from this owner searching for a proper place to add itself.
     * @param parent         The top-most layered pane this tooltip will paint over.
     */
    public Builder(@NotNull JComponent tooltipContent, @NotNull JComponent owner, @NotNull JLayeredPane parent) {
      myTooltipContent = tooltipContent;
      myOwner = owner;
      myParent = parent;
      myIsOwnerDisplayable = myOwner::isDisplayable;
    }

    /**
     * Provided for tests, since in tests we can't create JFrames (they would throw a headless
     * exception), so calling {@code myOwner.isDisplayble()} directly would always return false.
     */
    @TestOnly
    @NotNull
    public Builder setIsOwnerDisplayable(@NotNull Producer<Boolean> isOwnerDisplayable) {
      myIsOwnerDisplayable = isOwnerDisplayable;
      return this;
    }

    /**
     * @param isAnchored Sets whether the tooltip should be anchored to the top left corner of the owner, instead of following the mouse.
     */
    @NotNull
    public Builder setAnchored(boolean isAnchored) {
      myAnchoredToOwner = isAnchored;
      return this;
    }

    /**
     * @param showDropShadow Sets whether to include drop shadows around the tooltip. This has the effect of adding extra paddings around
     *                       the tooltip which might not always be desirable.
     */
    @NotNull
    public Builder setShowDropShadow(boolean showDropShadow) {
      myShowDropShadow = showDropShadow;
      return this;
    }

    @NotNull
    public TooltipComponent build() {
      return new TooltipComponent(this);
    }
  }
}