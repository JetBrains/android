/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.nodemodel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * This model represents an Atrace node, used for determining the color to draw for the
 * FlameChart, and CallChart.
 */
public class AtraceNodeModel implements CaptureNodeModel {

  // Pattern to match names with the format Letters Number. Eg: Frame 1234
  private static final Pattern ID_GROUP = Pattern.compile("^([A-Za-z\\s]*)(\\d+)");
  private final String myId;
  @NotNull private final String myName;

  public AtraceNodeModel(@NotNull String name) {
    // We match the numbers at the end of a tag so the UI can group elements that have an incrementing number at the end as the same thing.
    // This means that "Frame 1", and "Frame 2" will appear as a single element "Frame ###". This allows us to collect the stats, and
    // colorize these elements as if they represent the same thing.
    Matcher matches = ID_GROUP.matcher(name);
    // If we have a group 0 that is not the name then something went wrong. Fallback to the name.
    if (matches.matches() && matches.group(0).equals(name)) {
      // If we find numbers in the group then instead of using the numbers use "###"
      myId = matches.group(1);
      myName = matches.group(1) + "###";
    } else {
      myId = name;
      myName = name;
    }
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getFullName() {
    return myName;
  }

  @Override
  @NotNull
  public String getId() {
    return myId;
  }
}