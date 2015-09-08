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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.actions.FramebufferTypeAction;
import com.android.tools.idea.editors.gfxtrace.actions.FramebufferWireframeAction;
import com.android.tools.idea.editors.gfxtrace.service.RenderSettings;
import com.android.tools.idea.editors.gfxtrace.service.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public class FrameBufferController extends Controller {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new FrameBufferController(editor).myPanel;
  }

  private static final int MAX_SIZE = 0xffff;

  public enum BufferType {
    Color,
    Depth
  }

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();
  @NotNull private final PathStore<AtomPath> myAtomPath = new PathStore<AtomPath>();
  @NotNull private final JBScrollPane myScrollPane = new JBScrollPane();
  @NotNull private final ImagePanel myImagePanel = new ImagePanel();
  @NotNull private final RenderSettings mySettings = new RenderSettings();
  @NotNull private JBLoadingPanel myLoading;
  @NotNull private BufferType myBufferType = BufferType.Color;

  private final AtomicInteger imageLoadCount = new AtomicInteger();
  private ListenableFuture<?> request = Futures.immediateFuture(0);

  public synchronized int newImageRequest(ListenableFuture<?> request) {
    this.request.cancel(true);
    this.request = request;
    return imageLoadCount.incrementAndGet();
  }

  public boolean isCurrentImageRequest(int request) {
    return imageLoadCount.get() == request;
  }

  private FrameBufferController(@NotNull GfxTraceEditor editor) {
    super(editor);

    myScrollPane.getVerticalScrollBar().setUnitIncrement(20);
    myScrollPane.getHorizontalScrollBar().setUnitIncrement(20);
    myScrollPane.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    myScrollPane.setViewport(myImagePanel.getViewport());

    myLoading = new JBLoadingPanel(new BorderLayout(), myEditor.getProject());
    myLoading.add(myScrollPane, BorderLayout.CENTER);

    mySettings.setMaxHeight(MAX_SIZE);
    mySettings.setMaxWidth(MAX_SIZE);
    mySettings.setWireframeMode(WireframeMode.noWireframe());
    myPanel.add(myLoading, BorderLayout.CENTER);

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), false);
    myPanel.add(toolbar.getComponent(), BorderLayout.WEST);
  }

  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new FramebufferTypeAction(this, BufferType.Color, "Color", "Display the color framebuffer", AllIcons.Gutter.Colors));
    group.add(new FramebufferTypeAction(this, BufferType.Depth, "Depth", "Display the depth framebuffer", AllIcons.Gutter.OverridenMethod));
    group.add(new Separator());
    group.add(new FramebufferWireframeAction(this, WireframeMode.noWireframe(), "None", "Display the frambuffer without wireframing",
                                             AllIcons.Ide.Macro.Recording_1));
    group.add(new FramebufferWireframeAction(this, WireframeMode.wireframeOverlay(), "Overlay", "Wireframe the last draw call only",
                                             AllIcons.Gutter.Unique));
    group.add(new FramebufferWireframeAction(this, WireframeMode.allWireframe(), "All", "Draw the framebuffer with full wireframing",
                                             AllIcons.Graph.Grid));
    return group;
  }

  @NotNull
  public BufferType getBufferType() {
    return myBufferType;
  }

  public void setBufferType(@NotNull BufferType bufferType) {
    if (!myBufferType.equals(bufferType)) {
      myBufferType = bufferType;
      updateBuffer();
    }
  }

  @NotNull
  public WireframeMode getWireframeMode() {
    return mySettings.getWireframeMode();
  }

  public void setWireframeMode(@NotNull WireframeMode mode) {
    if (!mySettings.getWireframeMode().equals(mode)) {
      mySettings.setWireframeMode(mode);
      updateBuffer();
    }
  }

  @Override
  public void notifyPath(Path path) {
    boolean updateTabs = false;
    if (path instanceof DevicePath) {
      updateTabs |= myRenderDevice.update((DevicePath)path);
    }
    if (path instanceof AtomPath) {
      updateTabs |= myAtomPath.update((AtomPath)path);
    }
    if (updateTabs && myRenderDevice.getPath() != null && myAtomPath.getPath() != null) {
      // TODO: maybe do the selected tab first, but it's probably not much of a win
      updateBuffer();
    }
  }

  public void updateBuffer() {
    final ListenableFuture<FetchedImage> imageFuture = loadImage();
    final int imageRequest = newImageRequest(imageFuture);

    myLoading.startLoading();

    Futures.addCallback(imageFuture, new FutureCallback<FetchedImage>() {
      @Override
      public void onSuccess(@Nullable FetchedImage result) {
        updateBuffer(imageRequest, result);
      }

      @Override
      public void onFailure(Throwable t) {
        if (!(t instanceof CancellationException)) {
          LOG.error(t);
        }

        EdtExecutor.INSTANCE.execute(new Runnable() {
          @Override
          public void run() {
            if (isCurrentImageRequest(imageRequest)) {
              myLoading.stopLoading();
            }
          }
        });
      }
    });
  }

  private ListenableFuture<FetchedImage> loadImage() {
    return Futures.transform(getImageInfoPath(), new AsyncFunction<ImageInfoPath, FetchedImage>() {
      @Override
      public ListenableFuture<FetchedImage> apply(ImageInfoPath imageInfoPath) throws Exception {
        return FetchedImage.load(myEditor.getClient(), imageInfoPath);
      }
    });
  }

  private ListenableFuture<ImageInfoPath> getImageInfoPath() {
    switch (myBufferType) {
      case Color:
        return myEditor.getClient().getFramebufferColor(myRenderDevice.getPath(), myAtomPath.getPath(), mySettings);
      case Depth:
        return myEditor.getClient().getFramebufferDepth(myRenderDevice.getPath(), myAtomPath.getPath());
      default:
        return null;
    }
  }

  private void updateBuffer(final int imageRequest, FetchedImage fetchedImage) {
    final Image image = fetchedImage.icon.getImage();
    EdtExecutor.INSTANCE.execute(new Runnable() {
      @Override
      public void run() {
        // Back in the UI thread here
        if (isCurrentImageRequest(imageRequest)) {
          myLoading.stopLoading();
          myImagePanel.setImage(image);
        }
      }
    });
  }

  private static class ImagePanel extends JPanel {
    private static final double ZOOM_FIT = Double.POSITIVE_INFINITY;
    private static final double MAX_ZOOM = 8;
    private static final double MIN_ZOOM_WIDTH = 100.0;
    private static final int ZOOM_AMOUNT = 5;
    private static final int SCROLL_AMOUNT = 15;
    private static final BufferedImage EMPTY_IMAGE = UIUtil.createImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);

    private final JBViewport parent = new JBViewport();
    private Image image = EMPTY_IMAGE;
    private double zoom;

    private ImagePanel() {
      this.zoom = ZOOM_FIT;
      this.parent.setView(this);

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

      // Add the mouse listeners to the parent, so the coordinates stay consistent.
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
              zoom(-ZOOM_AMOUNT, new Point(parent.getWidth() / 2, parent.getHeight() / 2));
              break;
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_SUBTRACT:
              zoom(ZOOM_AMOUNT, new Point(parent.getWidth() / 2, parent.getHeight() / 2));
              break;
            case KeyEvent.VK_EQUALS:
              if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                zoom(-ZOOM_AMOUNT, new Point(parent.getWidth() / 2, parent.getHeight() / 2));
              }
              else {
                zoomToFit();
              }
              break;
          }
        }
      });
      setFocusable(true);
    }

    public JBViewport getViewport() {
      return parent;
    }

    public void setImage(Image image) {
      if (this.image == EMPTY_IMAGE) {
        // Ignore any zoom actions that might have happened before the first real image was shown.
        zoomToFit();
      }
      this.image = image;
      revalidate();
      repaint();
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

    private void zoom(int amount, Point cursor) {
      Dimension oldSize = getPreferredSize();
      oldSize.setSize(Math.max(parent.getWidth(), oldSize.width), Math.max(parent.getHeight(), oldSize.height));

      if (zoom == ZOOM_FIT) {
        zoom = getFitRatio();
      }
      int delta = Math.min(Math.max(amount, -5), 5);
      zoom = Math.min(MAX_ZOOM, Math.max(getMinZoom(), zoom * (1 - 0.05 * delta)));
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

    private double getFitRatio() {
      return Math.min((double)getWidth() / image.getWidth(this), (double)getHeight() / image.getHeight(this));
    }

    private double getMinZoom() {
      // The smallest zoom factor to see the whole image or that causes the larger dimension to be no less than MIN_ZOOM_WIDTH pixels.
      return Math.min(1, Math.min(getFitRatio(), Math.min(MIN_ZOOM_WIDTH / image.getWidth(this), MIN_ZOOM_WIDTH / image.getHeight(this))));
    }
  }
}
