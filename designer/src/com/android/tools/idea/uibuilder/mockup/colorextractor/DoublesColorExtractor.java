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
import com.android.tools.pixelprobe.color.Colors;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;


/**
 * Abstract version of a {@link ColorExtractor} that use a clustering algorithm taking
 * an Collection of arrays of doubles representing the color in the CIELab color space.
 */
public abstract class DoublesColorExtractor extends ColorExtractor {

  /**
   * Longest edge of the scaled image used in the clustering
   */
  protected static final int MAX_IMAGE_SIZE = 300;

  /**
   * Increment in the loop parsing the image.
   * Meaning only 1 pixel on PARSE_INCREMENT will be used for the clustering
   */
  protected static final int PARSE_INCREMENT = 7;

  /**
   * Number of coordinate (dimensions) of a point in the
   * clustering space (CIELab color space)
   */
  protected static final int POINT_DIMENSION = 3;


  protected DoublesColorExtractor(Mockup mockup) {
    super(mockup);
  }

  protected DoublesColorExtractor(@NotNull BufferedImage image) {
    super(image);
  }

  /**
   * Run the extraction of the color in a separate thread and send the result in {@link ColorExtractorCallback#result(Collection)}.
   *
   * @param callback callback to get the progress and the result
   */
  @Override
  public void run(ColorExtractorCallback callback) {
    if (callback == null) {
      return;
    }
    myCallback = callback;
    BackgroundExtractTask task = new BackgroundExtractTask();
    task.addPropertyChangeListener(
      evt -> {
        if ("progress".equals(evt.getPropertyName())) {
          myCallback.progress((Integer)evt.getNewValue());
        }
      });
    task.execute();
  }

  /**
   * Parse the image and return a List of array of double containing values representing the color values
   * in the CIELab color space for each pixel.
   *
   * We save the correspondence between the CIELab and RGB value in LAB_RGB to easily recover the rgb value from the lab one
   *
   * @param image        the image to be parsed
   * @param labToRgb     map to cache the rgb value corresponding to the LAB ones
   * @param rgbToLab     map to cache the LAB value corresponding to the RGB ones
   * @param treatedPixel List of int[2] representing the pixel parsed  @return a List of array of double containing values representing the color values
   *                     in the CIELab color space for each pixel used as input for the clustering
   */
  @SuppressWarnings("UseJBColor")
  @NotNull
  @VisibleForTesting
  static List<double[]> getLABPixels(BufferedImage image,
                                     HashMap<Integer, Integer> labToRgb,
                                     HashMap<Integer, double[]> rgbToLab,
                                     @Nullable java.util.List<Integer> treatedPixel) {
    final float[] tempLAB = new float[3]; // temporary array used to retrieve the LAB value from the RGB

    java.util.List<double[]> clusterInput = new ArrayList<>();

    // We get the RGB value of each pixel and convert the value into the LAB space.
    // We then construct an array of this LAB values that will be used as input for the clustering
    int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    for (int i = 0; i < pixels.length; i += PARSE_INCREMENT) {
      // Used for test to get the list of the parsed pixels
      if (treatedPixel != null) {
        treatedPixel.add(i);
      }

      // Get RGB value of the pixel
      int rgb = pixels[i];

      // Prepare input data
      new Color(rgb).getColorComponents(Colors.getLabColorSpace(), tempLAB);
      double[] LABDoubles = {tempLAB[0], tempLAB[1], tempLAB[2]};
      labToRgb.put(Arrays.hashCode(LABDoubles), rgb);
      rgbToLab.put(rgb, LABDoubles);
      clusterInput.add(LABDoubles);
    }
    return clusterInput;
  }

  /**
   * Worker class that execute the Extraction algorithm
   */
  protected class BackgroundExtractTask extends SwingWorker<Collection<ExtractedColor>, Object> {

    @Override
    protected Collection<ExtractedColor> doInBackground() throws Exception {

      if (myImage == null) {
        return Collections.emptyList(); // If there is no image return an empty TreeSet
      }
      return extractColors();
    }

    @NotNull
    private Collection<ExtractedColor> extractColors() {
      BufferedImage image = ImageUtils.createScaledImage(myImage, MAX_IMAGE_SIZE);

      // Keeping a correspondence map of the RGB values for each LAB value allows to not recompute the RGB value from
      // the LAB value and thus not losing precision through the conversion.
      final HashMap<Integer, Integer> labToRgb = new HashMap<>();
      final HashMap<Integer, double[]> rgbToLab = new HashMap<>();

      // Get the LAB value of each pixel in myImage
      final java.util.List<double[]> clusterInput = getLABPixels(image, labToRgb, rgbToLab, null);
      List<ExtractedColor> extractedColors = runClustering(labToRgb, clusterInput, this::publishProgress);
      publishProgress(1);
      return extractedColors;
    }

    /**
     * Call the {@link SwingWorker#setProgress(int)} method of this {@link SwingWorker}.
     *
     * @param progress progess between 0.0f and 1.0f
     */
    protected void publishProgress(float progress) {
      setProgress(Math.max(0, Math.min(100, Math.round(progress * 100))));
    }

    @Override
    protected void done() {
      try {
        myCallback.result(get());
      }
      catch (Exception ignore) {
      }
    }
  }

  /**
   * Subclass should implements this method to run the clustering.
   *
   * @param labToRgb         Map to retrieve the rgb value corresponding to an LAB value to avoid
   *                         loss of precision if we had to recompute the rgb value. It should be used to construct the list
   *                         of {@link ExtractedColor}.
   * @param clusterInput     List of array of doubles representing the pixel value in the CIELab color space.
   *                         each arrays has 3 values representing respectively the L, a, b values of the color of the pixel.
   * @param progressListener Listener to be passed to the clustrer to get update of the progress
   * @return a list of {@link ExtractedColor} that should be constructed with the result of the clustering.
   */
  abstract List<ExtractedColor> runClustering(HashMap<Integer, Integer> labToRgb,
                                              List<double[]> clusterInput,
                                              Clusterer.ProgressListener progressListener);
}
