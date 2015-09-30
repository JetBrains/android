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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class ImagePanel extends JPanel {
  private static final int ZOOM_AMOUNT = 5;
  private static final int SCROLL_AMOUNT = 15;

  @NotNull private final ImageComponent myImage;

  public ImagePanel() {
    super(new BorderLayout());

    JBScrollPane scrollPane = new JBScrollPane();
    myImage = new ImageComponent(scrollPane);
    scrollPane.getVerticalScrollBar().setUnitIncrement(20);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
    scrollPane.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    add(scrollPane, BorderLayout.CENTER);
    setFocusable(true);
  }

  public void addToolbarActions(DefaultActionGroup group) {
    myImage.addToolbarActions(group);
  }

  public StatusText getEmptyText() {
    return myImage.getEmptyText();
  }

  public void setImage(Image image) {
    myImage.setImage(image);
  }

  private static final class ImageComponent extends JComponent {
    private static final double ZOOM_FIT = Double.POSITIVE_INFINITY;
    private static final double MAX_ZOOM_FACTOR = 8;
    private static final double MIN_ZOOM_WIDTH = 100.0;
    private static final BufferedImage EMPTY_IMAGE = UIUtil.createImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);

    private final JViewport parent;
    private final StatusText emptyText;
    private Image image = EMPTY_IMAGE;
    private double zoom;

    public ImageComponent(JBScrollPane scrollPane) {
      scrollPane.setViewportView(this);
      this.parent = scrollPane.getViewport();
      this.emptyText = new StatusText() {
        @Override
        protected boolean isStatusVisible() {
          return image == EMPTY_IMAGE;
        }
      };
      this.emptyText.attachTo(parent);
      this.zoom = ZOOM_FIT;

      MouseAdapter mouseHandler = new MouseAdapter() {
        private int lastX, lastY;

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          zoom(Math.max(-ZOOM_AMOUNT, Math.min(ZOOM_AMOUNT, e.getWheelRotation())), e.getPoint());
        }

        @Override
        public void mousePressed(MouseEvent e) {
          lastX = e.getX();
          lastY = e.getY();

          if (isPanningButton(e)) {
            setCursor(new Cursor(Cursor.MOVE_CURSOR));
          }
          else {
            zoomToFit();
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          setCursor(null);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          int dx = lastX - e.getX(), dy = lastY - e.getY();
          lastX = e.getX();
          lastY = e.getY();

          if (isPanningButton(e)) {
            scrollBy(dx, dy);
          }
        }

        private boolean isPanningButton(MouseEvent e) {
          // Pan for either the primary mouse button or the mouse wheel.
          return (e.getModifiersEx() & (InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK)) != 0;
        }
      };

      // Add the mouse listeners to the scrollpane, so the coordinates stay consistent.
      parent.addMouseListener(mouseHandler);
      parent.addMouseWheelListener(mouseHandler);
      parent.addMouseMotionListener(mouseHandler);

      addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_K:
              scrollBy(0, -SCROLL_AMOUNT);
              break;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_J:
              scrollBy(0, SCROLL_AMOUNT);
              break;
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_H:
              scrollBy(-SCROLL_AMOUNT, 0);
              break;
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_L:
              scrollBy(SCROLL_AMOUNT, 0);
              break;
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_ADD:
              zoom(-ZOOM_AMOUNT, getCenterPoint());
              break;
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_SUBTRACT:
              zoom(ZOOM_AMOUNT, getCenterPoint());
              break;
            case KeyEvent.VK_EQUALS:
              if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                zoom(-ZOOM_AMOUNT, getCenterPoint());
              }
              else {
                zoomToFit();
              }
              break;
          }
        }
      });
    }

    public StatusText getEmptyText() {
      return emptyText;
    }

    public void setImage(Image image) {
      if (this.image == EMPTY_IMAGE) {
        // Ignore any zoom actions that might have happened before the first real image was shown.
        zoomToFit();
      }
      this.image = (image == null) ? EMPTY_IMAGE : image;
      revalidate();
      repaint();
    }

    public void addToolbarActions(DefaultActionGroup group) {
      group.add(new AnAction("Zoom to Fit", "Fit the image to the panel", AndroidIcons.ZoomFit) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          zoomToFit();
        }
      });
      group.add(new AnAction("Actual Size", "Display the image at its actual size", AndroidIcons.ZoomActual) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          zoomToActual();
        }
      });
      group.add(new AnAction("Zoom In", "Zoom In", AndroidIcons.ZoomIn) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          zoom(-ZOOM_AMOUNT, getCenterPoint());
        }
      });
      group.add(new AnAction("Zoom Out", "Zoom Out", AndroidIcons.ZoomOut) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          zoom(ZOOM_AMOUNT, getCenterPoint());
        }
      });
    }

    @Override
    public Dimension getPreferredSize() {
      return (zoom == ZOOM_FIT)
             ? new Dimension(parent.getWidth(), parent.getHeight())
             : new Dimension((int)(zoom * image.getWidth(this)), (int)(zoom * image.getHeight(this)));
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (image == EMPTY_IMAGE) {
        emptyText.paint(parent, g);
        return;
      }

      double scale = (zoom == ZOOM_FIT) ? getFitRatio() : zoom;
      int w = (int)(image.getWidth(this) * scale), h = (int)(image.getHeight(this) * scale);
      g.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, this);
    }

    private void scrollBy(int dx, int dy) {
      if (dx == 0 && dy == 0) {
        // Do the revalidate and repaint that scrollRectoToVisible would do.
        revalidate();
        repaint();
      }
      else {
        // The passed rectangle is relative to the currently visible rectangle, i.e. it is not in view coordinates.
        parent.scrollRectToVisible(new Rectangle(new Point(dx, dy), parent.getExtentSize()));
      }
    }

    private Point getCenterPoint() {
      return new Point(parent.getWidth() / 2, parent.getHeight() / 2);
    }

    private void zoom(int amount, Point cursor) {
      Dimension oldSize = getPreferredSize();
      oldSize.setSize(Math.max(parent.getWidth(), oldSize.width), Math.max(parent.getHeight(), oldSize.height));

      if (zoom == ZOOM_FIT) {
        zoom = getFitRatio();
      }
      int delta = Math.min(Math.max(amount, -5), 5);
      zoom = Math.min(getMaxZoom(), Math.max(getMinZoom(), zoom * (1 - 0.05 * delta)));
      invalidate();

      Dimension newSize = getPreferredSize();
      newSize.setSize(Math.max(parent.getWidth(), newSize.width), Math.max(parent.getHeight(), newSize.height));

      // Attempt to keep the same pixel under the mouse pointer.
      Point pos = parent.getViewPosition();
      pos.translate(cursor.x, cursor.y);
      scrollBy(pos.x * newSize.width / oldSize.width - pos.x, pos.y * newSize.height / oldSize.height - pos.y);
    }

    private void zoomToFit() {
      zoom = ZOOM_FIT;
      revalidate();
      repaint();
    }

    private void zoomToActual() {
      zoom = 1;
      revalidate();
      repaint();
    }

    private double getFitRatio() {
      return Math.min((double)getWidth() / image.getWidth(this), (double)getHeight() / image.getHeight(this));
    }

    private double getMinZoom() {
      // The smallest zoom factor to see the whole image or that causes the larger dimension to be no less than MIN_ZOOM_WIDTH pixels.
      return Math.min(1, Math.min(getFitRatio(), Math.min(MIN_ZOOM_WIDTH / image.getWidth(this), MIN_ZOOM_WIDTH / image.getHeight(this))));
    }

    private double getMaxZoom() {
      return Math.max(MAX_ZOOM_FACTOR, getFitRatio());
    }
  }
}
