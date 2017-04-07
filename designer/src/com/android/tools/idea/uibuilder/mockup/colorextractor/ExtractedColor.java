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

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
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
  /**
   * Represent a color extracted by a {@link ColorExtractor}.
   *
   * @param color         the rgb value of the color
   * @param occurrence    number of time this color or any close color appeared in the image this color has been extracted from
   * @param neighborColor Colors that were considered are almost the same as color in the image
   */
  public ExtractedColor(int color, int occurrence, @Nullable Set<Integer> neighborColor) {
    myColor = color;
    myOccurrence = occurrence;
    myNeighborColor = neighborColor != null ? neighborColor : new HashSet<>(0);
  }

  public int getColor() {
    return myColor;
  }

  public int getOccurrence() {
    return myOccurrence;
  }


  public Set<Integer> getNeighborColor() {
    return myNeighborColor;
  }

  /**
   * @return the color Hexadecimal code
   */
  @Override
  public String toString() {
    return String.format(Locale.US, "#%05X", myColor);
  }

  @Override
  public int compareTo(ExtractedColor o) {
    return o.getOccurrence() - myOccurrence;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExtractedColor that = (ExtractedColor)o;

    if (myColor != that.myColor) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myColor;
  }
}
