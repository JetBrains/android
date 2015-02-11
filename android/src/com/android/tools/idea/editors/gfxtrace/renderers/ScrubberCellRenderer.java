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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.android.tools.idea.editors.gfxtrace.controllers.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.controllers.ImageFetcher;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.ScrubberLabelData;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.RoundedLineBorder;
import com.android.tools.idea.editors.gfxtrace.rpc.RenderSettings;
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

  @NotNull private final ScrubberLabel myScrubberLabel;
  @NotNull private RenderSettings myRenderSettings;
  @NotNull private Set<Integer> myOutstandingIconFetches;
  @NotNull private HashMap<Integer, ImageIcon> myCachedImages;
  @NotNull private ImageIcon myBlankIcon;
  @NotNull private AtomicBoolean shouldStop = new AtomicBoolean(false);
  @NotNull private List<Integer> myPostRenderCleanupCacheHits = new ArrayList<Integer>(20);
  private ScheduledFuture<?> myTicker;
  private Dimension myLargestKnownIconDimension = new Dimension(MIN_WIDTH, MIN_HEIGHT);
  private int myRepaintsNeeded;

  @NotNull private List<DimensionChangeListener> myDimensionChangeListeners = new ArrayList<DimensionChangeListener>(1);
  private ImageFetcher myImageFetcher;

  public ScrubberCellRenderer() {
    myScrubberLabel = new ScrubberLabel();

    myOutstandingIconFetches = new HashSet<Integer>();
    myCachedImages = new HashMap<Integer, ImageIcon>();

    myRenderSettings = new RenderSettings();
    myRenderSettings.setMaxWidth(MAX_WIDTH);
    myRenderSettings.setMaxHeight(MAX_HEIGHT);
    myRenderSettings.setWireframe(false);

    myBlankIcon = new ImageIcon(createBlankImage(DEFAULT_IMAGE_SIZE));
  }

  private static Image createBlankImage(@NotNull Dimension dimension) {
    //noinspection UndesirableClassUsage
    BufferedImage blankImage = new BufferedImage(dimension.width, dimension.height, BufferedImage.TYPE_BYTE_BINARY);
    Graphics2D g = blankImage.createGraphics();
    g.setPaint(UIUtil.getListForeground());
    g.fillRect(0, 0, dimension.width, dimension.height);
    g.dispose();
    return blankImage;
  }

  public void setup(@NotNull ImageFetcher imageFetcher) {
    myImageFetcher = imageFetcher;
  }

  public void addDimensionChangeListener(@NotNull DimensionChangeListener listener) {
    myDimensionChangeListeners.add(listener);
  }

  /**
   * This method returns a custom JBLabel to show the final frame render.
   * <p/>
   * The icons that are used in the scrubber view are generated on a remote server. This fact necessitates a few requirements for displaying
   * icons in the scrubber view:
   * 1) When the icon is being generated, the UI needs to remain responsive.
   * 2) While the icon is being generated, there needs to exist UI indicators to notify the user that the the icon is being generated.
   * 3) The icon should not be regenerated if it has already been generated, within the memory constraints of Studio. This therefore
   * necessitates some sort of caching mechanism.
   * 4) Caches need to get periodically evicted since each icon is upwards of ~150kB in size. A capture with 600 frames requires ~88MB of
   * memory to hold all the thumbnails in memory, and is completely variable depending on the length of capture.
   * <p/>
   * Therefore, this method (directly or indirectly) satisfies the above requirements by:
   * 1) Farming off the icon generation to a separate thread.
   * 2) Draw the loading indicator via a custom Swing component (ScrubberLabel).
   * 3) Implementing a simple caching mechanism based on a sliding window view of the film strip.
   * 4) Periodic cache eviction based on last-rendered time, and the current position in the sliding window. As in, the user is always
   * seeing a contiguous segment of frames in the UI (a "window"). For most use cases, the user will scroll left or right, which makes cache
   * locality based on the current viewport position in the view.
   * <p/>
   * The general flow of this method is as follows:
   * 1) Look into the cache to see if an icon exists for the given parameters.
   * 2a) If so, populate the singleton ScrubberLabel component with the cached icon and return it. Done.
   * 2b) If not, queue a request to the server to generate the desired icon on a separate thread. When the server returns, cache the state
   * and the icon, and request a repaint (which will cause the new icon displayed).
   * 3) (While generating the icon) Start a timer to periodically repaint the loading icon that will be a placeholder for the desired icon.
   * 4) Populate the singleton ScrubberLabel component with the placeholder icon and return it. Done.
   */
  @Override
  public Component getListCellRendererComponent(@NotNull final JList jList,
                                                @NotNull Object data,
                                                final int index,
                                                final boolean isSelected,
                                                boolean cellHasFocus) {
    assert (data instanceof ScrubberLabelData);
    final ScrubberLabelData labelData = (ScrubberLabelData)data;
    myScrubberLabel.setUserData(labelData);

    ImageIcon result = myCachedImages.get(index);
    if (result == null) {
      if (myOutstandingIconFetches.contains(index)) {
        myRepaintsNeeded++;
      }
      else {
        myOutstandingIconFetches.add(index);
        labelData.setLoading(true);
        final AtomicBoolean shouldStopReference = shouldStop;
        final ImageFetcher closedImageFetcher = myImageFetcher;

        // The renderer should run in parallel since it doesn't affect the state of the editor.
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            ImageIcon imageIcon = null;
            try {
              ImageFetcher.ImageFetchHandle handle = closedImageFetcher.queueColorImage(labelData.getAtomId(), myRenderSettings);

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
                  myOutstandingIconFetches.remove(index);
                  if (shouldStopReference.get() || finalImageIcon == null) {
                    return;
                  }

                  updateDefaultImageIcon(finalImageIcon);

                  labelData.setLoading(false);
                  labelData.setSelected(isSelected);
                  labelData.setIcon(finalImageIcon);
                  myCachedImages.put(index, finalImageIcon);
                  jList.repaint();
                }
              });
            }
          }
        });
      }
    }

    labelData.setSelected(isSelected);
    myScrubberLabel.setBorder(isSelected ? SELECTED_BORDER : DEFAULT_BORDER);

    queueInvalidateCache();
    myPostRenderCleanupCacheHits.add(index);

    // If necessary, schedule a repeating repaint so that the loading icon animates.
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

    return myScrubberLabel;
  }

  @NotNull
  public Dimension getCellDimensions() {
    if (myLargestKnownIconDimension.getWidth() > MIN_WIDTH && myLargestKnownIconDimension.getHeight() > MIN_HEIGHT) {
      return new Dimension(myLargestKnownIconDimension.width + 2 * BORDER_SIZE, myLargestKnownIconDimension.height + 2 * BORDER_SIZE);
    }
    return new Dimension((int)myRenderSettings.getMaxWidth() + 2 * BORDER_SIZE, (int)myRenderSettings.getMaxHeight() + 2 * BORDER_SIZE);
  }

  @NotNull
  public ImageIcon getDefaultIcon() {
    return myBlankIcon;
  }

  public void clearState() {
    clearCache();
    myImageFetcher = null;
  }

  public void clearCache() {
    shouldStop.set(true);
    shouldStop = new AtomicBoolean(false);
    myCachedImages.clear();
  }

  /**
   * This methods updates the default blank image icon's size.
   * <p/>
   * Since there currently is no way to know how large the largest icon will be a priori, this method checks and changes the default icon
   * to the union of the largest icon dimensions encountered so far.
   */
  private void updateDefaultImageIcon(@NotNull ImageIcon newIcon) {
    if (newIcon.getIconHeight() > myLargestKnownIconDimension.getHeight() ||
        newIcon.getIconWidth() > myLargestKnownIconDimension.getWidth()) {
      myLargestKnownIconDimension.setSize(Math.max(newIcon.getIconWidth(), myLargestKnownIconDimension.width),
                                          Math.max(newIcon.getIconHeight(), myLargestKnownIconDimension.height));
      myBlankIcon.setImage(createBlankImage(myLargestKnownIconDimension));
      for (DimensionChangeListener listener : myDimensionChangeListeners) {
        listener.notifyDimensionChanged(getCellDimensions());
      }
    }
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

  public interface DimensionChangeListener {
    void notifyDimensionChanged(@NotNull Dimension newDimension);
  }
}
