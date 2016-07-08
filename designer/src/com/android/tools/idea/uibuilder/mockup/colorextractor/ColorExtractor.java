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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Extract the meaningful myColor of an image using the DBSCAN clustering algorithm.
 *
 * The DBSCAN algorithm allows to gather in the same cluster the myColor that are perceptually almost the same.
 *
 * The clustering is done in the CIELAB myColor space. The CIELAB myColor space is useful since an euclidean distance of 1 between two
 * colors is approximately the minimum perceptual difference between the two myColor (i.e colors with a distance < 1 in CIE LAB will look
 * the same for the human eye).
 *
 * Then for each cluster we only take the most present myColor. Doing so, the myColor used only for antialiasing in the image are not extracted.
 */
@SuppressWarnings("UseJBColor")
public class ColorExtractor {

  /**
   * Maximum Euclidean distance between two myColor in the CIELAB space two gather them in the same cluster
   */
  private static final int EPS = 2;
  private ColorExtractorCallback myCallback;
  private BufferedImage myImage;

  public ColorExtractor(Mockup mockup) {
    myImage = mockup.getImage();
    // TODO get the cropped image of the mockup
  }

  /**
   * Convenient constructor used for test
   *
   * @param image
   */
  ColorExtractor(BufferedImage image) {
    myImage = image;
  }

  /**
   * Run the extraction of the color in a separate thread and send the result in {@link ColorExtractorCallback#result(Collection)}.
   *
   * @param callback callback to get the progress and the result
   */
  public void run(ColorExtractorCallback callback) {
    if (callback == null) {
      return;
    }
    myCallback = callback;
    BackgroundExtractTask backgroundExtractTask = new BackgroundExtractTask();
    backgroundExtractTask.addPropertyChangeListener(
      evt -> {
        if ("progress".equals(evt.getPropertyName())) {
          myCallback.progress((Integer)evt.getNewValue());
        }
      });
    backgroundExtractTask.execute();
  }

  /**
   * Parse the image and return a List of array of double containing values representing the color values
   * in the CIELab color space for each pixel.
   *
   * We save the correspondence between the CIELab and RGB value in LAB_RGB to easily recover the rgb value from the lab one
   *
   * @param image   the image to be parsed
   * @param LAB_RGB map to save the rgb value corresponding to the LAB one
   * @return a List of array of double containing values representing the color values
   * in the CIELab color space for each pixel used as input for the clustering
   */
  @NotNull
  private static List<double[]> getLABPixels(BufferedImage image, HashMap<Integer, Integer> LAB_RGB) {
    final float[] tempLAB = new float[3]; // temporary array used to retrieve the LAB value from the RGB

    List<double[]> clusterInput = new ArrayList<>();

    // We get the RGB value of each pixel and convert the value into the LAB space.
    // We then construct an array of this LAB values that will be used as input for the clustering
    for (int x = 0; x < image.getWidth(); x += 11) {
      for (int y = 0; y < image.getHeight(); y += 3) {
        // Get RGB value of the pixel
        int rgb = image.getRGB(x, y);
        // Prepare DBSCAN
        new Color(rgb).getColorComponents(Colors.getLabColorSpace(), tempLAB);
        double[] LABDoubles = {tempLAB[0], tempLAB[1], tempLAB[2]};
        LAB_RGB.put(Arrays.hashCode(LABDoubles), rgb);
        clusterInput.add(LABDoubles);
      }
    }
    return clusterInput;
  }

