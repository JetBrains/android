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
package com.google.gct.studio.samples;

import com.appspot.gsamplesindex.samplesindex.model.Sample;
import com.appspot.gsamplesindex.samplesindex.model.Screenshot;
import com.google.api.client.util.Lists;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.intellij.util.containers.ComparatorUtil.max;

/**
 * Panel that draws a list of images vertically stacked, handles resizing by scaling the images
 */
public class SamplePreviewPanel extends JPanel implements ComponentListener {
  private List<BufferedImage> myImages = Lists.newArrayList();
  private List<Image> myScaledImages = Lists.newArrayList();
  //private final Map<String, BufferedImage> myImageCache = new SoftValueHashMap<String, BufferedImage>();
  private final LoadingCache<String, BufferedImage> myImageCache;
  private int myHeight = 0;
  private boolean myHasPreview = false;
  private static int PADDING = 5;
  private SwingWorker myBackgroundTask = null;

  public SamplePreviewPanel() {
    addComponentListener(this);
    myImageCache = CacheBuilder.newBuilder().weakValues().build(new CacheLoader<String, BufferedImage>() {
      @Override
      public BufferedImage load(@NotNull String imageUrl) throws Exception {
        return ImageIO.read(new URL(imageUrl));
      }
    });
  }

  /**
   * Set the sample, so the Panel can extract the screenshots
   */
  public void setSample(@Nullable Sample sample) {
    myImages.clear();
    myScaledImages.clear();
    myHeight = 0;
    if (myBackgroundTask != null) {
      myBackgroundTask.cancel(true);
      myBackgroundTask = null;
    }
    if (sample == null || sample.getScreenshots() == null || sample.getScreenshots().isEmpty()) {
      myHasPreview = false;
      revalidate();
      repaint();
    }
    else {
      myHasPreview = true;
      revalidate();
      repaint();
      loadImagesInBackground(sample);
    }
  }

  /**
   * Download sample images in the background, cache downloaded images
   * Keep modification of the myImages list on the UI thread
   */
  private void loadImagesInBackground(@NotNull final Sample sample) {
    myBackgroundTask = new SwingWorker<List<BufferedImage>, Void>() {
      @Override
      @Nullable
      protected List<BufferedImage> doInBackground() throws Exception {
        final List<BufferedImage> images = Lists.newArrayList();
        for (Screenshot screenshot : sample.getScreenshots()) {
          if (isCancelled()) {
            return null;
          }
          try {
            String url = screenshot.getLink();
            BufferedImage image = myImageCache.get(url);
            images.add(image);
          }
          catch (Exception e) {
            // don't add screenshot, cache or cache-loader threw exception
          }
        }
        return images;
      }

      @Override
      protected void done() {
        if (isCancelled()) {
          return;
        }
        try {
          List<BufferedImage> result = get();
          if (result != null) {
            myImages = result;
            if (myImages.isEmpty()) {
              myHasPreview = false;
            }
            scaleImages();
            revalidate();
            repaint();
          }
        }
        catch (InterruptedException e) {
          // ignore
        }
        catch (ExecutionException e) {
          // ignore
        }
      }
    };
    myBackgroundTask.execute();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    int yPos = 0;
    if (myScaledImages.isEmpty()) {
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      String text = SamplesBrowserBundle.message("sample.browser.no.preview");
      if (myHasPreview) {
        text = "Loading preview...";
      }
      Rectangle2D r = g.getFontMetrics().getStringBounds(text, g);
      g.drawString(text, 1, (int) r.getHeight());
    }
    for (Image img : myScaledImages) {
      g.drawImage(img, 0, yPos, null);
      yPos += img.getHeight(null) + PADDING;
    }
  }

  /**
   * Override preferred size because we are overriding painting, this is required to get scrolling to work correctly
   */
  @Override
  public Dimension getPreferredSize() {
    return new Dimension(this.getWidth(), myHeight);
  }

  /**
   * scales images to fit the width of the panel
   */
  private void scaleImages() {
    myHeight = 0;
    myScaledImages.clear();
    int panelWidth = max(1, this.getWidth());
    for (BufferedImage img : myImages) {
      int yScaled = max(1, img.getHeight() * panelWidth / img.getWidth());
      myScaledImages.add(img.getScaledInstance(panelWidth, yScaled, Image.SCALE_SMOOTH));
      myHeight += yScaled + PADDING;
    }
  }

  @Override
  public void componentResized(ComponentEvent e) {
    scaleImages();
  }

  @Override
  public void componentMoved(ComponentEvent e) {
    // ignored
  }

  @Override
  public void componentShown(ComponentEvent e) {
    // ignored
  }

  @Override
  public void componentHidden(ComponentEvent e) {
    // ignored
  }
}
