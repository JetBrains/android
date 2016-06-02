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

import com.android.tools.idea.editors.gfxtrace.UiCallback;
import com.android.tools.idea.editors.gfxtrace.service.image.MultiLevelImage;
import com.android.tools.rpclib.futures.FutureController;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBSlidingPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import icons.AndroidIcons;
import icons.ImagesIcons;
import org.jetbrains.annotations.NotNull;
import sun.awt.image.IntegerComponentRaster;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class ImagePanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(ImagePanel.class);
  private static final int ZOOM_AMOUNT = 5;
  private static final int SCROLL_AMOUNT = 15;

  @NotNull private final ImageComponent myImage;
  @NotNull private final JBLabel myStatus = new JBLabel();

  public ImagePanel() {
    super(new BorderLayout());

    JBScrollPane scrollPane = new JBScrollPane();
    myImage = new ImageComponent(scrollPane);
    scrollPane.getVerticalScrollBar().setUnitIncrement(20);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
    scrollPane.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    add(scrollPane, BorderLayout.CENTER);
    add(new JPanel(new FlowLayout(FlowLayout.LEFT)) {{
      add(myImage.getLevelChooser());
      add(myStatus, BorderLayout.SOUTH);
    }} , BorderLayout.SOUTH);
    setFocusable(true);

    MouseAdapter mouseHandler = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        update(e.getX(), e.getY());
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myStatus.setText(" ");
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        update(e.getX(), e.getY());
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        update(e.getX(), e.getY());
      }

      private void update(int x, int y) {
        myImage.getPixel(x, y).formatTo(myStatus);
      }
    };
    scrollPane.getViewport().addMouseListener(mouseHandler);
    scrollPane.getViewport().addMouseMotionListener(mouseHandler);
  }

  public void addToolbarActions(DefaultActionGroup group, boolean enableVerticalFlip) {
    myImage.addToolbarActions(group, enableVerticalFlip);
  }

  public void clearImage() {
    myImage.clearImage();
  }

  public StatusText getEmptyText() {
    return myImage.getEmptyText();
  }

  public void setImageRequestController(FutureController imageRequestController) {
    myImage.setImageRequestController(imageRequestController);
  }

  public void setImage(MultiLevelImage image) {
    myImage.setImage(image);
  }

  private static final class ImageComponent extends JComponent {
    private static final double ZOOM_FIT = Double.POSITIVE_INFINITY;
    private static final double MAX_ZOOM_FACTOR = 8;
    private static final double MIN_ZOOM_WIDTH = 100.0;
    private static final int BORDER_SIZE = JBUI.scale(2);
    private static final Border BORDER = new LineBorder(JBColor.border(), BORDER_SIZE);
    private static final Paint CHECKER_PAINT = new CheckerboardPaint();
    private static final int CHANNEL_RED = 0, CHANNEL_GREEN = 1, CHANNEL_BLUE = 2, CHANNEL_ALPHA = 3;

    private final JViewport parent;
    private final StatusText emptyText;
    private final LevelChooser levelChooser = new LevelChooser();
    private FutureController imageRequestController = FutureController.NULL_CONTROLLER;
    private MultiLevelImage image;
    private BufferedImage level = MultiLevelImage.EMPTY_LEVEL;
    private BufferedImage displayed = null;
    private double zoom;
    private boolean drawCheckerBoard = true;
    private boolean flipped = false;
    private boolean[] channels = new boolean[] { true, true, true, true };

    public ImageComponent(JBScrollPane scrollPane) {
      scrollPane.setViewportView(this);
      this.parent = scrollPane.getViewport();
      this.emptyText = new StatusText() {
        @Override
        protected boolean isStatusVisible() {
          return image == MultiLevelImage.EMPTY_IMAGE;
        }
      };
      this.emptyText.attachTo(parent);
      this.zoom = ZOOM_FIT;
      this.levelChooser.addListener(new LevelChooser.Listener() {
        @Override
        public void levelChanged(int level) {
          loadLevel(level);
        }
      });

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

    public void setImageRequestController(FutureController imageRequestController) {
      this.imageRequestController = imageRequestController;
    }

    public void clearImage() {
      this.image = MultiLevelImage.EMPTY_IMAGE;
      this.level = MultiLevelImage.EMPTY_LEVEL;
      levelChooser.update(1);
      revalidate();
      repaint();
    }

    public void setImage(MultiLevelImage image) {
      if (this.image == MultiLevelImage.EMPTY_IMAGE) {
        // Ignore any zoom actions that might have happened before the first real image was shown.
        zoomToFit();
      }
      this.image = (image == null) ? MultiLevelImage.EMPTY_IMAGE : image;
      this.level = MultiLevelImage.EMPTY_LEVEL;
      this.displayed = null;
      if (!levelChooser.update(this.image.getLevelCount())) {
        loadLevel(levelChooser.getLevel());
      }
      revalidate();
      repaint();
    }

    public void addToolbarActions(DefaultActionGroup group, boolean enableVerticalFlip) {
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
      group.add(new Separator());
      group.add(new AnAction("Color Channels", "Select color channels to show", AndroidIcons.GfxTrace.ColorChannels) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          JPanel contents = new JPanel();
          contents.add(new ChannelCheckbox("Red", CHANNEL_RED));
          contents.add(new ChannelCheckbox("Green", CHANNEL_GREEN));
          contents.add(new ChannelCheckbox("Blue", CHANNEL_BLUE));
          contents.add(new ChannelCheckbox("Alpha", CHANNEL_ALPHA));

          JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(contents, contents)
            .setCancelOnClickOutside(true)
            .setResizable(false)
            .setMovable(false)
            .createPopup();

          Component source = e.getInputEvent().getComponent();
          popup.setMinimumSize(new Dimension(0, source.getHeight()));
          popup.show(new RelativePoint(source, new Point(source.getWidth(), 0)));
        }

        class ChannelCheckbox extends JBCheckBox {
          public ChannelCheckbox(String text, final int index) {
            super(text, channels[index]);
            addActionListener(e -> {
              setChannelEnabled(index, isSelected());
            });
          }
        }
      });
      group.add(new ToggleAction("Show Checkerboard", "Toggle the checkerboard background", ImagesIcons.ToggleTransparencyChessboard) {
        @Override
        public boolean isSelected(AnActionEvent e) {
          return drawCheckerBoard;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
          drawCheckerBoard = state;
          repaint();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          super.update(e);
          Presentation presentation = e.getPresentation();
          presentation.setEnabled(channels[CHANNEL_ALPHA]);
        }
      });
      if (enableVerticalFlip) {
        flipped = true;
        group.add(new ToggleAction("Flip Vertically", "Flip The image vertically", AndroidIcons.GfxTrace.FlipVertically) {
          @Override
          public boolean isSelected(AnActionEvent e) {
            return flipped;
          }

          @Override
          public void setSelected(AnActionEvent e, boolean state) {
            flipped = state;
            repaint();
          }
        });
      }
    }

    public LevelChooser getLevelChooser() {
      return levelChooser;
    }

    public Pixel getPixel(int x, int y) {
      if (this.image == MultiLevelImage.EMPTY_IMAGE || level == MultiLevelImage.EMPTY_LEVEL) {
        return Pixel.OUT_OF_BOUNDS;
      }

      double scale = (zoom == ZOOM_FIT) ? getFitRatio() : zoom;
      int w = (int)(level.getWidth(this) * scale), h = (int)(level.getHeight(this) * scale);

      Point pos = parent.getViewPosition();
      pos.translate(x - (getWidth() - w) / 2, y - (getHeight() - h) / 2);
      if (pos.x < 0 || pos.x >= w || pos.y < 0 || pos.y >= h) {
        return Pixel.OUT_OF_BOUNDS;
      }

      // Use OpenGL coordinates: origin at bottom left. While XY will be as shown (possibly flipped), UV stays constant to the origin
      // of the image used as a texture.
      int pixelX = (int)(pos.x / scale), pixelY = (int)(pos.y / scale);
      float u = (pos.x + 0.5f) / w, v = (pos.y + 0.5f) / h; // This is actually flipped v.
      return new Pixel(pixelX, level.getHeight() - pixelY - 1, u, flipped ? v : 1 - v , level.getRGB(pixelX, pixelY));
    }

    @Override
    public Dimension getPreferredSize() {
      return (zoom == ZOOM_FIT)
             ? new Dimension(parent.getWidth(), parent.getHeight())
             : new Dimension((int)(zoom * level.getWidth(this)) + 2 * BORDER_SIZE, (int)(zoom * level.getHeight(this)) + 2 * BORDER_SIZE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (image == MultiLevelImage.EMPTY_IMAGE || level == MultiLevelImage.EMPTY_LEVEL) {
        emptyText.paint(parent, g);
        return;
      }

      double scale = (zoom == ZOOM_FIT) ? getFitRatio() : zoom;
      int w = (int)(level.getWidth(this) * scale), h = (int)(level.getHeight(this) * scale);
      int x = (getWidth() - w) / 2, y = (getHeight() - h) / 2;

      if (drawCheckerBoard && channels[CHANNEL_ALPHA]) {
        ((Graphics2D)g).setPaint(CHECKER_PAINT);
        g.fillRect(x, y, w, h);
      }

      AffineTransform transform = ((Graphics2D)g).getTransform();
      if (flipped) {
        ((Graphics2D)g).transform(new AffineTransform(1, 0, 0, -1, 0, getHeight() - 1));
      }
      if (displayed == null) {
        Composite composite = null;
        int imageFormat = 0;
        if (channels[CHANNEL_RED] && channels[CHANNEL_GREEN] && channels[CHANNEL_BLUE]) {
          if (channels[CHANNEL_ALPHA]) {
            // If all channels are enabled, just display the original image.
            displayed = level;
          }
          else {
            // Use the platform provided alpha composite if alpha is disabled.
            composite = AlphaComposite.Src;
            imageFormat = BufferedImage.TYPE_3BYTE_BGR;
          }
        }
        else {
          // Use a custom color filtering composite otherwise.
          composite = (srcColorModel, dstColorModel, hints) -> new CompositeContext() {
            @Override
            public void dispose() {
            }

            @Override
            public void compose(Raster srcRaster, Raster dstIn, WritableRaster dstOut) {
              byte[] dst = ((DataBufferByte)dstOut.getDataBuffer()).getData();
              byte[] src = ((DataBufferByte)srcRaster.getDataBuffer()).getData();
              for (int i = 0; i < dst.length; i += 4) {
                dst[i + 0] = channels[CHANNEL_ALPHA] ? src[i + 0] : -1;
                dst[i + 1] = channels[CHANNEL_BLUE] ? src[i + 1] : 0;
                dst[i + 2] = channels[CHANNEL_GREEN] ? src[i + 2] : 0;
                dst[i + 3] = channels[CHANNEL_RED] ? src[i + 3] : 0;
              }
            }
          };
          imageFormat = BufferedImage.TYPE_4BYTE_ABGR;
        }

        if (composite != null) {
          //noinspection UndesirableClassUsage
          displayed = new BufferedImage(level.getWidth(this), level.getHeight(this), imageFormat);
          Graphics2D displayedG = displayed.createGraphics();
          displayedG.setComposite(composite);
          displayedG.drawImage(level, 0, 0, this);
          displayedG.dispose();
        }
      }
      g.drawImage(displayed, x, y, w, h, this);

      ((Graphics2D)g).setTransform(transform);
      BORDER.paintBorder(this, g, x - BORDER_SIZE, y - BORDER_SIZE, w + 2 * BORDER_SIZE, h + 2 * BORDER_SIZE);
    }

    private void loadLevel(int index) {
      index = Math.min(image.getLevelCount() - 1, index);
      Rpc.listen(image.getLevel(index), LOG, imageRequestController, new UiCallback<BufferedImage, BufferedImage>() {
        @Override
        protected BufferedImage onRpcThread(Rpc.Result<BufferedImage> result) throws RpcException, ExecutionException {
          return result.get();
        }

        @Override
        protected void onUiThread(BufferedImage result) {
          updateLevel(result);
        }
      });
    }

    protected void updateLevel(BufferedImage level) {
      if (this.level == MultiLevelImage.EMPTY_LEVEL) {
        // Ignore any zoom actions that might have happened before the first real image was shown.
        zoomToFit();
      }
      this.level = (level == null) ? MultiLevelImage.EMPTY_LEVEL : level;
      this.displayed = null;
      revalidate();
      repaint();
    }

    private void setChannelEnabled(int channel, boolean enabled) {
      if (channels[channel] != enabled) {
        channels[channel] = enabled;
        displayed = null;
        repaint();
      }
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
      return Math.min((double)(getWidth() - 2 * BORDER_SIZE) / level.getWidth(this),
                      (double)(getHeight() - 2 * BORDER_SIZE) / level.getHeight(this));
    }

    private double getMinZoom() {
      // The smallest zoom factor to see the whole image or that causes the larger dimension to be no less than MIN_ZOOM_WIDTH pixels.
      return Math.min(1, Math.min(getFitRatio(), Math.min(MIN_ZOOM_WIDTH / level.getWidth(this), MIN_ZOOM_WIDTH / level.getHeight(this))));
    }

    private double getMaxZoom() {
      return Math.max(MAX_ZOOM_FACTOR, getFitRatio());
    }

    /**
     * A {@link Paint} that will paint a checkerboard pattern. The current implementation aligns the pattern to the window (device)
     * coordinates, so the checkerboard remains stationary, even when the panel and the image is scrolled.
     */
    private static class CheckerboardPaint implements Paint, PaintContext {
      private static final int CHECKER_SIZE = JBUI.scale(15);
      private static final int TWO_CHECKER_SIZE = 2 * CHECKER_SIZE;
      private static final int LIGHT_COLOR = 0xFFFFFFFF;
      private static final int DARK_COLOR = 0xFFC0C0C0;

      // Cached raster and pixel values. They are re-allocated whenever a larger size is required. The raster's data is updated each time
      // a raster is requested in #getRaster(int, int, int, int).
      // A checkerboard can be broken down into rows of squares of alternating colors. There are two alternating rows: those that start with
      // a dark color and those that start with the light color. We cache the pixel values of a single raster scan line for both types of
      // rows, so they don't need to be computed every time.
      private WritableRaster cachedRaster;
      private int[] cachedEvenRow = new int[0];
      private int[] cachedOddRow = new int[0];

      @Override
      public PaintContext createContext(
          ColorModel cm, Rectangle deviceBounds, Rectangle2D userBounds, AffineTransform xform, RenderingHints hints) {
        return this;
      }

      @Override
      public void dispose() {
        cachedRaster = null;
      }

      @Override
      public ColorModel getColorModel() {
        return ColorModel.getRGBdefault();
      }

      @Override
      public Raster getRaster(int x, int y, int w, int h) {
        WritableRaster raster = cachedRaster;
        if (raster == null || w > raster.getWidth() || h > raster.getHeight()) {
          cachedRaster = raster = getColorModel().createCompatibleWritableRaster(w, h);
        }
        w = raster.getWidth();
        h = raster.getHeight();

        // Compute the x & y pixel offsets into a 2x2 checker tile. The checkerboard is aligned to (0, 0).
        int xOffset = x % TWO_CHECKER_SIZE, yOffset = y % TWO_CHECKER_SIZE;
        int[] evenRow = cachedEvenRow, oddRow = cachedOddRow;
        if (evenRow.length < xOffset + w || oddRow.length < xOffset + w) {
          // The scan line caches are sized in multiples of 2 checker squares.
          evenRow = new int[TWO_CHECKER_SIZE * ((xOffset + w + TWO_CHECKER_SIZE - 1) / TWO_CHECKER_SIZE)];
          oddRow = new int[evenRow.length];
          // Fill in the cached scan lines, two squares at a time.
          for (int i = 0; i < evenRow.length; i += TWO_CHECKER_SIZE) {
            // The even row is light, dark, light, dark, etc.
            Arrays.fill(evenRow, i, i + CHECKER_SIZE, LIGHT_COLOR);
            Arrays.fill(evenRow, i + CHECKER_SIZE, i + TWO_CHECKER_SIZE, DARK_COLOR);
            // The odd row is dark, light, dark, light, etc.
            Arrays.fill(oddRow, i, i + CHECKER_SIZE, DARK_COLOR);
            Arrays.fill(oddRow, i + CHECKER_SIZE, i + TWO_CHECKER_SIZE, LIGHT_COLOR);
          }
        }

        // The pixels array is a w * h row major storage backend of the raster data.
        int[] pixels = ((IntegerComponentRaster)raster).getDataStorage();
        int[][] rows = new int[][] { evenRow, oddRow };
        // The current checker row being copied. Initialized to align to the requested (x, y) coordinates.
        int curRowPointer = (yOffset < CHECKER_SIZE) ? 0 : 1;
        int[] curRow = rows[curRowPointer];
        // Copy the cached scan lines into the raster.
        for (int i = 0, done = 0, tileY = yOffset % CHECKER_SIZE; i < h; i++, tileY++, done += w) {
          if (tileY >= CHECKER_SIZE) {
            // We've completed a row of checker squares, switch to the other row type.
            tileY = 0;
            curRowPointer = (curRowPointer + 1) & 1;
            curRow = rows[curRowPointer];
          }
          // The scan lines are aligned to 2x2 checker tiles, so we copy starting at xOffset.
          System.arraycopy(curRow, xOffset, pixels, done, w);
        }
        return raster;
      }

      @Override
      public int getTransparency() {
        return Transparency.OPAQUE;
      }
    }

    private static class LevelChooser extends JPanel {
      private final JSlider mySlider = new JSlider();

      public LevelChooser() {
        mySlider.setPaintTicks(true);
        mySlider.setSnapToTicks(true);
        mySlider.setMajorTickSpacing(1);
        mySlider.setFocusable(false);
        setVisible(false);

        final JBLabel valueLabel = new JBLabel("0");
        mySlider.addChangeListener(new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            valueLabel.setText(String.valueOf(mySlider.getValue()));
          }
        });

        add(new JBLabel("Level:"));
        add(mySlider);
        add(valueLabel);
      }

      public boolean update(int numLevels) {
        setVisible(numLevels > 1);

        int value = mySlider.getValue();
        if (value >= numLevels) {
          mySlider.setValue(numLevels - 1);
        }
        mySlider.setMinimum(0);
        mySlider.setMaximum(numLevels - 1);
        return value >= numLevels;
      }

      public int getLevel() {
        return mySlider.getValue();
      }

      public void addListener(final Listener listener) {
        mySlider.addChangeListener(new ChangeListener() {
          private int value = mySlider.getValue();

          @Override
          public void stateChanged(ChangeEvent e) {
            int newValue = mySlider.getValue();
            if (newValue != value) {
              value = newValue;
              listener.levelChanged(newValue);
            }
          }
        });
      }

      public interface Listener {
        void levelChanged(int level);
      }
    }
  }

  private static class Pixel {
    public static final Pixel OUT_OF_BOUNDS = new Pixel(-1, -1, -1, -1, 0) {
      @Override
      public void formatTo(JBLabel label) {
        label.setText(" ");
      }
    };

    public final int x, y;
    public final float u, v;
    public final int rgba;

    public Pixel(int x, int y, float u, float v, int rgba) {
      this.x = x;
      this.y = y;
      this.u = u;
      this.v = v;
      this.rgba = rgba;
    }

    public void formatTo(JBLabel label) {
      label.setText(String.format("X: %d, Y: %d, U: %05f, V: %05f, ARGB: %08x", x, y, u, v, rgba));
    }
  }
}
