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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * Parametrized test class that runs all the tests of {@link com.android.tools.adtui.imagediff}.
 *
 * Use {@link BaselineImagesGenerator} if you need to generate new images. For consistency, you
 * should use a Linux machine.
 */
@RunWith(Parameterized.class)
public class ImageDiffTest {

  @Parameterized.Parameters(name = "{0}")
  public static List<ImageDiffEntry> getImageDiffEntries() {
    // The parameters of this test class are all the image diff entries of the imagediff package, so it can run all the tests registered.
    return ImageDiffUtil.IMAGE_DIFF_ENTRIES;
  }

  /**
   * Contains the parameter of the class, which means the {@link ImageDiffEntry} being used for each test that will run.
   */
  private ImageDiffEntry myEntry;

  public ImageDiffTest(ImageDiffEntry imageDiffEntry) {
    myEntry = imageDiffEntry;
  }

  @Test
  public void runTest() {
    // Asserts that a generated image is similar (within a given threshold) to a baseline image with given name.
    ImageDiffUtil.assertImagesSimilar(myEntry.getBaselineFilename(),
                                      myEntry.generateComponentImage(),
                                      myEntry.getSimilarityThreshold());
  }
}
