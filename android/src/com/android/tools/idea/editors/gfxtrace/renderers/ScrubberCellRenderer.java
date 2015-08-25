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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.controllers.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.controllers.ScrubberController;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.ScrubberLabelData;
import com.android.tools.idea.editors.gfxtrace.renderers.styles.RoundedLineBorder;
import com.android.tools.idea.editors.gfxtrace.service.RenderSettings;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.image.ImageInfo;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.idea.editors.gfxtrace.service.path.ImageInfoPath;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final int TICK_MILLISECONDS = 66;
  @NotNull private static final Border DEFAULT_BORDER = new EmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE);
  @NotNull private static final Border SELECTED_BORDER = new RoundedLineBorder(UIUtil.getFocusedBoundsColor(), 5, false);
  @NotNull private static final Dimension DEFAULT_IMAGE_SIZE = new Dimension(MAX_WIDTH, MAX_HEIGHT);
  @NotNull private static final ScheduledExecutorService ourTickerScheduler = ConcurrencyUtil.newSingleScheduledThreadExecutor("ScrubberAnimation");
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);

  @NotNull private final ScrubberLabel myScrubberLabel;
  @NotNull private RenderSettings myRenderSettings;
  @NotNull private ImageIcon myBlankIcon;
  @NotNull final Dimension myLargestKnownIconDimension = new Dimension(MIN_WIDTH, MIN_HEIGHT);
  @Nullable private Runnable myTicker;
  @NotNull private final ScrubberController myController;

  public ScrubberCellRenderer(@NotNull ScrubberController controller) {
    myController = controller;
    myScrubberLabel = new ScrubberLabel();

    myRenderSettings = new RenderSettings();
    myRenderSettings.setMaxWidth(MAX_WIDTH);
    myRenderSettings.setMaxHeight(MAX_HEIGHT);
    myRenderSettings.setWireframeMode(WireframeMode.noWireframe());

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

  /**
   * This method returns a custom JBLabel to show the final frame render.
   */
  @Override
  public Component getListCellRendererComponent(@NotNull final JList jList,
                                                @NotNull Object data,
                                                final int index,
                                                final boolean isSelected,
                                                boolean cellHasFocus) {
    assert (data instanceof ScrubberLabelData);
    final ScrubberLabelData labelData = (ScrubberLabelData)data;
    final ServiceClient client = myController.getClient();
    final DevicePath devicePath = myController.getRenderDevice();
    myScrubberLabel.setUserData(labelData);
    if ((!labelData.isLoaded()) && (!labelData.isLoading()) && (devicePath != null)) {
      labelData.startLoading();
      if (myTicker == null) {
        myTicker = new Runnable() {
          @Override
          public void run() {
            EdtExecutor.INSTANCE.execute(new Runnable() {
              @Override
              public void run() {
                if (myController.isLoading()) {
                  jList.repaint();
                  ourTickerScheduler.schedule(myTicker, TICK_MILLISECONDS, TimeUnit.MILLISECONDS);
                }
                else {
                  myTicker = null;
                }
              }
            });
          }
        };
        myTicker.run();
      }
      ListenableFuture<ImageInfoPath> imagePathF = client.getFramebufferColor(devicePath, labelData.atomPath, myRenderSettings);
      Futures.addCallback(imagePathF, new LoadingCallback<ImageInfoPath>(LOG, labelData) {
        @Override
        public void onSuccess(@Nullable final ImageInfoPath imagePath) {
          Futures.addCallback(client.get(imagePath), new LoadingCallback<ImageInfo>(LOG, labelData) {
            @Override
            public void onSuccess(@Nullable final ImageInfo imageInfo) {
              Futures.addCallback(client.get(imageInfo.getData()), new LoadingCallback<byte[]>(LOG, labelData) {
                @Override
                public void onSuccess(@Nullable final byte[] data) {
                  final FetchedImage fetchedImage = new FetchedImage(imageInfo, data);
                  final ImageIcon image = fetchedImage.createImageIcon();
                  EdtExecutor.INSTANCE.execute(new Runnable() {
                    @Override
                    public void run() {
                      // Back in the UI thread here
                      updateDefaultImageIcon(image);
                      labelData.stopLoading();
                      labelData.setSelected(isSelected);
                      labelData.setIcon(image);
                      jList.repaint();
                    }
                  });
                }
              });
            }
          });
        }
      });
    }
    labelData.setSelected(isSelected);
    myScrubberLabel.setBorder(isSelected ? SELECTED_BORDER : DEFAULT_BORDER);
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
      myController.notifyDimensionChanged(getCellDimensions());
    }
  }
}
