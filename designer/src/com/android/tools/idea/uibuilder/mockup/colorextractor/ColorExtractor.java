/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.colorextractor;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;

/**
 * Asynchronously extract the meaningful myColor of an image.
 *
 * The subclass should implements {@link #run(ColorExtractorCallback)} that will do
 * the work in a separate thread. The results will be passed with as a {@link Collection} of {@link ExtractedColor}
 * in the {@link ColorExtractorCallback}
 *
 */
public abstract class ColorExtractor {

  protected ColorExtractorCallback myCallback;

  @Nullable protected BufferedImage myImage;

  /**
   * Create a new ColorExtractor that will extract the image in mockup if there is one.
   * If the mockup has cropping area defined, the color will be extracted only from this area.
   *
   * @param mockup The mockup with the image to extract the color from.
   */
  protected ColorExtractor(Mockup mockup) {
    Rectangle cropping = mockup.getCropping();
    BufferedImage image = mockup.getImage();
    if (image != null) {
      init(image.getSubimage(cropping.x, cropping.y, cropping.width, cropping.height));
    }
  }

  protected ColorExtractor(@NotNull BufferedImage image) {
    init(image);
  }

  private void init(BufferedImage image) {
    myImage = image;
  }

  /**
   * Callback to get the result of {@link ColorExtractor}
   *
   * @see ColorExtractor#run(ColorExtractorCallback)
   */
  public interface ColorExtractorCallback {

    /**
     * Publish the result of the color extractor in the Swing UI thread.
     *
     * @param rgbColors Sorted collection of the color by occurrences in the image.
     *                  Empty collection if the image was null
     */
    void result(Collection<ExtractedColor> rgbColors);

    /**
     * Publish the progress of the color extraction in the Swing UI thread.
     *
     * @param progress progress of the color extraction. 0 <= progress <= 100
     */
    void progress(int progress);
  }

  /**
   * Run the extraction of the color in a separate thread and send the result in {@link ColorExtractorCallback#result(Collection)}.
   *
   * @param callback callback to get the progress and the result
   */
  public abstract void run(ColorExtractorCallback callback);

}
