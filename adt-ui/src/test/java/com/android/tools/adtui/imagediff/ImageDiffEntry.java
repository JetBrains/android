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
package com.android.tools.adtui.imagediff;

import java.awt.image.BufferedImage;

/**
 * Contains a baseline image filename, a method to generate a {@link BufferedImage} and a similarity threshold that indicates
 * how much both images can differ (in %) to still be considered similar.
 */
abstract class ImageDiffEntry {

  private String myBaselineFilename;

  private float mySimilarityThreshold;

  ImageDiffEntry(String baselineFilename, float similarityThreshold) {
    myBaselineFilename = baselineFilename;
    mySimilarityThreshold = similarityThreshold;
  }

  ImageDiffEntry(String baselineFilename) {
    this(baselineFilename, ImageDiffUtil.DEFAULT_IMAGE_DIFF_PERCENT_THRESHOLD);
  }

  /**
   * Generates an image from a component.
   */
  protected abstract BufferedImage generateComponentImage();

  public final float getSimilarityThreshold() {
    return mySimilarityThreshold;
  }

  public final String getBaselineFilename() {
    return myBaselineFilename;
  }

  @Override
  public String toString() {
    return myBaselineFilename;
  }
}
