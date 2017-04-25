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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.updater.Updater;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Entries used in image diff generators that generate tests for some {@link com.android.tools.adtui.AnimatedComponent} usually have a
 * common sequence of steps to generate the main component:
 *    1) initialize structures common to all components (main component, xRange, choregrapher, list of animatables, etc.)
 *    2) initialize structures common to the generator itself (e.g. the line chart in {@link LineChartEntriesRegistrar});
 *    3) generate the component(s) of the test;
 *    4) register all the components to a choreographer;
 *    5) generate data for the test;
 *
 * The only step that needs to change from test to test is step 3. Therefore, abstract method
 * {@link ImageDiffEntry#generateComponentImage()} can be overridden and the specific code of each test can be written by overriding
 * {@link AnimatedComponentImageDiffEntry#generateComponent()}.
 */
abstract class AnimatedComponentImageDiffEntry extends ImageDiffEntry {

  /**
   * Total number of values added to the charts rendered in {@link #myContentPane}.
   */
  protected static final int TOTAL_VALUES = 50;

  /**
   * Simulated time delta, in microseconds, between each value added to the charts rendered in {@link #myContentPane}.
   */
  protected static final long TIME_DELTA_US = TimeUnit.MILLISECONDS.toMicros(50);

  private static final Dimension MAIN_COMPONENT_DIMENSION = new Dimension(640, 480);

  protected JPanel myContentPane;

  protected long myCurrentTimeUs;

  protected long myRangeStartUs;

  protected long myRangeEndUs;

  protected Range myXRange;

  protected List<Updatable> myComponents;

  protected FakeTimer myTimer;

  protected Updater myUpdater;

  AnimatedComponentImageDiffEntry(String baselineFilename, float similarityThreshold) {
    super(baselineFilename, similarityThreshold);
  }

  AnimatedComponentImageDiffEntry(String baselineFilename) {
    super(baselineFilename);
  }

  /**
   * Generates an image from {@link #myContentPane}.
   */
  @Override
  protected final BufferedImage generateComponentImage() {
    // Initialize structures common across AnimatedComponents
    setUpBase();

    // Initialize structures common across all test methods of the generator
    setUp();

    // Generate the components specific of the current test.
    generateComponent();

    // Register the main component and its subcomponents in the choreographer
    myUpdater.register(myComponents);

    // Generate test data in case the component needs it.
    generateTestData();

    // Step the model once so the test data is reflected on the component.
    myTimer.step();

    return ImageDiffUtil.getImageFromComponent(myContentPane);
  }

  private void setUpBase() {
    // TODO: add one more level to the hierarchy and move this to a base class that can be subclassed by generators of different components
    myContentPane = new JPanel(new BorderLayout());
    myContentPane.setSize(MAIN_COMPONENT_DIMENSION);
    myContentPane.setPreferredSize(MAIN_COMPONENT_DIMENSION);

    myCurrentTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    myRangeStartUs = myCurrentTimeUs;
    myRangeEndUs = myRangeStartUs + TOTAL_VALUES * TIME_DELTA_US;
    myXRange = new Range(myRangeStartUs, myRangeEndUs);
    // We don't need to set a proper FPS to the choreographer, as we're interested in the final image only, not the animation.
    myTimer = new FakeTimer();
    myUpdater = new Updater(myTimer);
    myComponents = new ArrayList<>();
  }

  /**
   * Contains the logic to generate the component of a specific test.
   */
  protected abstract void generateComponent();

  /**
   * Generates test data deterministically.
   * This method can be user, for example, to add data to charts, set some values to axes, fill text boxes, select checkboxes, etc.
   */
  protected abstract void generateTestData();

  /**
   * Initializes the structures that are common across all the tests of a generator.
   * It's called once per test method.
   */
  protected abstract void setUp();
}
