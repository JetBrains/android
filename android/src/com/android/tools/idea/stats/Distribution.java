/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.stats;

import com.android.repository.Revision;

import java.util.List;

/**
 * Information about a single Android version, including its share of Android usage overall.
 */

public class Distribution implements Comparable<Distribution> {
  private int apiLevel;
  private Revision version;
  private String description;
  private String url;
  private java.util.List<TextBlock> descriptionBlocks;
  private double distributionPercentage;
  private String name;


  private Distribution() {
    // Private default for json conversion
  }


  public Revision getVersion() {
    return version;
  }

  public double getDistributionPercentage() {
    return distributionPercentage;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getUrl() {
    return url;
  }

  public List<TextBlock> getDescriptionBlocks() {
    return descriptionBlocks;
  }

  public int getApiLevel() {
    return apiLevel;
  }

  @Override
  public int compareTo(Distribution other) {
    return Integer.valueOf(apiLevel).compareTo(other.apiLevel);
  }

  public static class TextBlock {
    public String title;
    public String body;
  }
}