  /**
   * Find the color with the most occurrences in each cluster of similar colors.
   *
   * For each cluster of similar color, parse all the colors and count the number of pixel of this exact color.
   * Then add the color with the most occurrence in an {@link ExtractedColor}
   * and set its number of occurrence in the image at the number of similar color in the cluster.
   *
   * @param LAB_RGB  Map containing the rgb value corresponding to a CIELAB value.
   *                 Should have been populated with {@link ColorExtractor#getLABPixels} before
   * @param clusters the clusters return by {@link DBSCANClusterer#cluster(double[][])}
   * @return a TreeSet of the {@link ExtractedColor} sorted by the number of occurrences for each color (ascending order)
   */
  @NotNull
  private static List<ExtractedColor> getMainColorPerCluster(HashMap<Integer, Integer> LAB_RGB, List<List<double[]>> clusters) {
    List<ExtractedColor> colors = new ArrayList<>(clusters.size());

    for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {

      List<double[]> cluster = clusters.get(clusterIndex);
      HashMap<Integer, Integer> occurrences = new HashMap<>();
      int maxOccurrence = 0;
      int mostPresentColor = 0;

      for (int colorIndex = 0; colorIndex < cluster.size(); colorIndex++) {
        int colorOccurrence;
        double[] LAB = cluster.get(colorIndex);

        // Retrieve the rgb value corresponding to the LAB value
        int rgb = LAB_RGB.get(Arrays.hashCode(LAB));

        if (occurrences.containsKey(rgb)) {
          // If the color is already in the map, increment the number of occurrence
          colorOccurrence = occurrences.get(rgb) + 1;
          occurrences.replace(rgb, colorOccurrence);
        }
        else {
          // Else add the color to the map and set its occurrence to 1
          occurrences.put(rgb, 1);
          colorOccurrence = 1;
        }

        // Update the color occurring the most
        if (colorOccurrence > maxOccurrence) {
          maxOccurrence = colorOccurrence;
          mostPresentColor = rgb;
        }
      }
      colors.add(new ExtractedColor(mostPresentColor, cluster.size()));
    }

    return colors;
  }

  /**
   * Worker class that execute the clustering algorithm
   */
  private class BackgroundExtractTask extends SwingWorker<List<ExtractedColor>, Object> {

    @Override
    protected List<ExtractedColor> doInBackground() throws Exception {
      if (myImage == null) {
        return Collections.emptyList(); // If there is no image return an empty TreeSet
      }

      // Keeping a correspondence map of the RGB values for each LAB value allows to not recompute the RGB value from
      // the LAB value and thus not losing precision through the conversion.
      final HashMap<Integer, Integer> LAB_RGB = new HashMap<>();

      int imageSize = Math.max(myImage.getHeight(), myImage.getWidth());
      int minClusterSize = getMinClusterSize(imageSize);

      // Get the LAB value of each pixel in myImage
      final List<double[]> clusterInput = getLABPixels(myImage, LAB_RGB);

      // Run clusterization
      final DBSCANClusterer dbscan = new DBSCANClusterer(EPS, minClusterSize, this::publishProgress);
      final List<List<double[]>> clusters = dbscan.cluster(clusterInput.toArray(new double[clusterInput.size()][3]));

      return getMainColorPerCluster(LAB_RGB, clusters);
    }

    private void publishProgress(float progress) {
      setProgress(Math.round(progress * 100));
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
   * Compute the minimum cluster size regarding the imageSize.
   *
   * Minimum cluster size is 0 if image size < 16 else Math.pow(imageSize, 0.25)
   *
   * @param imageSize width or height of the image. Typically the bigger of both.
   * @return the minimum cluster size
   */
  private static int getMinClusterSize(int imageSize) {
    int minClusterSize;
    if (imageSize < 16) {
      minClusterSize = 0;
    }
    else {
      minClusterSize = (int)Math.round(Math.pow(imageSize, 0.25));
    }
    return minClusterSize;
  }

  /**
   * Callback to get the result of {@link ColorExtractor}
   *
   * @see ColorExtractor#run(ColorExtractorCallback)
   */
  public interface ColorExtractorCallback {

    /**
     * @param rgbColors Sorted collection of the color by occurrences in the image.
     *                  Empty collection if the image was null
     */
    void result(Collection<ExtractedColor> rgbColors);

    /**
     * Publish the progress of the color extraction.
     *
     * @param progress progress of the color extraction. 0 <= progress <= 100
     */
    void progress(int progress);
  }
}
