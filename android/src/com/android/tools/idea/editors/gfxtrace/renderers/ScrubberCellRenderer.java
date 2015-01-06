/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.android.tools.idea.editors.gfxtrace.controllers.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.controllers.ImageFetcher;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.ScrubberFrameData;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.RoundedLineBorder;
import com.android.tools.rpclib.rpc.RenderSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScrubberCellRenderer implements ListCellRenderer {
  private static final int MAX_CACHE_SIZE = 45;
  private static final int BORDER_SIZE = 5;
  private static final int MIN_WIDTH = 64;
  private static final int MIN_HEIGHT = 64;
  private static final int MAX_WIDTH = 192;
  private static final int MAX_HEIGHT = 192;
  @NotNull private static final Border DEFAULT_BORDER = new EmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE);
  @NotNull private static final Border SELECTED_BORDER = new RoundedLineBorder(UIUtil.getFocusedBoundsColor(), 5, false);
  @NotNull private static final Dimension DEFAULT_IMAGE_SIZE = new Dimension(MAX_WIDTH, MAX_HEIGHT);
  @NotNull private static final ScheduledExecutorService ourScheduler =
    ConcurrencyUtil.newSingleScheduledThreadExecutor("ScrubberAnimation");
  @NotNull private RenderSettings myRenderSettings;
  @NotNull private Set<Integer> myOutstandingRenders;
  @NotNull private HashMap<Integer, ImageIcon> myCachedImages;
  @NotNull private ImageIcon myBlankIcon;
  @NotNull private AtomicBoolean shouldStop = new AtomicBoolean(false);
  @NotNull private List<Integer> myPostRenderCleanupCacheHits = new ArrayList<Integer>(20);
  private ScheduledFuture<?> myTicker;
  private Dimension myLargestKnownIcon = new Dimension(MIN_WIDTH, MIN_HEIGHT);
  private int myRepaintsNeeded;

  @NotNull private List<DimensionChangeListener> myDimensionChangeListeners = new ArrayList<DimensionChangeListener>(1);
  private ImageFetcher myImageFetcher;

  public ScrubberCellRenderer() {
    myOutstandingRenders = new HashSet<Integer>();
    myCachedImages = new HashMap<Integer, ImageIcon>();

    myRenderSettings = new RenderSettings();
    myRenderSettings.setMaxWidth(MAX_WIDTH);
    myRenderSettings.setMaxHeight(MAX_HEIGHT);
    myRenderSettings.setWireframe(false);

    myBlankIcon = new ImageIcon(createBlankImage(DEFAULT_IMAGE_SIZE));
  }

  public void setup(@NotNull ImageFetcher imageFetcher) {
    myImageFetcher = imageFetcher;
  }

  public void addDimensionChangeListener(@NotNull DimensionChangeListener listener) {
    myDimensionChangeListeners.add(listener);
  }

  @Override
  public Component getListCellRendererComponent(final JList jList,
                                                Object o,
                                                final int index,
                                                final boolean isSelected,
                                                boolean cellHasFocus) {
    assert (o instanceof ScrubberLabel);
    final ScrubberLabel existingLabel = (ScrubberLabel)o;

    ImageIcon result = myCachedImages.get(index);
    if (result == null) {
      if (myOutstandingRenders.contains(index)) {
        myRepaintsNeeded++;
      }
      else {
        myOutstandingRenders.add(index);
        existingLabel.setLoading(true);
        final AtomicBoolean shouldStopReference = shouldStop;
        final ImageFetcher closedImageFetcher = myImageFetcher;

        // The renderer should run in parallel since it doesn't affect the state of the editor.
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            ImageIcon imageIcon = null;
            try {
              ImageFetcher.ImageFetchHandle handle =
                closedImageFetcher.queueColorImage(existingLabel.getUserData().getAtomId(), myRenderSettings);

              if (handle != null) {
                FetchedImage fetchedImage = closedImageFetcher.resolveImage(handle);

                if (fetchedImage != null) {
                  imageIcon = fetchedImage.createImageIcon();
                }
              }
            }
            finally {
              final ImageIcon finalImageIcon = imageIcon;
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  myOutstandingRenders.remove(index);
                  if (shouldStopReference.get() || finalImageIcon == null) {
                    return;
                  }

                  if (finalImageIcon.getIconHeight() > myLargestKnownIcon.getHeight() ||
                      finalImageIcon.getIconWidth() > myLargestKnownIcon.getWidth()) {
                    myLargestKnownIcon.setSize(Math.max(finalImageIcon.getIconWidth(), myLargestKnownIcon.width),
                                               Math.max(finalImageIcon.getIconHeight(), myLargestKnownIcon.height));
                    myBlankIcon.setImage(createBlankImage(myLargestKnownIcon));
                    for (DimensionChangeListener listener : myDimensionChangeListeners) {
                      listener.notifyDimensionChanged(getCellDimensions());
                    }
                  }

                  existingLabel.setIcon(finalImageIcon);
                  existingLabel.setLoading(false);
                  existingLabel.setBorder(existingLabel.getBorder());
                  existingLabel.setSelected(isSelected);
                  myCachedImages.put(index, finalImageIcon);
                  jList.repaint();
                }
              });
            }
          }
        });
      }
    }

    existingLabel.setBorder(isSelected ? SELECTED_BORDER : DEFAULT_BORDER);
    existingLabel.setSelected(isSelected);

    queueInvalidateCache();
    myPostRenderCleanupCacheHits.add(index);

    if (myRepaintsNeeded > 0 && myTicker == null) {
      myTicker = ourScheduler.scheduleAtFixedRate(new Runnable() {
        @Override
        public void run() {
          // Need to run this in the EDT, since the scheduled/timer doesn't run the Runnable in the EDT.
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myRepaintsNeeded = 0;
              jList.repaint();
            }
          });
        }
      }, 0, 66, TimeUnit.MILLISECONDS);
    }
    else if (myRepaintsNeeded == 0 && myTicker != null) {
      myTicker.cancel(false);
      myTicker = null;
    }

    return existingLabel;
  }

  @NotNull
  public JComponent getBlankLabel(@NotNull ScrubberFrameData data, boolean isSelected) {
    ScrubberLabel blankLabel = new ScrubberLabel(data);
    blankLabel.setIcon(myBlankIcon);
    blankLabel.setLoading(true);
    blankLabel.setVerticalAlignment(SwingConstants.TOP);
    blankLabel.setHorizontalTextPosition(SwingConstants.CENTER);
    blankLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
    blankLabel.setBorder(DEFAULT_BORDER);
    blankLabel.setSelected(isSelected);

    return blankLabel;
  }

  @NotNull
  public Dimension getCellDimensions() {
    if (myLargestKnownIcon.getWidth() > MIN_WIDTH && myLargestKnownIcon.getHeight() > MIN_HEIGHT) {
      return new Dimension(myLargestKnownIcon.width + 2 * BORDER_SIZE, myLargestKnownIcon.height + 2 * BORDER_SIZE);
    }
    return new Dimension((int)myRenderSettings.getMaxWidth() + 2 * BORDER_SIZE, (int)myRenderSettings.getMaxHeight() + 2 * BORDER_SIZE);
  }

  public void clear() {
    clearCache();
    myImageFetcher = null;
  }

  public void clearCache() {
    shouldStop.set(true);
    shouldStop = new AtomicBoolean(false);
    myCachedImages.clear();
  }

  /**
   * This method queues a task to run on the EDT to invalid the caches.
   * <p/>
   * This object needs to invalidate the cache to prevent the cache from growing indefinitely. However, since Swing does not have a simple
   * way to recognize "end of draw", this method inserts a callback to the end of the EDT invokeLater queue which gets processed after all
   * UI update draw calls have completed. This ensures that cache cleanup happens after all rendering calls to this renderer object have
   * completed for this frame before cleanup occurs.
   */
  private void queueInvalidateCache() {
    if (myPostRenderCleanupCacheHits.size() == 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (myPostRenderCleanupCacheHits.size() > 2 && myCachedImages.size() > MAX_CACHE_SIZE) {
            Integer[] hitIndices = myPostRenderCleanupCacheHits.toArray(new Integer[myPostRenderCleanupCacheHits.size()]);
            Arrays.sort(hitIndices);
            int midHitIndex = hitIndices[(hitIndices.length + 1) / 2 - 1]; // If length is even, use the lesser value.

            Integer[] cachedKeys = myCachedImages.keySet().toArray(new Integer[myCachedImages.size()]);
            Arrays.sort(cachedKeys);
            int midHitIndexKeyPosition = Arrays.binarySearch(cachedKeys, midHitIndex);
            if (midHitIndexKeyPosition < 0) {
              midHitIndexKeyPosition = -(midHitIndexKeyPosition + 1);
            }

            for (int i = 0; i < midHitIndexKeyPosition - MAX_CACHE_SIZE / 2; ++i) {
              myCachedImages.remove(cachedKeys[i]);
            }
            for (int i = midHitIndexKeyPosition - MAX_CACHE_SIZE / 2 + MAX_CACHE_SIZE; i < cachedKeys.length; ++i) {
              myCachedImages.remove(cachedKeys[i]);
            }
          }
          myPostRenderCleanupCacheHits.clear();
        }
      });
    }
  }

  private Image createBlankImage(@NotNull Dimension dimension) {
    //noinspection UndesirableClassUsage
    BufferedImage blankImage = new BufferedImage(dimension.width, dimension.height, BufferedImage.TYPE_BYTE_BINARY);
    Graphics2D g = blankImage.createGraphics();
    g.setPaint(UIUtil.getListForeground());
    g.fillRect(0, 0, dimension.width, dimension.height);
    g.dispose();
    return blankImage;
  }

  public interface DimensionChangeListener {
    void notifyDimensionChanged(@NotNull Dimension newDimension);
  }
}
