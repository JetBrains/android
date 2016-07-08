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

import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Contains some information about a color extracted by {@link ColorExtractor}.
 *
 * Comparable by number of occurrences
 */
public class ExtractedColor implements Comparable<ExtractedColor> {

  private final int myColor;
  private final int myOccurrence;
  private final Set<Integer> myNeighborColor;
  private double[] myLAB;

  public ExtractedColor(int color, double[] lab, int occurrence, Set<Integer> neighborColor) {
    myColor = color;
    myOccurrence = occurrence;
    myLAB = lab;
    myNeighborColor = neighborColor;
  }

  public int getColor() {
    return myColor;
  }

  public int getOccurrence() {
    return myOccurrence;
  }

  public double[] getLAB() {
    return myLAB;
  }

  public Set<Integer> getNeighborColor() {
    return myNeighborColor;
  }

  @Override
  public String toString() {
    return String.format(Locale.US, "#%05X", myColor);
  }

  @Override
  public int compareTo(ExtractedColor o) {
    return myOccurrence - o.getOccurrence();
  }
}
