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
  private final Component myContent;

  @NotNull
  private final Component myOwner;

  @NotNull
  private final Producer<Boolean> myIsOwnerDisplayable;

  private final boolean myAnchoredToOwner;

  private final boolean myShowDropShadow;

  @Nullable
  private final Class<? extends JLayeredPane> myPreferredParentClass;

  @Nullable
  private Point myLastPoint;

  private final ComponentListener myParentListener;

  private TooltipComponent(@NotNull Builder builder) {
    myContent = builder.myContent;
    myOwner = builder.myOwner;
    myIsOwnerDisplayable = builder.myIsOwnerDisplayable;
    myPreferredParentClass = builder.myPreferredParentClass;
    myAnchoredToOwner = builder.myAnchoredToOwner;
    myShowDropShadow = builder.myShowDropShadow;

    add(myContent);
    removeFromParent();

    // Note: invokeLater here is important in order to avoid modifying the hierarchy during a
    // hierarchy event.
    // We usually handle tooltip removal on mouseexit, but we add this here for possible edge
    // cases.
    myOwner.addHierarchyListener(event -> SwingUtilities.invokeLater(() -> {
      if (!myIsOwnerDisplayable.produce()) {
        removeFromParent();
      }
    }));

    myParentListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        setBounds();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        setBounds();
      }
    };
  }

  private void recomputeParent() {
    if (!myIsOwnerDisplayable.produce()) {
      // No need to walk ancestors if the owner has been removed from the hierarchy
      removeFromParent();
      return;
    }

    JLayeredPane layeredPane = null;
    for (Component c : new TreeWalker(myOwner).ancestors()) {
      if (c instanceof JLayeredPane) {
        layeredPane = (JLayeredPane)c;
        if (c.getClass() == myPreferredParentClass) {
          break;
        }
      }
      else if (c instanceof RootPaneContainer) {
        layeredPane = ((RootPaneContainer)c).getLayeredPane();
      }
    }

    if (layeredPane == getParent()) {
      return;
    }

    removeFromParent();
    if (layeredPane != null) {
      setParent(layeredPane);
    }
  }

  private void removeFromParent() {
    setVisible(false);
    if (getParent() != null) {
      getParent().removeComponentListener(myParentListener);
      getParent().remove(this);
    }
  }

  private void setParent(@NotNull JLayeredPane parent) {
    setVisible(true);
    parent.add(this, JLayeredPane.POPUP_LAYER);
    parent.addComponentListener(myParentListener);
    setBounds();
  }

  private void setBounds() {
    Container parent = getParent();
    if (parent == null) {
      return;
    }
    setBounds(0, 0, parent.getWidth(), parent.getHeight());
  }

  public void registerListenersOn(Component component) {
    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        handleMove(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myLastPoint = null;
        if (isVisible()) {
          removeFromParent();
        }
        opaqueRepaint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        handleMove(e);
      }

      private void handleMove(MouseEvent e) {
        if (!isVisible()) {
          // Recalculate the dimensions of the content prior to recomputing the parent, as recomputing the parent will cause a relayout.
          Rectangle oldBounds = myContent.getBounds();
          Dimension preferredSize = getPreferredSize();
          myContent.setBounds(oldBounds.x, oldBounds.y, preferredSize.width, preferredSize.height);

          recomputeParent();
        }
        myLastPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TooltipComponent.this);
        opaqueRepaint();
      }
    };
    component.addMouseMotionListener(adapter);
    component.addMouseListener(adapter);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension preferredSize = myContent.getPreferredSize();
    Dimension minSize = myContent.getMinimumSize();
    return new Dimension(Math.max(preferredSize.width, minSize.width), Math.max(preferredSize.height, minSize.height));
  }

  @Override
  protected void draw(Graphics2D g, Dimension dim) {
    if (!myContent.isVisible()) {
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
    myContent.setBounds(x, y, preferredSize.width, preferredSize.height);

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
    myContent.repaint();
  }

  public static class Builder {
    @NotNull private final Component myContent;
    @NotNull private final Component myOwner;
    @NotNull private Producer<Boolean> myIsOwnerDisplayable;
    @Nullable private Class<? extends JLayeredPane> myPreferredParentClass;
    private boolean myAnchoredToOwner;
    private boolean myShowDropShadow = true;

    /**
     * Construct a tooltip component to show for a particular {@code owner}. After
     * construction, you should also use {@link TooltipComponent#registerListenersOn(Component)} to ensure the
     * tooltip will show up on mouse movement.
     *
     * @param owner The suggested owner for this tooltip. The tooltip will walk up the
     *              tree from this owner searching for a proper place to add itself.
     */
    public Builder(@NotNull Component content, @NotNull Component owner) {
      myContent = content;
      myOwner = owner;
      myIsOwnerDisplayable = myOwner::isDisplayable;
    }

    /**
     * @param preferredParentClass If not {@code null}, the type of pane to use as this tooltip's
     *                             parent (useful if you have a specific parent pane in mind)
     */
    @NotNull
    public Builder setPreferredParentClass(@Nullable Class<? extends JLayeredPane> preferredParentClass) {
      myPreferredParentClass = preferredParentClass;
      return this;
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